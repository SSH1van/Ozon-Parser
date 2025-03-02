# Ozon-Parser

## About
Парсинг товаров OZON из файла urls.csv, в котором указываются ссылки на категории товаров

## Configuration
Перед запуском приложения создайте файлы `application.properties` и `config.yaml` в директории `resources` со следующими структурами:

### application.properties
Файл содержит настройки подключения к прокси и базе данных. Пример структуры:

```properties
# Настройки подключения к прокси
proxy.username=username
proxy.password=password

# Настройки подключения к базе данных (PostgreSQL)
database.username=postgres
database.password=root
```

### config.yaml
Файл содержит список прокси-серверов. Пример структуры:
```properties
proxies:
  - 127.0.0.1:8080
  - 127.0.0.2:8080
  - 127.0.0.3:8080
  - 127.0.0.4:8080
```