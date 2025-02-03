package com.ivan.selenium.ozonparser.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

public class CsvToUrls {
    private static final Logger LOGGER = Logger.getLogger(CsvToUrls.class.getName());

    public static List<String> readUrlsFromCsv(String filePath) {
        List<String> urls = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty() && line.contains("https://")) {
                    urls.add(line.trim());
                }
            }
        } catch (IOException e) {
            LOGGER.severe("Ошибка чтения файла: " + e.getMessage());
        }

        // Перемешиваем список
        //Collections.shuffle(urls);

        return urls;
    }
}
