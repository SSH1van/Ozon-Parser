package com.ivan.selenium.ozonparser.actions;

import com.ivan.selenium.ozonparser.data.DatabaseManager;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;
import java.text.SimpleDateFormat;

import java.util.*;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedWriter;

public class PageActions {
    public static WebDriver driver;
    private static final int MAX_ATTEMPTS = 5;
    private static final Logger LOGGER = Logger.getLogger(PageActions.class.getName());

    /************************************************
     *                БАЗОВЫЕ МЕТОДЫ                *
     ************************************************/
    public void openUrl(String url, long timeSleep) {
        try {
            driver.get(url);
            TimeUnit.SECONDS.sleep(timeSleep);
        } catch (Exception TimeoutException) {
            // Ошибка загрузки страницы ожидаема
        }
    }

    public String extractCategory() {
        try {
            WebElement headerElement = driver.findElement(By.cssSelector("[data-widget='resultsHeader'] h1"));
            return headerElement.getText();
        }
        catch (Exception e) {
            LOGGER.severe("Ошибка при выполнении extractCategory: " + e.getMessage());
            savePageSource("extractCategory_ERORR_");
            return "";
        }
    }

    public void scrollAndClick(String categoryName) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        Random random = new Random();

        try {
            do {
                Set<String> collectedLinks = new HashSet<>();

                CompletableFuture<Void> scrollFuture = CompletableFuture.runAsync(() -> scrollToBottom(jsExecutor, random));
                while (!scrollFuture.isDone()) {
                    CompletableFuture<Void> collectFuture = CompletableFuture.runAsync(() -> collectPageData(collectedLinks));
                    collectFuture.get();

                    TimeUnit.MILLISECONDS.sleep(1000 + random.nextInt(500));
                }
                scrollFuture.get();
                System.out.println("Страница записана в базу данных в таблицу: " + DatabaseManager.tableName);
            } while (navigateToNextPage(jsExecutor));
        } catch (Exception e) {
            LOGGER.severe("Ошибка при выполнении scrollAndClick: " + e.getMessage());
            savePageSource("scrollAndClick_ERORR_");
        }
    }

    /************************************************
     *   ПРОКРУТКА СТРАНИЦЫ ВНИЗ ПОКА ЭТО ВОЗМОЖНО  *
     ************************************************/
    private void scrollToBottom(JavascriptExecutor jsExecutor, Random random) {
        Actions actions = new Actions(driver);
        long lastHeight = getScrollHeight(jsExecutor);
        int waitTimeInMillis  = 800;
        int retryCount = 0;

        while (retryCount < MAX_ATTEMPTS) {
            try {
                actions.scrollByAmount(0, 2500 + random.nextInt(500)).perform();
                TimeUnit.MILLISECONDS.sleep(waitTimeInMillis + random.nextInt(300));

                // Проверяем, изменилась ли высота страницы
                long newHeight = getScrollHeight(jsExecutor);
                if (newHeight != lastHeight) {
                    lastHeight = newHeight;
                    retryCount = 0;
                } else {
                    retryCount++;
                }
            } catch (Exception e) {
                LOGGER.severe("Ошибка при выполнении scrollToBottom: " + e.getMessage());
                savePageSource("scrollToBottom_ERORR_");
                retryCount = 0;
            }
        }
    }

    private long getScrollHeight(JavascriptExecutor jsExecutor) {
        Object result = jsExecutor.executeScript("return document.body.scrollHeight");
        if (result instanceof Number) {
            return ((Number) result).longValue();
        } else {
            LOGGER.severe("Ошибка при получении высоты страницы в функции getScrollHeight");
            savePageSource("getScrollHeight_ERORR_");
        }
        return 0;
    }

    /************************************************
     *    СБОР ДАННЫХ СО СТРАНИЦЫ И ЗАПИСЬ В БД     *
     ************************************************/
    private void collectPageData(Set<String> collectedLinks) {
        try {
            WebElement paginatorContent = driver.findElement(By.id("paginatorContent"));
            List<WebElement> productTiles = paginatorContent
                    .findElements(By.cssSelector("[data-widget='searchResultsV2'] .tile-root"));

            for (WebElement productTile : productTiles) {
                try {
                    // Получение ссылки на товар
                    String productLink = "https://www.ozon.ru" +
                            Objects.requireNonNull(productTile.findElement(By.cssSelector(".tile-clickable-element"))
                            .getDomAttribute("href")).split("\\?")[0];
                    if (collectedLinks.contains(productLink)) {
                        continue;
                    }

                    // Получение цены товара
                    WebElement priceElement = productTile.findElement(By.cssSelector(".tsHeadline500Medium"));
                    String productPriceString = priceElement.getText().replaceAll("\\D", "");
                    int productPrice = Integer.parseInt(productPriceString);

                    // Добавляем ссылку в список уже обработанных
                    collectedLinks.add(productLink);

                    DatabaseManager.insertProduct(productPrice, productLink);
                } catch (Exception e) {
                    // Ошибка получения цены ожидаема
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Ошибка при выполнении collectPageData: " + e.getMessage());
            savePageSource("collectPageData_ERORR_");
        }
    }

    /************************************************
     *        ПЕРЕХОД НА СЛЕДУЮЩУЮ СТРАНИЦУ        *
     ************************************************/
    private boolean navigateToNextPage(JavascriptExecutor jsExecutor) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(1));

        // Поиск кнопки "Дальше"
        WebElement nextButton;
        try {
            nextButton = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//div[text()='Дальше']/ancestor::a")));
        } catch (Exception e) {
            System.out.println("Ошибка поиска кнопки ожидаема");
            return false;
        }

        try {
            // Скроллинг к кнопке "Дальше"
            scrollToElement(jsExecutor, nextButton);

            // Нажатие на кнопку "Дальше"
            if (!clickNextButton(nextButton)) {
                return false;
            }

            // Ожидание новой страницы
            return waitForNextPage(wait);
        } catch (Exception e) {
            LOGGER.severe("Ошибка при выполнении navigateToNextPage: " + e.getMessage());
            savePageSource("navigateToNextPage_ERORR_");
            return false;
        }
    }

    private void scrollToElement(JavascriptExecutor jsExecutor, WebElement element) {
        jsExecutor.executeScript("arguments[0].scrollIntoView(true);", element);
        jsExecutor.executeScript("window.scrollBy(0, -200);");
    }

    private boolean clickNextButton(WebElement nextButton) {
        try {
            nextButton.click();
            return true;
        } catch (Exception e) {
            LOGGER.severe("Ошибка при нажатии на кнопку 'Дальше': " + e.getMessage());
            savePageSource("clickNextButton_ERORR_");
            return false;
        }
    }

    private boolean waitForNextPage(WebDriverWait wait) {
        try {
            // Ожидаем появления индикатора ошибки
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-widget='searchResultsError']")
            ));
            System.out.println("Отсутствие контента на новой странице ожидаемо");
            return false;
        } catch (TimeoutException e) {
            // Ошибки нет, страница загрузилась
            return true;
        }
    }

    /************************************************
     *                 ЛОГИРОВАНИЕ                  *
     ************************************************/
    private void savePageSource(String fileName) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            fileName += timestamp + ".txt";

            // Получаем HTML-код текущей страницы
            String pageSource = driver.getPageSource();

            // Указываем путь к папке log
            File logDir = new File("results/" + DatabaseManager.globalFolderName + "/log");
            if (!logDir.exists() && !logDir.mkdirs()) {
                LOGGER.severe("Не удалось создать директорию: " + logDir.getAbsolutePath());
                return; // Прекращаем выполнение метода, если директория не создана
            }

            // Формируем путь к файлу внутри папки log
            File file = new File(logDir, fileName);

            // Записываем HTML-код в файл
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                assert pageSource != null;
                writer.write(pageSource);
            }
        } catch (IOException e) {
            LOGGER.severe("Ошибка при сохранении HTML-кода: " + e.getMessage());
        }
    }
}
