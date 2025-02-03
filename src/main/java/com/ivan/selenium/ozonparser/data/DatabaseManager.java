package com.ivan.selenium.ozonparser.data;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DatabaseManager {
    public static String tableName = "unknown";
    private static String DB_URL;
    public static String globalFolderName = "";

    public static void initializeDatabasePath() {
        String folderName = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date());
        globalFolderName = folderName;
        File resultsDir = new File("results");
        File dateDir = new File(resultsDir, folderName);

        if (!dateDir.exists() && !dateDir.mkdirs()) {
            System.err.println("Не удалось создать директорию: " + dateDir.getAbsolutePath());
            throw new RuntimeException("Не удалось создать директорию для базы данных");
        }

        String dbPath = new File(dateDir, "products.db").getAbsolutePath();
        DB_URL = "jdbc:sqlite:" + dbPath;
    }

    public static void createTable (String tempTableName) {
        tableName = tempTableName;
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            if (conn != null) {
                try (Statement stmt = conn.createStatement()) {
                    // Создание таблицы
                    String createTableSQL = "CREATE TABLE IF NOT EXISTS \"" + tableName + "\" (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "price INTEGER NOT NULL, " +
                            "link TEXT NOT NULL UNIQUE);";
                    stmt.execute(createTableSQL);
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка подключения к базе данных: " + e.getMessage());
        }
    }

    public static void insertProduct(int price, String link) {
        String insertSQL = "INSERT INTO \"" + tableName + "\"(price, link) VALUES(?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setInt(1, price);
            pstmt.setString(2, link);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getSQLState().startsWith("23")) { // Код ошибки уникального ограничения
                System.err.println("Продукт с такой ссылкой уже существует: " + link);
            } else {
                System.err.println("Ошибка добавления продукта: " + e.getMessage());
            }
        }
    }
}
