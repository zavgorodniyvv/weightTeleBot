# ========================
# 1-й этап: Сборка кода
# ========================
FROM maven:3.8.7-eclipse-temurin-17 AS build

# Создадим директорию для проекта
WORKDIR /app

# Скопируем файл pom.xml и скачаем зависимости отдельно (для оптимизации кэширования)
COPY pom.xml .
RUN mvn dependency:go-offline

# Копируем остальной исходный код
COPY . .

# Собираем проект (исключая тесты, если не нужны)
RUN mvn clean package -DskipTests

# ========================
# 2-й этап: Финальный образ
# ========================
FROM eclipse-temurin:17
WORKDIR /app

# Копируем jar из предыдущего этапа
COPY --from=build /app/target/*.jar /app/bot.jar

# Устанавливаем значения по умолчанию для переменных
# (при необходимости переопределяются на этапе "docker run")
ENV BOT_USERNAME="CHANGE_ME_USERNAME"
ENV BOT_TOKEN="CHANGE_ME_TOKEN"

# Запускаем бот
ENTRYPOINT ["java", "-jar", "/app/bot.jar"]
