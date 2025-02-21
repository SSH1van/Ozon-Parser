package com.ivan.selenium.ozonparser.data;

import com.ivan.selenium.ozonparser.config.ConfigReader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

public class DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final HikariDataSource dataSource;
    public static String globalFolderName = "";

    static {
        // Настраиваем HikariCP
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/products");
        config.setUsername(ConfigReader.getDatabaseUser());
        config.setPassword(ConfigReader.getDatabasePassword());

        // Оптимальные параметры пула соединений
        config.setMaximumPoolSize(10);  // Максимальное количество соединений в пуле
        config.setMinimumIdle(2);       // Минимальное количество простаивающих соединений
        config.setIdleTimeout(30000);   // Закрытие неиспользуемых соединений (30 сек)
        config.setMaxLifetime(1800000); // Максимальное время жизни соединения (30 мин)
        config.setConnectionTimeout(10000); // Таймаут подключения (10 сек)

        dataSource = new HikariDataSource(config);
    }

    // Получаем соединение из пула
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // Закрываем пул при завершении работы приложения
    public static void closePool() {
        if (dataSource != null) {
            dataSource.close();
            LOGGER.info("Пул соединений HikariCP закрыт.");
        }
    }

    public void initializeLogPath() {
        String folderName = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        globalFolderName = folderName;
        File logDir = new File("log");
        File dateDir = new File(logDir, folderName);

        if (!dateDir.exists() && !dateDir.mkdirs()) {
            System.err.println("Не удалось создать директорию: " + dateDir.getAbsolutePath());
            throw new RuntimeException("Не удалось создать директорию для базы данных");
        }
    }

    public void addCategory(String categoryName) {
        String sql = "INSERT INTO categories (name) VALUES (?) ON CONFLICT (name) DO NOTHING";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, categoryName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Ошибка при добавлении категории: " + e.getMessage());
        }
    }

    public void addProductWithPrice(String categoryName, String productUrl, int price) {
        String sql = """
        WITH category AS (
            SELECT id FROM categories WHERE name = ?
        ), 
        product AS (
            INSERT INTO products (url, category_id)
            SELECT ?, category.id FROM category
            ON CONFLICT (url) DO UPDATE SET url = EXCLUDED.url
            RETURNING id
        )
        INSERT INTO product_prices (product_id, date, price)
        SELECT product.id, NOW(), ? FROM product;
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, categoryName);
            stmt.setString(2, productUrl);
            stmt.setInt(3, price);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Ошибка при добавлении продукта: " + e.getMessage());
        }
    }
}
