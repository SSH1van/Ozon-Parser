package com.ivan.selenium.ozonparser.core;

import com.ivan.selenium.ozonparser.data.CsvToUrls;
import com.ivan.selenium.ozonparser.actions.PageActions;
import com.ivan.selenium.ozonparser.data.DatabaseManager;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.List;
import java.util.logging.Logger;
import java.time.Duration;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final List<String> urls = CsvToUrls.readUrlsFromCsv("urls.csv");

    static boolean headless = false;
    static long timeRefresh = 20;
    static long timeSleep = 5;

    public static void main(String[] args) {
        WebDriverManager driverManager = new WebDriverManager();
        ChromeOptions options = driverManager.createOptions(headless);
        WebDriver driver = driverManager.initDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(timeRefresh));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nПрограмма завершена. Освобождаем ресурсы...");
            driverManager.cleanUp();
        }));

        try {
            PageActions actions = new PageActions(driver);

            // Создание директорий и определение пути к БД
            DatabaseManager.initializeDatabasePath();

            for (String url : urls) {
                // Открываем ссылку на товар
                actions.openUrl(url, timeSleep);

                // Получаем название таблицы
                String categoryName = actions.extractCategory();

                // Создать базу данных и таблицу
                DatabaseManager.createTable(categoryName);

                // Получаем цену и ссылку на товар
                actions.scrollAndClick(categoryName);

                // Перезагрузка chrome под новым IP
                driverManager.restartDriverWithCleanUserData();

                System.out.println("Ссылка на товар обработана: " + url + "\n");
            }
            System.out.println("Все товары по ссылкам успешно записаны в базу данных");
        } catch (Exception e) {
            LOGGER.severe("Ошибка при выполнении main функции: " + e.getMessage());
        } finally {
            driverManager.cleanUp();
        }
    }
}