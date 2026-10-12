# ========================
# 1-й этап: Сборка кода
# ========================
FROM maven:3.9.11-eclipse-temurin-25 AS build
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
FROM eclipse-temurin:25-jre
WORKDIR /app

# Копируем jar из предыдущего этапа
COPY --from=build /app/target/*.jar /app/bot.jar

# Переменные BOT_USERNAME, BOT_TOKEN, MONGODB_URI задаются на этапе "docker run".
# Значений по умолчанию нет намеренно: без них бот должен падать явно.

# Настройки JVM для экономии памяти:
#  - UseCompactObjectHeaders: заголовки объектов 8 байт вместо 12 (JDK 25), меньше heap
#  - SerialGC: самый экономный сборщик мусора для одного маленького процесса
#  - Xss512k: стеки потоков вдвое меньше стандартных
#  - TieredStopAtLevel=1 + ReservedCodeCacheSize: только быстрый JIT C1, меньше кэш кода и потоков компилятора
#  - ExitOnOutOfMemoryError: при нехватке памяти процесс завершается, а не зависает (перезапуск — на стороне хостинга)
ENV JAVA_TOOL_OPTIONS="-XX:+UseCompactObjectHeaders -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=32m -XX:+ExitOnOutOfMemoryError"

# Запускаем бот
ENTRYPOINT ["java", "-jar", "/app/bot.jar"]
