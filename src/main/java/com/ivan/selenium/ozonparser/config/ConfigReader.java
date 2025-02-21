package com.ivan.selenium.ozonparser.config;

import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.List;
import java.io.InputStream;

public class ConfigReader {
    private static final List<String> proxies;

    static {
        try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream("config.yaml")) {
            if (input == null) {
                throw new RuntimeException("Не удалось найти файл config.yaml");
            }
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(input);

            Object proxiesObj = config.get("proxies");
            if (proxiesObj instanceof List<?>) {
                // Safely cast to List<String>
                proxies = ((List<?>) proxiesObj).stream()
                        .filter(item -> item instanceof String)
                        .map(item -> (String) item)
                        .toList();
            } else {
                throw new RuntimeException("Неверный формат конфигурации: 'proxies' должен быть списком строк");
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка чтения конфигурации", e);
        }
    }

    public static List<String> getProxies() {
        return proxies;
    }

    public static String getProxyUser() {
        return System.getenv("PROXY_USER");
    }

    public static String getProxyPassword() {
        return System.getenv("PROXY_PASSWORD");
    }

    public static String getDatabaseUser() {
        return System.getenv("DATABASE_USER");
    }

    public static String getDatabasePassword() {
        return System.getenv("DATABASE_PASSWORD");
    }
}

