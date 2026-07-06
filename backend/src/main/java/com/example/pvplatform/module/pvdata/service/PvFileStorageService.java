package com.example.pvplatform.module.pvdata.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.persistence.entity.FileResourceDO;
import com.example.pvplatform.persistence.mapper.FileResourceMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class PvFileStorageService {
    private final FileResourceMapper fileMapper;
    private final Path root;
    private final long maxBytes;

    public PvFileStorageService(FileResourceMapper fileMapper,
                                @Value("${pv-data.import.storage-dir:./data/pv-imports}") String storageDir,
                                @Value("${pv-data.import.max-file-size:10485760}") long maxBytes) {
        this.fileMapper = fileMapper;
        this.root = Paths.get(storageDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public StoredFile store(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(400, "文件大小超过限制");
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new BusinessException(400, "文件名不能为空");
        }
        original = Paths.get(original).getFileName().toString();
        String extension = extension(original);
        if (!extension.equals("csv") && !extension.equals("xlsx")) {
            throw new BusinessException(400, "仅支持 CSV 和 XLSX");
        }
        verifySignature(file, extension);
        String storageName = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(storageName).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(400, "文件路径不合法");
        }
        try {
            Files.createDirectories(root);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            FileResourceDO resource = new FileResourceDO();
            resource.setOwnerUserId(userId);
            resource.setOriginalName(trim(original, 255));
            resource.setStorageName(storageName);
            resource.setStoragePath(target.toString());
            resource.setFileType(extension);
            resource.setBusinessType("PV_DATA");
            resource.setFileSize(file.getSize());
            resource.setChecksum(HexFormat.of().formatHex(digest.digest()));
            resource.setCreatedAt(LocalDateTime.now());
            fileMapper.insert(resource);
            return new StoredFile(resource, target, extension);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // Preserve the original storage error.
            }
            throw new BusinessException(500, "上传文件保存失败");
        }
    }

    private void verifySignature(MultipartFile file, String extension) {
        try (InputStream input = new BufferedInputStream(file.getInputStream())) {
            byte[] prefix = input.readNBytes(4096);
            if ("xlsx".equals(extension)) {
                if (prefix.length < 4 || prefix[0] != 'P' || prefix[1] != 'K') {
                    throw new BusinessException(400, "文件扩展名与内容类型不匹配");
                }
            } else {
                for (byte value : prefix) {
                    if (value == 0) {
                        throw new BusinessException(400, "文件扩展名与内容类型不匹配");
                    }
                }
            }
        } catch (IOException exception) {
            throw new BusinessException(400, "无法读取上传文件");
        }
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record StoredFile(FileResourceDO resource, Path path, String extension) {
    }
}
