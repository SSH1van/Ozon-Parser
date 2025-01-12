package com.ivan.selenium.ozonparser;
import java.sql.*;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:products.db";

    public static void createDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            if (conn != null) {
                System.out.println("База данных создана!");
                try (Statement stmt = conn.createStatement()) {
                    // Создание таблицы
                    String createTableSQL = "CREATE TABLE IF NOT EXISTS products (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "name TEXT NOT NULL, " +
                            "price INTEGER NOT NULL, " +
                            "link TEXT NOT NULL);";
                    stmt.execute(createTableSQL);
                    System.out.println("Таблица 'products' создана!");
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка подключения к базе данных: " + e.getMessage());
        }
    }

    public static void insertProduct(String name, int price, String link) {
        String insertSQL = "INSERT INTO products(name, price, link) VALUES(?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, price);
            pstmt.setString(3, link);
            pstmt.executeUpdate();
            // System.out.println("Продукт добавлен: " + name);
        } catch (SQLException e) {
            System.err.println("Ошибка добавления продукта: " + e.getMessage());
        }
    }
}
