package com.ivan.selenium.ozonparser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringProcessing {
    public String extractCategory(String url) {
        // Регулярное выражение для извлечения частей URL
        String regex = "/category/([a-z0-9-]+)(?:/([a-z0-9-]+))?";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);

        // Проверяем совпадения
        if (matcher.find()) {
            String mainCategory = matcher.group(1); // Основная категория
            String subCategory = matcher.group(2); // Подкатегория (если есть)

            if (subCategory != null) {
                return sanitizeTableName(mainCategory + "-" + subCategory);
            } else {
                return sanitizeTableName(mainCategory);
            }
        }
        return "unknown"; // Если URL не соответствует формату
    }

    private static String sanitizeTableName(String tableName) {
        return tableName.replaceAll("-\\d+", "").replace("-", "_");
    }
}
