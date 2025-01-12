package com.ivan.selenium.ozonparser;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public void getProducts() throws InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Set<String> seenProducts = new HashSet<>();
        boolean hasMoreData = true;

        while (hasMoreData) {
            // Ожидание загрузки элемента paginatorContent
            WebElement paginatorContent = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("paginatorContent")));

            // Поиск всех карточек товаров в текущей видимой области
            List<WebElement> productTiles = paginatorContent.findElements(By.cssSelector("[data-widget='searchResultsV2'] .tile-root"));

            for (WebElement productTile : productTiles) {
                try {
                    // Извлечение названия товара, его цены и ссылки
                    String productName = productTile.findElement(By.cssSelector(".tile-clickable-element .tsBody500Medium")).getText();

                    String productPriceString = productTile.findElement(By.cssSelector(".c3023-a1.tsHeadline500Medium")).getText();
                    productPriceString = productPriceString.substring(0, productPriceString.length() - 1).replaceAll(" ", "");
                    int productPrice = Integer.parseInt(productPriceString);

                    String productLink = "https://www.ozon.ru" + productTile.findElement(By.cssSelector(".tile-clickable-element")).getDomAttribute("href");

                    if (seenProducts.add(productName)) {
                        DatabaseManager.insertProduct(productName, productPrice, productLink);
                    }
                } catch (Exception e) {
                    // Обработка случаев, когда элементы не могут быть найдены
                    System.err.println("Ошибка при извлечении данных для товара: " + e.getMessage());
                }
            }

            // Прокрутка вниз для загрузки новых товаров
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("window.scrollBy(0, document.body.scrollHeight);");

            // Ожидание загрузки новых товаров
            Thread.sleep(2000); // Отрегулируйте эту задержку в зависимости от производительности страницы

            // Проверка, были ли загружены новые товары, путем сравнения текущего размера списка товаров
            int currentSize = paginatorContent.findElements(By.cssSelector("[data-widget='searchResultsV2'] .tile-root")).size();
            hasMoreData = currentSize > seenProducts.size();
        }
    }
}
