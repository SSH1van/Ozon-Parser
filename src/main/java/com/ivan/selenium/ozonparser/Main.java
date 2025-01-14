package com.ivan.selenium.ozonparser;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private static final List<String> urls = List.of(
            "https://www.ozon.ru/category/smartfony-15502/samsung-24565087/?__rr=1&page=801",
            "https://www.ozon.ru/category/smartfony-15502/apple-26303000/?__rr=2&page=801"
    );

//    private static final String OZON_CATEGORY_URL = "https://www.ozon.ru/category/smartfony-15502/samsung-24565087/?__rr=1&page=801";
//    private static final String OZON_CATEGORY_URL = "https://www.ozon.ru/category/smartfony-15502/apple-26303000/";

    static String relativePath = "User Data";
    static long timeRefresh = 5000;
    static long timeSleep = 0;
    static boolean headless = false;

    public static void main(String[] args) {
        WebDriverManager driverManager = new WebDriverManager();
        ChromeOptions options = WebDriverManager.createOptions(relativePath, headless);
        WebDriver driver = driverManager.initDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofMillis(timeRefresh));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Программа завершена. Освобождаем ресурсы...");
            driverManager.cleanUp();
        }));

        try {
            PageActions actions = new PageActions(driver);
            StringProcessing stringProcessing = new StringProcessing();

            for (String url : urls) {
                // Открываем ссылку на товар
                actions.openUrl(url, timeSleep);

                // Получаем название таблицы
                String categoryName = stringProcessing.extractCategory(url);

                // Создать базу данных и таблицу
                DatabaseManager.createTable(categoryName);

                // Получаем название, цену и ссылку
                actions.scrollAndClick();
            }
        } catch (Exception e) {
            LOGGER.severe("Произошла ошибка: " + e.getMessage());
        } finally {
            driverManager.cleanUp();
        }

    }
}