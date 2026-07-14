package com.example.pvplatform.module.user.service;

import com.example.pvplatform.common.exception.BusinessException;

import java.util.Set;

final class ContactPhoneNormalizer {
    private static final Set<String> SUPPORTED_CALLING_CODES = Set.of("+86", "+1", "+44", "+81", "+82");

    private ContactPhoneNormalizer() {}

    static String normalize(String input) {
        if (input == null || input.isBlank()) return null;

        String compact = input.trim().replaceAll("[\\s()-]", "");
        if (compact.matches("^1[3-9]\\d{9}$")) compact = "+86" + compact;
        if (!compact.startsWith("+")) {
            throw new BusinessException(400, "联系手机号须包含国家/地区区号");
        }

        String callingCode = SUPPORTED_CALLING_CODES.stream()
            .filter(compact::startsWith)
            .max((left, right) -> Integer.compare(left.length(), right.length()))
            .orElse(null);
        if (callingCode == null) {
            throw new BusinessException(400, "暂不支持该国家/地区区号");
        }
        if ("+86".equals(callingCode)) {
            if (!compact.matches("^\\+861[3-9]\\d{9}$")) {
                throw new BusinessException(400, "请输入有效的中国大陆 11 位手机号");
            }
        } else if (!compact.matches("^\\+[1-9]\\d{7,14}$")) {
            throw new BusinessException(400, "请输入有效的国际手机号");
        }
        return compact;
    }
}
