package com.ivan.selenium.ozonparser;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final List<String> urls = CsvToUrls.readUrlsFromCsv("urls.csv");

    static long timeRefresh = 20;
    static long timeSleep = 5;
    static boolean headless = true;

    public static void main(String[] args) {
        WebDriverManager driverManager = new WebDriverManager();
        ChromeOptions options = WebDriverManager.createOptions(headless);
        WebDriver driver = driverManager.initDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(timeRefresh));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Программа завершена. Освобождаем ресурсы...");
            driverManager.cleanUp();
        }));

        try {
            PageActions actions = new PageActions(driver);

            // Создание директорий и определение пути к БД
            DatabaseManager.initializeDatabasePath();

            for (String url : urls) {
                // Открываем ссылку на товар
                actions.openUrl(url, timeSleep);

                // Проверка на наличие экрана блокировки
                if (actions.checkLockScreen()) return;

                // Получаем название таблицы
                String categoryName = actions.extractCategory();

                // Создать базу данных и таблицу
                DatabaseManager.createTable(categoryName);

                // Получаем название, цену и ссылку
                actions.scrollAndClick();
            }
        } catch (Exception e) {
            LOGGER.severe("Ошибка при выполнении main функции: " + e.getMessage());
        } finally {
            driverManager.cleanUp();
        }
    }
}