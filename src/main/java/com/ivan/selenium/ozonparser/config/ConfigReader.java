package com.ivan.selenium.ozonparser.config;

import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.List;
import java.util.Properties;
import java.io.InputStream;

public class ConfigReader {
    private static final List<String> proxies;
    private static final Properties properties;

    static {
        // Инициализация Properties
        properties = new Properties();
        try (InputStream propsInput = ConfigReader.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (propsInput == null) {
                throw new RuntimeException("Не удалось найти файл application.properties");
            }
            properties.load(propsInput);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка чтения application.properties", e);
        }

        // Чтение config.yaml
        try (InputStream yamlInput = ConfigReader.class.getClassLoader().getResourceAsStream("config.yaml")) {
            if (yamlInput == null) {
                throw new RuntimeException("Не удалось найти файл config.yaml");
            }
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(yamlInput);

            Object proxiesObj = config.get("proxies");
            if (proxiesObj instanceof List<?>) {
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
        return properties.getProperty("proxy.user");
    }

    public static String getProxyPassword() {
        return properties.getProperty("proxy.password");
    }

    public static String getDatabaseUser() {
        return properties.getProperty("database.user");
    }

    public static String getDatabasePassword() {
        return properties.getProperty("database.password");
    }
}