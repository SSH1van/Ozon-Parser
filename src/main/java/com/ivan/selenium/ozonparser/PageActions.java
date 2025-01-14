package com.ivan.selenium.ozonparser;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.List;

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

    public void scrollAndClick () {
        int timeWait = 200;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(timeWait));
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;

        try {
            do {
                scrollToBottom(jsExecutor, timeWait);

                collectPageData();
            } while (navigateToNextPage(jsExecutor, wait));
        } catch (Exception e) {
            System.err.println("Ошибка при выполнении scrollAndClick: " + e.getMessage());
        }
    }

    private void scrollToBottom(JavascriptExecutor jsExecutor, int timeWait) throws InterruptedException {
        long lastHeight = getScrollHeight(jsExecutor);
        int iterations = 0;

        while (true) {
            jsExecutor.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            TimeUnit.MILLISECONDS.sleep(timeWait);

            long newHeight = getScrollHeight(jsExecutor);
            if (newHeight != lastHeight) {
                lastHeight = newHeight;
                iterations = 0;
            } else if (iterations < 15) {
                iterations++;
            } else {
                return;
            }
        }
    }

    private void collectPageData() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            WebElement paginatorContent = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("paginatorContent")));
            List<WebElement> productTiles = paginatorContent.findElements(By.cssSelector("[data-widget='searchResultsV2'] .tile-root"));

            for (WebElement productTile : productTiles) {
                try {
                    // Получение цены товара
                    WebElement priceElement = productTile.findElement(By.cssSelector(".tsHeadline500Medium"));
                    String productPriceString = priceElement.getText().replaceAll("\\D", ""); // Удаление всех нецифровых символов
                    int productPrice = Integer.parseInt(productPriceString);

                    // Получение ссылки на товар
                    String productLink = "https://www.ozon.ru" + Objects.requireNonNull(productTile.findElement(By.cssSelector(".tile-clickable-element")).getDomAttribute("href")).split("\\?")[0];

                    DatabaseManager.insertProduct(productPrice, productLink);

                } catch (Exception e) {
                    // Ошибка получения цены ожидаема
                }
            }
            System.out.println("Страница записана в базу данных");
        } catch (TimeoutException e) {
            System.err.println("Таймаут ожидания элемента paginatorContent: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Общая ошибка: " + e.getMessage());
        }
    }

    private boolean navigateToNextPage(JavascriptExecutor jsExecutor, WebDriverWait wait) {
        try {
            WebElement nextButton = driver.findElement(By.xpath("//div[text()='Дальше']/ancestor::a"));
            jsExecutor.executeScript("arguments[0].scrollIntoView(true);", nextButton);
            jsExecutor.executeScript("window.scrollBy(0, -200);");
            nextButton.click();
            TimeUnit.SECONDS.sleep(2);

            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-widget='searchResultsError']")
            ));
            return false;
        } catch (Exception e) {
            // Ошибка поиска элемента ожидаема
            return true;
        }
    }

    private static long getScrollHeight(JavascriptExecutor jsExecutor) {
        Object result = jsExecutor.executeScript("return document.body.scrollHeight");
        if (result instanceof Number) {
            return ((Number) result).longValue();
        } else {
            throw new IllegalStateException("Не удалось получить высоту страницы.");
        }
    }
}
