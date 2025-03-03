package com.ivan.selenium.ozonparser.data;

import com.ivan.selenium.ozonparser.config.ConfigReader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class DatabaseService {
    private static final Logger LOGGER = Logger.getLogger(DatabaseService.class.getName());
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

    public void addProductWithPrice(List<String> categoryPath, String productUrl, int price) {
        try (Connection conn = getConnection()) {
            // Включаем транзакцию для атомарности
            conn.setAutoCommit(false);

            // Получаем или создаем категорию
            Integer categoryId = getOrCreateCategory(conn, categoryPath);

            // Проверяем существование продукта по url
            Long productId = getProductIdByUrl(conn, productUrl);
            if (productId == null) {
                // Продукта нет, вставляем новый
                productId = insertProduct(conn, productUrl, categoryId);
            }

            // Добавляем цену для продукта
            insertProductPrice(conn, productId, price);

            conn.commit();
        } catch (SQLException e) {
            LOGGER.severe("Ошибка при добавлении продукта: " + e.getMessage());
        }
    }

    // Вспомогательный метод: получение или создание категории
    private Integer getOrCreateCategory(Connection conn, List<String> categoryPath) throws SQLException {
        if (categoryPath == null || categoryPath.isEmpty()) {
            throw new IllegalArgumentException("Путь категории не может быть пустым");
        }

        String checkSql = "SELECT id FROM categories WHERE name = ? AND " +
                "((parent_id IS NULL AND ? IS NULL) OR (parent_id = ?)) AND level = ?";
        String insertSql = "INSERT INTO categories (name, parent_id, level) VALUES (?, ?, ?) RETURNING id";

        Integer parentId = null; // Для корневого уровня (level 1) parent_id = null
        Integer categoryId = null;

        for (int i = 0; i < categoryPath.size(); i++) {
            String categoryName = categoryPath.get(i);
            int level = i + 1; // Уровень начинается с 1

            // Проверяем, существует ли категория с данным именем, parent_id и level
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, categoryName);
                if (parentId == null) {
                    checkStmt.setNull(2, java.sql.Types.INTEGER); // Для проверки IS NULL
                    checkStmt.setNull(3, java.sql.Types.INTEGER); // Не используется, если parentId null
                } else {
                    checkStmt.setInt(2, parentId); // Для проверки IS NULL (не используется)
                    checkStmt.setInt(3, parentId); // Для проверки =
                }
                checkStmt.setInt(4, level);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        categoryId = rs.getInt("id");
                        parentId = categoryId; // Обновляем parentId для следующего уровня
                        continue;
                    }
                }
            }

            // Если категория не найдена, создаём её
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, categoryName);
                if (parentId == null) {
                    insertStmt.setNull(2, java.sql.Types.INTEGER);
                } else {
                    insertStmt.setInt(2, parentId);
                }
                insertStmt.setInt(3, level);

                try (ResultSet rs = insertStmt.executeQuery()) {
                    rs.next();
                    categoryId = rs.getInt("id");
                    parentId = categoryId; // Обновляем parentId для следующего уровня
                }
            }
        }

        return categoryId; // Возвращаем ID последней созданной или найденной категории
    }

    // Вспомогательный метод: получение ID продукта по URL
    private Long getProductIdByUrl(Connection conn, String productUrl) throws SQLException {
        String sql = "SELECT id FROM products WHERE url = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, productUrl);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
                return null;
            }
        }
    }

    // Вспомогательный метод: вставка нового продукта
    private Long insertProduct(Connection conn, String productUrl, Integer categoryId) throws SQLException {
        String sql = "INSERT INTO products (url, category_id) VALUES (?, ?) RETURNING id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, productUrl);
            stmt.setInt(2, categoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        }
    }

    // Вспомогательный метод: вставка цены продукта
    private void insertProductPrice(Connection conn, Long productId, int price) throws SQLException {
        String sql = "INSERT INTO product_prices (product_id, date, price) VALUES (?, NOW(), ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, productId);
            stmt.setInt(2, price);
            stmt.executeUpdate();
        }
    }
}
