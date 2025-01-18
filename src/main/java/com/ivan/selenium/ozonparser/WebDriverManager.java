package com.ivan.selenium.ozonparser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.Cookie;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.file.Paths;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebDriverManager {
    private static final Logger LOGGER = Logger.getLogger(WebDriverManager.class.getName());
    private static WebDriver driver;

    public WebDriver initDriver(ChromeOptions options) {
        driver = new ChromeDriver(options);
        return driver;
    }

    // Сборщик мусора
    public void cleanUp() {
        if (driver != null) {
            driver.quit();
        }
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            int exitCode;
            boolean isRunning = isProcessRunning();

            if (!isRunning) {
                LOGGER.warning("Процесс chromedriver уже завершён или не найден.");
                return;
            }

            if (osName.contains("win")) {
                exitCode = executeCommand("taskkill", "/F", "/IM", "chromedriver.exe", "/T");
            } else {
                exitCode = executeCommand("pkill", "-f", "chromedriver");
            }

            if (exitCode == 0) {
                LOGGER.info("Процесс chromedriver успешно завершен.");
            } else {
                LOGGER.warning("Не удалось завершить процесс chromedriver. Код выхода: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Произошла ошибка при завершении процесса chromedriver.", e);
            Thread.currentThread().interrupt();
        }
    }

    // Проверяем, что останавливаемый процесс не запущен
    private boolean isProcessRunning() throws IOException {
        String command = System.getProperty("os.name").toLowerCase().contains("win") ? "tasklist" : "ps -e";
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        Process process = processBuilder.start();

        try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = input.readLine()) != null) {
                if (line.contains("chromedriver")) {
                    return true;
                }
            }
        }
        return false;
    }

    private int executeCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = processBuilder.start();
        return process.waitFor();
    }

    // Опции для Chrome
    public static ChromeOptions createOptions(boolean headless) {
        ChromeOptions chromeOptions = new ChromeOptions();

        // Указываем путь к chromedriver
        String driverPath = Paths.get("chromedriver/chromedriver.exe").toAbsolutePath().toString();
        // String driverPath = Paths.get("chromedriver-linux64/chromedriver").toAbsolutePath().toString();
        System.setProperty("webdriver.chrome.driver", driverPath);

        // Убираем заметные следы Selenium
        // chromeOptions.addArguments("--no-sandbox");
        chromeOptions.addArguments("--disable-blink-features=AutomationControlled");
        chromeOptions.addArguments("--disable-infobars");
        chromeOptions.addArguments("--disable-notifications");
        chromeOptions.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        chromeOptions.setExperimentalOption("useAutomationExtension", false);

        // Настройка DesiredCapabilities
        DesiredCapabilities caps = new DesiredCapabilities();
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.default_content_setting_values.notifications", 2); // Отключаем всплывающие уведомления
        caps.setCapability("goog:chromeOptions", prefs);

        // Интеграция ChromeOptions с DesiredCapabilities
        chromeOptions.merge(caps);

        // Выбираем user-agent
        chromeOptions.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");

        if (headless) {
            chromeOptions.addArguments("--headless=new");
            chromeOptions.addArguments("--disable-gpu");
        }

        return chromeOptions;
    }

    public static void loadCookies(String cookiesFilePath) {
        try {
            // Чтение файла cookies.json
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> cookies = mapper.readValue(new File(cookiesFilePath), new TypeReference<List<Map<String, Object>>>() {});

            for (Map<String, Object> cookieMap : cookies) {
                String name = (String) cookieMap.get("name");
                String value = (String) cookieMap.get("value");
                String domain = (String) cookieMap.get("domain");
                String path = (String) cookieMap.get("path");
                Long expiry = cookieMap.get("expiry") != null ? Long.valueOf(cookieMap.get("expiry").toString()) : null;
                boolean isSecure = cookieMap.get("secure") != null && (Boolean) cookieMap.get("secure");

                Cookie cookie = new Cookie.Builder(name, value)
                        .domain(domain)
                        .path(path)
                        .expiresOn(expiry != null ? new java.util.Date(expiry * 1000) : null)
                        .isSecure(isSecure)
                        .build();

                driver.manage().addCookie(cookie);
            }

            // Обновление страницы для применения куков
            driver.navigate().refresh();

        } catch (IOException e) {
            System.out.println("Возникла ошибка при записи куки файлов: " + e.getMessage());
        }
    }
}
