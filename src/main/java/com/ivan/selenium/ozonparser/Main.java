package com.ivan.selenium.ozonparser;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private static final String OZON_CATEGORY_URL = "https://www.ozon.ru/category/smartfony-15502/apple-26303000/";

    static String relativePath = "User Data";
    static long timeRefresh = 400;
    static long timeSleep = 300000;
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

            // Открываем ссылку на товар
            actions.openUrl(OZON_CATEGORY_URL, timeSleep);
        } catch (Exception e) {
            LOGGER.severe("Произошла ошибка: " + e.getMessage());
        } finally {
            driverManager.cleanUp();
        }

    }
}