package com.ivan.selenium.ozonparser.core;

import com.ivan.selenium.ozonparser.data.CsvToUrls;
import com.ivan.selenium.ozonparser.data.DatabaseManager;
import com.ivan.selenium.ozonparser.actions.PageActions;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final List<String> urls = CsvToUrls.readUrlsFromCsv("urls.csv");

    static boolean headless = false;
    static long timeLoad = 30;
    public static long timeSleep = 5;

    public static void main(String[] args) {
        WebDriverManager driverManager = new WebDriverManager();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nПрограмма завершена. Освобождаем ресурсы...");
            DatabaseManager.closePool();
            driverManager.cleanUp();

            File logDir = new File("log/" + DatabaseManager.globalFolderName);
            if (logDir.exists() && logDir.isDirectory() && logDir.delete()) {
                System.out.println("Пустая директория логов 'log/" + DatabaseManager.globalFolderName + "' удалена.");
            }
        }));

        try {
            PageActions actions = new PageActions();
            DatabaseManager dbManager = new DatabaseManager();

            // Создание директорий и определение пути к логам
            dbManager.initializeLogPath();

            for (String url : urls) {
                // Перезагрузка chrome под новым IP
                driverManager.restartDriverWithCleanUserData();

                // Открываем ссылку на товар
                actions.openUrl(url, timeSleep);

                // Получаем название таблицы
                String categoryName = actions.extractCategory(driverManager);

                // Добавить категорию в БД
                dbManager.addCategory(categoryName);

                // Получаем цену и ссылку на товар при скроллинге
                actions.scrollAndClick(categoryName, driverManager, dbManager);

                System.out.println("Ссылка на товар обработана: " + url + "\n");
            }
            System.out.println("Все товары по ссылкам успешно записаны в базу данных");
        } catch (Exception e) {
            LOGGER.severe("Ошибка при выполнении main функции: " + e.getMessage());
            PageActions.savePageSource("main_ERORR_");
        } finally {
            driverManager.cleanUp();
        }
    }
}