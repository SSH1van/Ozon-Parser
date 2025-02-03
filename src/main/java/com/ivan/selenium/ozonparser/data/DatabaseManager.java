package com.ivan.selenium.ozonparser.data;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

public class DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    public static String tableName = "unknown";
    private static String DB_URL;
    public static String globalFolderName = "";

    public void initializeDatabasePath() {
        String folderName = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
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

    public void createTable (String tempTableName) {
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
            LOGGER.severe("Ошибка подключения к базе данных: " + e.getMessage());
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
            if (e.getSQLState().startsWith("23")) { // Код ошибки уникального значения
                LOGGER.warning("Продукт с такой ссылкой уже существует: " + link);
            } else {
                LOGGER.warning("Ошибка добавления продукта: " + e.getMessage());
            }
        }
    }
}
