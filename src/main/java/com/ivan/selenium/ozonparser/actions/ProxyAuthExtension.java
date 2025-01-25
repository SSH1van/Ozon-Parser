package com.ivan.selenium.ozonparser.actions;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ProxyAuthExtension {
    public static File createProxyAuthExtension(String proxyHost, int proxyPort, String proxyUser, String proxyPassword) throws IOException {
        String manifestJson = """
                {
                  "version": "1.0.0",
                  "manifest_version": 2,
                  "name": "Chrome Proxy",
                  "permissions": [
                    "proxy", "tabs", "unlimitedStorage", "storage", "<all_urls>", "webRequest", "webRequestBlocking"
                  ],
                  "background": {
                    "scripts": ["background.js"]
                  },
                  "minimum_chrome_version": "22.0.0"
                }""";

        String backgroundJs = "var config = {\n" +
                "    mode: 'fixed_servers',\n" +
                "    rules: {\n" +
                "        singleProxy: {\n" +
                "            scheme: 'http',\n" +
                "            host: '" + proxyHost + "',\n" +
                "            port: parseInt('" + proxyPort + "')\n" +
                "        },\n" +
                "        bypassList: ['localhost']\n" +
                "    }\n" +
                "};\n" +
                "\n" +
                "chrome.proxy.settings.set({value: config, scope: 'regular'}, function() {});\n" +
                "\n" +
                "function callbackFn(details) {\n" +
                "    return {\n" +
                "        authCredentials: {\n" +
                "            username: '" + proxyUser + "',\n" +
                "            password: '" + proxyPassword + "'\n" +
                "        }\n" +
                "    };\n" +
                "}\n" +
                "\n" +
                "chrome.webRequest.onAuthRequired.addListener(\n" +
                "    callbackFn,\n" +
                "    {urls: ['<all_urls>']},\n" +
                "    ['blocking']\n" +
                ");";

        // Создаем временную папку
        Path tempDir = Files.createTempDirectory("proxyAuthExtension");
        File manifestFile = new File(tempDir.toFile(), "manifest.json");
        File backgroundFile = new File(tempDir.toFile(), "background.js");

        // Записываем файлы
        try (FileWriter writer = new FileWriter(manifestFile)) {
            writer.write(manifestJson);
        }
        try (FileWriter writer = new FileWriter(backgroundFile)) {
            writer.write(backgroundJs);
        }

        // Упаковка файлов в ZIP
        File zipFile = new File(tempDir.toFile().getAbsolutePath() + ".zip");
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addFileToZip(tempDir.toFile(), manifestFile, zipOut);
            addFileToZip(tempDir.toFile(), backgroundFile, zipOut);
        }

        return zipFile;
    }

    private static void addFileToZip(File baseDir, File file, ZipOutputStream zipOut) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            String zipEntryName = baseDir.toPath().relativize(file.toPath()).toString();
            zipOut.putNextEntry(new ZipEntry(zipEntryName));
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            zipOut.closeEntry();
        }
    }
}


