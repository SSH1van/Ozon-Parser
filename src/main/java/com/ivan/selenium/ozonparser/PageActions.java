package com.ivan.selenium.ozonparser;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
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

    public void getProducts() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement paginatorContent = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("paginatorContent")));
        List<WebElement> productTiles = paginatorContent.findElements(By.cssSelector("[data-widget='searchResultsV2'] .tile-root"));

        for (WebElement productTile : productTiles) {
            try {
                // Получение цены товара
                String productPriceString;
                try {
                    productPriceString = productTile.findElement(By.cssSelector(".tsHeadline500Medium")).getText();
                }
                catch (Exception e) {
                    // Если товара нет в наличии
                    continue;
                }
                productPriceString = productPriceString.substring(0, productPriceString.length() - 1).replaceAll(" ", "");
                int productPrice = Integer.parseInt(productPriceString);

                // Получение ссылки на товар
                String productLink = "https://www.ozon.ru" + productTile.findElement(By.cssSelector(".tile-clickable-element")).getDomAttribute("href");

                DatabaseManager.insertProduct(productPrice, productLink);
            } catch (Exception e) {
                System.err.println("Ошибка при извлечении данных для товара: " + e.getMessage());
            }
        }
        System.out.println("Страница записана в базу данных");
    }

    public void scrollAndClick () {
        int timeWait = 200;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(timeWait));
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;

        try {
            while (true) {
                scrollToBottom(jsExecutor, timeWait);

                collectPageData();
                if (!navigateToNextPage(jsExecutor, wait)) {
                    break;
                }
            }
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
        try {
            getProducts();
        } catch (Exception e) {
            System.err.println("Ошибка при сборе данных: " + e.getMessage());
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
                    By.xpath("//div[text()='Простите, произошла ошибка. Попробуйте обновить страницу или вернуться на шаг назад.']")
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
