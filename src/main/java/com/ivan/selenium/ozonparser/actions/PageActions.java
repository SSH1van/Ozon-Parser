package com.ivan.selenium.ozonparser.actions;

import com.ivan.selenium.ozonparser.data.DatabaseManager;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;
import java.text.SimpleDateFormat;

import java.util.List;
import java.util.Date;
import java.util.Random;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedWriter;

public class PageActions {
    public static WebDriver driver;
    private static final int MAX_ATTEMPTS = 5;

    public PageActions(WebDriver driver) {
        PageActions.driver = driver;
    }

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
            System.err.println("Ошибка при выполнении extractCategory: " + e.getMessage());
            return "";
        }
    }

    public void scrollAndClick(String categoryName) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        Random random = new Random();

        try {
            do {
                if (scrollToBottom(jsExecutor, random)) { return; }
                collectPageData();
            } while (navigateToNextPage(jsExecutor));
        } catch (Exception e) {
            System.err.println("Ошибка при выполнении scrollAndClick: " + e.getMessage());
        }

        // \УДАЛИТЬ/ ДЛЯ ТЕСТИРОВАНИЯ ПРОГРАММЫ \УДАЛИТЬ/
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = categoryName + "_" + timestamp + ".txt";
            System.out.println("Сохранил страницу при завершении обработки ссылки в scrollAndClick в файл " + fileName);
            savePageSource(fileName);
        } catch (Exception e) {
            System.err.println("Ошибка при сохранении страницы: " + e.getMessage());
        }
    }

    /************************************************
     *   ПРОКРУТКА СТРАНИЦЫ ВНИЗ ПОКА ЭТО ВОЗМОЖНО  *
     ************************************************/
    private boolean scrollToBottom(JavascriptExecutor jsExecutor, Random random) {
        Actions actions = new Actions(driver);
        long lastHeight = 0;
        try {
            lastHeight = getScrollHeight(jsExecutor);
        } catch (Exception e) {
            System.err.println("Ошибка при получении высоты страницы: " + e.getMessage());
        }
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
                System.err.println("Ошибка при выполнении scrollToBottom: " + e.getMessage());
                retryCount = 0;
            }
        }
        return false;
    }

    private long getScrollHeight(JavascriptExecutor jsExecutor) {
        Object result = jsExecutor.executeScript("return document.body.scrollHeight");
        if (result instanceof Number) {
            return ((Number) result).longValue();
        } else {
            throw new IllegalStateException("Не удалось получить высоту страницы.");
        }
    }

    /************************************************
     *    СБОР ДАННЫХ СО СТРАНИЦЫ И ЗАПИСЬ В БД     *
     ************************************************/
    private void collectPageData() {
        try {
            WebElement paginatorContent = driver.findElement(By.id("paginatorContent"));
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
        } catch (Exception e) {
            System.err.println("Ошибка при выполнении collectPageData: " + e.getMessage());

            // \УДАЛИТЬ/ ДЛЯ ТЕСТИРОВАНИЯ ПРОГРАММЫ \УДАЛИТЬ/
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = timestamp + ".txt";
            System.out.println("Сохранил таблицу при ошибке получения контента в paginatorContent в файл " + fileName);
            savePageSource(fileName);
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
            // Ошибка поиска ожидаема - отсутствие следующей страницы
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
            System.err.println("Ошибка при выполнении navigateToNextPage: " + e.getMessage());
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
            System.err.println("Ошибка при нажатии на кнопку 'Дальше': " + e.getMessage());
            return false;
        }
    }

    private boolean waitForNextPage(WebDriverWait wait) {
        try {
            // Ожидаем появления индикатора ошибки
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-widget='searchResultsError']")
            ));
            System.err.println("Контент на новой странице отсутствует");
            return false;
        } catch (TimeoutException e) {
            // Ошибки нет, страница загрузилась
            return true;
        }
    }

    /************************************************
     *                DEBUG РЕШЕНИЯ                 *
     ************************************************/
    private void savePageSource(String name) {
        try {
            // Получаем HTML-код текущей страницы
            String pageSource = driver.getPageSource();

            // Указываем путь к папке log
            File logDir = new File("results/" + DatabaseManager.globalFolderName + "/log");
            if (!logDir.exists() && !logDir.mkdirs()) {
                System.err.println("Не удалось создать директорию: " + logDir.getAbsolutePath());
                return; // Прекращаем выполнение метода, если директория не создана
            }

            // Формируем путь к файлу внутри папки log
            File file = new File(logDir, name);

            // Записываем HTML-код в файл
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                assert pageSource != null;
                writer.write(pageSource);
            }
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении HTML-кода: " + e.getMessage());
        }
    }
}
