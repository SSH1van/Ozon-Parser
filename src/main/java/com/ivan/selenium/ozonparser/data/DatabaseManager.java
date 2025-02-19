package com.ivan.selenium.ozonparser.data;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

public class DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/products";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "root";
    public static String globalFolderName = "";

    private final Connection connection;

    public DatabaseManager() throws SQLException {
        this.connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
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
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, categoryName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Ошибка при добавлении категории в addCategory " + e.getMessage());
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

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, categoryName);
            stmt.setString(2, productUrl);
            stmt.setInt(3, price);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Ошибка при добавлении продукта в addProductWithPrice " + e.getMessage());
        }
    }
}
