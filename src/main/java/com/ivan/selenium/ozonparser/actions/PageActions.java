package com.ivan.selenium.ozonparser.actions;

import com.ivan.selenium.ozonparser.core.WebDriverManager;
import com.ivan.selenium.ozonparser.data.DatabaseService;
import static com.ivan.selenium.ozonparser.core.Main.timeSleep;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import java.util.stream.Collectors;

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

    // Перезапускает driver в случае возникновения ошибки для повторной попытки выполнения действий
    private void restartOnError(WebDriverManager driverManager) {
        String currentUrl = driver.getCurrentUrl();
        driverManager.restartDriverWithCleanUserData();
        openUrl(currentUrl, timeSleep);
    }


    public List<String> extractCategoryPath(WebDriverManager driverManager) {
        try {
            WebElement breadCrumbs = driver.findElement(By.cssSelector("[data-widget='breadCrumbs']"));
            List<WebElement> crumbElements = breadCrumbs.findElements(By.cssSelector("li"));

            // Собираем путь из категорий
            List<String> categoryPath = crumbElements.stream()
                    .map(li -> {
                        try {
                            WebElement span = li.findElement(By.cssSelector("span"));
                            return span.getText().trim();
                        } catch (Exception e) {
                            return "";
                        }
                    })
                    .filter(text -> !text.isEmpty())
                    .collect(Collectors.toCollection(ArrayList::new)); // Используем ArrayList для добавления

            // Добавляем категорию из заголовка в конец списка
            WebElement headerElement = driver.findElement(By.cssSelector("[data-widget='resultsHeader'] h1"));
            String headerCategory = headerElement.getText().trim();
            categoryPath.add(headerCategory);

            return categoryPath;
        } catch (Exception e) {
            LOGGER.severe("Ошибка при выполнении extractCategoryPath: " + e.getMessage());
            savePageSource("extractCategoryPath_ERROR_");
            restartOnError(driverManager);
            return extractCategoryPath(driverManager); // Рекурсивный вызов после перезапуска
        }
    }

    public void scrollAndClick(List<String> categoryPath, WebDriverManager driverManager, DatabaseService dbManager) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        Random random = new Random();
        Set<String> collectedUrls = new HashSet<>();

        try {
            do {
                Set<String> collectedHashes = new HashSet<>(256);

                CompletableFuture<Void> scrollFuture = CompletableFuture.runAsync(() -> scrollToBottom(jsExecutor, random));
                while (!scrollFuture.isDone()) {
                    CompletableFuture<Void> collectFuture = CompletableFuture.runAsync(() -> collectPageData(collectedHashes, collectedUrls, categoryPath, dbManager));
                    collectFuture.get();

                    TimeUnit.MILLISECONDS.sleep(1000 + random.nextInt(500));
                }
                scrollFuture.get();
                // Окончательная проверка на новый контент
                CompletableFuture<Void> collectFuture = CompletableFuture.runAsync(() -> collectPageData(collectedHashes, collectedUrls, categoryPath, dbManager));
                collectFuture.get();

                System.out.println("Страница записана в базу данных для категории: " + categoryPath.getLast());
            } while (navigateToNextPage(jsExecutor));
        } catch (Exception e) {
            LOGGER.severe("Ошибка при выполнении scrollAndClick: " + e.getMessage());
            savePageSource("scrollAndClick_ERORR_");
            restartOnError(driverManager);
            scrollAndClick(categoryPath, driverManager, dbManager);
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
    private void collectPageData(Set<String> collectedHashes, Set<String> collectedUrls, List<String> categoryPath, DatabaseService dbManager) {
        try {
            WebElement paginator = driver.findElement(By.id("paginator"));
            List<WebElement> searchResults = paginator.findElements(By.cssSelector("[data-widget='searchResultsV2']"));

            for (WebElement searchResult : searchResults) {
                String productHtml = searchResult.getDomProperty("outerHTML");
                if (productHtml == null) continue;

                // Получаем только части двух товаров, где есть ссылки для экономии хранимых данных и получения хеша
                String hash;
                String trimmedHtml;
                if (productHtml.length() >= 7600) {
                    trimmedHtml = productHtml.substring(100, 300) + productHtml.substring(7400, 7600);
                } else {
                    trimmedHtml = productHtml.substring(Math.min(100, productHtml.length()), Math.min(300, productHtml.length()));
                }
                hash = hashString(trimmedHtml);

                // Добавляем хеш в список обработанных
                if (!collectedHashes.add(hash)) {
                    continue;
                }

                List<WebElement> productTiles = searchResult.findElements(By.cssSelector(".tile-root"));
                for (WebElement productTile : productTiles) {
                    try {
                        // Получение ссылки на товар
                        String productUrl = extractProductUrl(productTile);
                        if (productUrl == null || !collectedUrls.add(productUrl)) {
                            continue;
                        }

                        // Получение цены товара
                        int productPrice = extractProductPrice(productTile);

                        // Сохранение в БД
                        dbManager.addProductWithPrice(categoryPath, productUrl, productPrice);
                    } catch (Exception e) {
                        // Ошибка получения цены ожидаема
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Ошибка при выполнении collectPageData: " + e.getMessage());
            savePageSource("collectPageData_ERORR_");
        }
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Ошибка при вычислении хеша", e);
        }
    }

    // Извлечения URL
    private String extractProductUrl(WebElement productTile) {
        try {
            WebElement linkElement = productTile.findElement(By.cssSelector(".tile-clickable-element"));
            String href = linkElement.getDomAttribute("href");
            if (href == null) return null;
            return "https://www.ozon.ru" + href.split("\\?")[0];
        } catch (Exception e) {
            LOGGER.warning("Не удалось извлечь URL: " + e.getMessage());
            return null;
        }
    }

    // Извлечение цены
    private int extractProductPrice(WebElement productTile) throws NumberFormatException {
        WebElement priceElement = productTile.findElement(By.cssSelector(".tsHeadline500Medium"));
        String priceText = priceElement.getText().replaceAll("\\D", "");
        return Integer.parseInt(priceText);
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
    public static void savePageSource(String fileName) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            fileName += timestamp + ".txt";

            // Получаем HTML-код текущей страницы
            String pageSource = driver.getPageSource();

            // Указываем путь к папке log
            File logDir = new File("log/" + DatabaseService.globalFolderName);
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
