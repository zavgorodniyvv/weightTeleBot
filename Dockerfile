# ========================
# 1-й этап: Сборка кода
# ========================
FROM maven:3.9.9-eclipse-temurin-21 AS build
RUN apt-get update && apt-get upgrade -y
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
FROM eclipse-temurin:21
WORKDIR /app

# Копируем jar из предыдущего этапа
COPY --from=build /app/target/*.jar /app/bot.jar

# Устанавливаем значения по умолчанию для переменных
# (при необходимости переопределяются на этапе "docker run")
ENV BOT_USERNAME=1
ENV BOT_TOKEN=2
ENV MONGODB_URI=3

EXPOSE 10000
# Запускаем бот
ENTRYPOINT ["java", "-jar", "/app/bot.jar"]
