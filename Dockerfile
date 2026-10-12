# ========================
# 1-й этап: Сборка кода
# ========================
FROM maven:3.9.9-eclipse-temurin-21 AS build
# Создадим директорию для проекта
WORKDIR /app

# Скопируем файл pom.xml и скачаем зависимости отдельно (для оптимизации кэширования)
COPY pom.xml .
RUN mvn dependency:go-offline

# Копируем остальной исходный код
COPY src ./src

# Собираем проект (исключая тесты, если не нужны)
RUN mvn clean package -DskipTests

# ========================
# 2-й этап: Финальный образ
# ========================
# JRE вместо полного JDK: образ меньше, компилятор и прочие инструменты JDK не нужны
FROM eclipse-temurin:21-jre
WORKDIR /app

# Копируем jar из предыдущего этапа
COPY --from=build /app/target/*.jar /app/bot.jar

# Переменные BOT_USERNAME, BOT_TOKEN, MONGODB_URI задаются на этапе "docker run" / в Render.
# Значений по умолчанию нет намеренно: без них бот должен падать явно.

# Настройки JVM для экономии памяти:
#  - SerialGC: самый экономный сборщик мусора для одного маленького процесса
#  - Xss512k: стеки потоков вдвое меньше стандартных
#  - TieredStopAtLevel=1 + ReservedCodeCacheSize: только быстрый JIT C1, меньше кэш кода и потоков компилятора
#  - ExitOnOutOfMemoryError: при нехватке памяти процесс завершается, и Render его перезапускает
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=32m -XX:+ExitOnOutOfMemoryError"

EXPOSE 10000
# Запускаем бот
ENTRYPOINT ["java", "-jar", "/app/bot.jar"]
