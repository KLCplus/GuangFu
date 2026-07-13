package com.example.pvplatform.module.file.service;

import com.example.pvplatform.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

public final class ImageUploadValidator {
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_PIXELS = 40_000_000L;
    private ImageUploadValidator() {}
    public static ValidImage validate(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) throw new BusinessException(400, "图片不能为空");
        if (file.getSize() > maxBytes) throw new BusinessException(400, "图片超过大小限制");
        String original = file.getOriginalFilename() == null ? "" : Paths.get(file.getOriginalFilename()).getFileName().toString();
        String ext = extension(original);
        if (!EXTENSIONS.contains(ext)) throw new BusinessException(400, "仅支持 JPG、JPEG、PNG、WebP 图片");
        try {
            byte[] bytes = file.getBytes();
            if (!magicMatches(bytes, ext)) throw new BusinessException(400, "图片内容与扩展名不匹配");
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            // Standard JRE has no WebP reader. Magic validation is retained for WebP deployments;
            // where a reader is present it is decoded too.
            if (image == null && !"webp".equals(ext)) throw new BusinessException(400, "无法解码图片");
            if (image != null && (image.getWidth() <= 0 || image.getHeight() <= 0 || (long) image.getWidth() * image.getHeight() > MAX_PIXELS))
                throw new BusinessException(400, "图片尺寸不合法");
            String contentType = "png".equals(ext) ? "image/png" : "webp".equals(ext) ? "image/webp" : "image/jpeg";
            return new ValidImage(bytes, original, ext, contentType);
        } catch (BusinessException e) { throw e;
        } catch (Exception e) { throw new BusinessException(400, "无法读取图片"); }
    }
    private static boolean magicMatches(byte[] b, String ext) {
        if ("jpg".equals(ext) || "jpeg".equals(ext)) return b.length >= 3 && (b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255;
        if ("png".equals(ext)) return b.length >= 8 && (b[0]&255)==137 && b[1]==80 && b[2]==78 && b[3]==71 && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10;
        return b.length >= 12 && b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F' && b[8]=='W' && b[9]=='E' && b[10]=='B' && b[11]=='P';
    }
    private static String extension(String name) { int dot=name.lastIndexOf('.'); return dot < 0 ? "" : name.substring(dot+1).toLowerCase(Locale.ROOT); }
    public record ValidImage(byte[] bytes, String originalName, String extension, String contentType) {}
}
