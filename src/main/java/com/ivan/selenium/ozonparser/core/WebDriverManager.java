package com.ivan.selenium.ozonparser.core;

import com.ivan.selenium.ozonparser.config.ConfigReader;
import com.ivan.selenium.ozonparser.actions.PageActions;
import com.ivan.selenium.ozonparser.actions.ProxyAuthExtension;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.nio.file.Paths;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebDriverManager {
    private static final Logger LOGGER = Logger.getLogger(WebDriverManager.class.getName());
    private static WebDriver driver;

    private static int currentIndex = -1;
    private static final List<String> proxyList = new ArrayList<>();

    public WebDriverManager() {
        proxyList.add("");
        proxyList.addAll(ConfigReader.getProxies());
    }

    public WebDriver initDriver(ChromeOptions options) {
        driver = new ChromeDriver(options);
        return driver;
    }

    /************************************************
     *               СБОРЩИК МУСОРА                 *
     ************************************************/
    public void cleanUp() {
        if (driver != null) {
            driver.quit();
        }

        try {
            String osName = System.getProperty("os.name").toLowerCase();
            boolean isRunning = isProcessRunning(osName);

            if (!isRunning) {
                // Распространённое предупреждение, пропускаем вывод сообщение о нём
                //LOGGER.warning("Процесс chromedriver уже завершён или не найден.");
                return;
            }

            int exitCode;
            if (osName.contains("win")) {
                // Windows: chromedriver.exe
                exitCode = executeCommand("taskkill", "/F", "/IM", "chromedriver.exe", "/T");
            } else {
                // Linux/macOS: chromedriver
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

    // Проверяем, что процесс запущен
    private boolean isProcessRunning(String osName) throws IOException {
        String command = osName.contains("win") ? "tasklist" : "pgrep -fl chromedriver";

        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        Process process = processBuilder.start();

        try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = input.readLine()) != null) {
                if (osName.contains("win")) {
                    if (line.toLowerCase().contains("chromedriver.exe")) {
                        return true;
                    }
                } else {
                    // Для Linux/macOS считаем, что pgrep уже фильтрует только нужные процессы
                    return true;
                }
            }
        }
        return false;
    }

    // Выполнение команды
    private int executeCommand(String... commands) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(commands).start();
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                LOGGER.warning("Ошибка команды: " + errorLine);
            }
        }
        return process.waitFor();
    }

    /************************************************
     *            ПАРАМЕТРЫ ДЛЯ CHROME              *
     ************************************************/
    public ChromeOptions createOptions(boolean headless) {
        ChromeOptions chromeOptions = new ChromeOptions();
        setupProxy(chromeOptions);
        String userDataDirPath = configureDriverPaths();
        addBaseArguments(chromeOptions, userDataDirPath);
        setHeadlessMode(chromeOptions, headless);
        return chromeOptions;
    }

    private void setupProxy(ChromeOptions chromeOptions) {
        currentIndex = (currentIndex + 1) % proxyList.size();
        String currentProxy = proxyList.get(currentIndex);

        if (!currentProxy.isEmpty()) {
            String[] proxyParts = currentProxy.split(":");
            String proxyHost = proxyParts[0];
            int proxyPort = Integer.parseInt(proxyParts[1]);

            String proxyUser = ConfigReader.getProxyUser();
            String proxyPassword = ConfigReader.getProxyPassword();

            try {
                File proxyExtension = ProxyAuthExtension.createProxyAuthExtension(proxyHost, proxyPort, proxyUser, proxyPassword);
                chromeOptions.addExtensions(proxyExtension);
            } catch (IOException e) {
                throw new RuntimeException("Ошибка при создании расширения для прокси: ", e);
            }
        }
    }

    private String configureDriverPaths() {
        String osName = System.getProperty("os.name").toLowerCase();
        String driverPath;

        if (osName.contains("win")) {
            driverPath = Paths.get("chromedriver/chromedriver.exe").toAbsolutePath().toString();
        } else {
            driverPath = Paths.get("chromedriver-linux64/chromedriver").toAbsolutePath().toString();
        }

        System.setProperty("webdriver.chrome.driver", driverPath);
        return Paths.get("user-data-dir").toAbsolutePath().toString();
    }

    private void addBaseArguments(ChromeOptions chromeOptions, String userDataDirPath) {
        // Убираем заметные следы Selenium
        chromeOptions.addArguments("--no-sandbox", "--window-size=1920,1080", "user-data-dir=" + userDataDirPath,
                "--disable-blink-features=AutomationControlled", "--incognito");

        // Параметры, которые пока что просто добавлены
        chromeOptions.addArguments("--lang=ru", "--disable-infobars", "--disable-popup-blocking",
                "--disable-dev-shm-usage", "--disable-webgl", "--disable-webrtc", "--disable-translate");
        chromeOptions.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        chromeOptions.setExperimentalOption("useAutomationExtension", false);

        // Выбираем user-agent
        //chromeOptions.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36");
        //chromeOptions.addArguments("user-agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36");
        chromeOptions.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");
    }

    private void setHeadlessMode(ChromeOptions chromeOptions, boolean headless) {
        if (headless) {
            chromeOptions.addArguments("--headless=new", "--disable-gpu");
        }
    }

    /************************************************
     *     ПЕРЕЗАПУСК DRIVER И ОЧИСТКА USERDATA     *
     ************************************************/
    public void restartDriverWithCleanUserData() {
        try {
            stopDriver();
            cleanUserData();
            startDriver();
        } catch (Exception e) {
            LOGGER.severe("Ошибка при перезапуске WebDriver: " + e.getMessage());
        }
    }

    private void stopDriver() {
        WebDriverManager driverManager = new WebDriverManager();
        driverManager.cleanUp();
    }

    private void cleanUserData() {
        String userDataDirPath = Paths.get("user-data-dir").toAbsolutePath().toString();
        File userDataDir = new File(userDataDirPath);

        if (userDataDir.exists()) {
            for (File file : Objects.requireNonNull(userDataDir.listFiles())) {
                deleteRecursively(file);
            }
        }
    }

    private void startDriver() {
        WebDriverManager driverManager = new WebDriverManager();
        ChromeOptions options = driverManager.createOptions(Main.headless);
        PageActions.driver = driverManager.initDriver(options);
        PageActions.driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(Main.timeLoad));
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            for (File child : Objects.requireNonNull(file.listFiles())) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            LOGGER.severe("Не удалось удалить файл/директорию: " + file.getAbsolutePath());
        }
    }
}
