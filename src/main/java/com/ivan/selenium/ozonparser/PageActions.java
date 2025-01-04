package com.ivan.selenium.ozonparser;

import org.openqa.selenium.WebDriver;

import java.util.concurrent.TimeUnit;

public class PageActions {
    private final WebDriver driver;

    public PageActions(WebDriver driver) {
        this.driver = driver;
    }

    // Открытие ссылки
    public void openUrl(String url, long timeSleep) throws InterruptedException {
        try {
            driver.get(url);
        } catch (Exception TimeoutException) {
            // Ошибка загрузки страницы ожидаема
        }
        TimeUnit.SECONDS.sleep(timeSleep);
    }
}
