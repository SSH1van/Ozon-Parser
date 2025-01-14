package com.ivan.selenium.ozonparser;
import java.sql.*;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:products.db";
    private static String tableName = "unknown";

    public static void createTable (String tempTableName) {
        tableName = tempTableName;
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            if (conn != null) {
                try (Statement stmt = conn.createStatement()) {
                    // Создание таблицы
                    String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "price INTEGER NOT NULL, " +
                            "link TEXT NOT NULL);";
                    stmt.execute(createTableSQL);
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка подключения к базе данных: " + e.getMessage());
        }
    }

    public static void insertProduct(int price, String link) {
        String insertSQL = "INSERT INTO " + tableName + "(price, link) VALUES(?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setInt(1, price);
            pstmt.setString(2, link);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Ошибка добавления продукта: " + e.getMessage());
        }
    }
}
