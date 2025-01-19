package com.ivan.selenium.ozonparser;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Random;

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

    public String extractCategory() {
        WebElement headerElement = driver.findElement(By.cssSelector("[data-widget='resultsHeader'] h1"));
        return headerElement.getText();
    }

    public void scrollAndClick() {
        int timeWait = 200;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(timeWait));
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        Actions actions = new Actions(driver);
        Random random = new Random();

        try {
            do {
                if (scrollToBottom(jsExecutor, actions, timeWait, random)) { return; }
                collectPageData();
            } while (navigateToNextPage(actions, wait, random));
        } catch (Exception e) {
            System.err.println("Ошибка при выполнении scrollAndClick: " + e.getMessage());
        }
    }

    private boolean scrollToBottom(JavascriptExecutor jsExecutor, Actions actions, int timeWait, Random random) throws InterruptedException {
        long lastHeight = getScrollHeight(jsExecutor);
        int i = 0;

        while (true) {
            actions.scrollByAmount(0, 1000 + random.nextInt(800)).perform();
            TimeUnit.MILLISECONDS.sleep(timeWait + random.nextInt(100));

            if (checkLockScreen()) return true;

            long newHeight = getScrollHeight(jsExecutor);
            if (newHeight != lastHeight) {
                lastHeight = newHeight;
                i = 0;
            } else if (i < 12) {
                i++;
            } else {
                return false;
            }
        }
    }

    public boolean checkLockScreen() {
        try {
            driver.findElement(By.xpath("//button[text()='Обновить']"));
            System.out.println("Блокировка экрана!");

            Random random = new Random();
            for (int i = 0; i < 10;  i++) {
                driver.manage().deleteAllCookies();
                driver.navigate().refresh();
                TimeUnit.MILLISECONDS.sleep(random.nextInt(1000) + random.nextInt(500));

                try {
                    driver.findElement(By.xpath("//button[text()='Обновить']"));
                } catch (Exception e) {
                    System.out.println("Блокировка экрана снята!");
                    throw new NoSuchElementException("Экран блокировки не найден");
                }
            }
            return true;
        } catch (Exception e) {
            // Ошибка поиска экрана блокировки ожидаема
            return false;
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
            System.out.println("Страница записана в базу данных в таблицу: " + DatabaseManager.tableName);
        } catch (TimeoutException e) {
            System.err.println("Таймаут ожидания элемента paginatorContent: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Общая ошибка: " + e.getMessage());
        }
    }

    private boolean navigateToNextPage(Actions actions, WebDriverWait wait, Random random) throws InterruptedException {
        WebElement nextButton;
        try {
            nextButton = driver.findElement(By.xpath("//div[text()='Дальше']/ancestor::a"));
        } catch (Exception e) {
            // Отсутствие следующей страницы
            return false;
        }

        actions.moveToElement(nextButton).perform();
        try {
            nextButton.click();
        } catch (Exception TimeoutException) {
            System.out.println("Слишком долгий переход на страницу по кнопке 'Дальше'");
            // Ошибка загрузки страницы ожидаема
        }
        TimeUnit.MILLISECONDS.sleep(2000 + random.nextInt(100));

        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-widget='searchResultsError']")
            ));
            return false;
        } catch (Exception e) {
            // Отсутствие контента на новой странице
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
