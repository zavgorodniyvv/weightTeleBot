package name.slava.weighttelebot;

import jakarta.annotation.PostConstruct;
import name.slava.weighttelebot.model.UserData;
import name.slava.weighttelebot.model.WeightEntry;
import name.slava.weighttelebot.repository.UserDataRepository;
import name.slava.weighttelebot.serivce.UserDataService;
import name.slava.weighttelebot.serivce.UserDataServiceImpl;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MainBotClass extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(MainBotClass.class);

    // Замените на свои значения (или берите из переменных окружения)
    private static final String BOT_USERNAME = System.getenv("BOT_USERNAME");
    private static final String BOT_TOKEN = System.getenv("BOT_TOKEN");

    private final UserDataService userDataService;

    // Конструктор бота (можно оставить пустым, если всё нужное инициализируем статически)
    public MainBotClass(UserDataService userDataService) {
        super();
        this.userDataService = userDataService;
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    /**
     * Точка входа. Запускаем бота.
     */
    @PostConstruct
    public void init() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new MainBotClass(userDataService));
            logger.info("==============Bot started successfully!==============");
        } catch (TelegramApiException e) {
            logger.error("Error while starting bot {}", e.getMessage());
        }
    }

    /**
     * Главный метод, который вызывается при каждом обновлении (входящем сообщении) от Telegram.
     */
    @Override
    public void onUpdateReceived(Update update) {

        // Проверяем, что апдейт содержит сообщение
        if (update.hasMessage()) {
            Message message = update.getMessage();

            // Проверяем, что это текст
            if (message.hasText()) {
                // Текст сообщения
                String text = message.getText();
                // id чата (или пользователя)
                long chatId = message.getChatId();

                // Обрабатываем команды
                if (text.startsWith("/start")) {
                    handleStartCommand(chatId);
                } else if (text.startsWith("/setweight")) {
                    handleSetWeight(chatId, text);
                } else if (text.startsWith("/settarget")) {
                    handleSetTarget(chatId, text);
                } else if (text.startsWith("/showdata")) {
                    handleShowData(chatId);
                } else if (text.startsWith("/forecast")) {
                    handleForecast(chatId);
                } else if (text.startsWith("/chart")) {
                    handleChart(chatId);
                } else {
                    // Если нужно - обрабатываем произвольный текст
                    sendTextMessage(chatId, "Неизвестная команда. Попробуйте /start");
                }
            }
        }

        // Можно также проверять наличие CallbackQuery (кнопок), фото и т.д., если нужно.
    }

    /**
     * Отправка текстового сообщения пользователю.
     */
    private void sendTextMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error while sending message: {}", e.getMessage());
        }
    }

    // --------------------------------
    // Обработчики команд
    // --------------------------------

    private void handleStartCommand(long chatId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Привет! Я бот для отслеживания веса.\n");
        sb.append("Доступные команды:\n");
        sb.append("/setweight <число> - Добавить текущий вес.\n");
        sb.append("/settarget <число> - Установить желаемый вес.\n");
        sb.append("/showdata - Показать все введённые данные.\n");
        sb.append("/chart - Получить график.\n");
        sb.append("/forecast - Получить прогноз достижения цели.\n");

        sendTextMessage(chatId, sb.toString());
    }

    private void handleSetWeight(long chatId, String text) {
        logger.info("Setting weight for chatId: {}", chatId);
        // Формат: /setweight 70.5
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sendTextMessage(chatId, "Укажите вес. Пример: /setweight 75.3");
            return;
        }
        try {
            double weight = Double.parseDouble(parts[1]);

            // Получаем или создаём UserData
            UserData data = userDataService.getOrDefault(chatId, new UserData());
            data.getWeights().add(new WeightEntry(LocalDate.now(), weight));
            userDataService.put(chatId, data);

            sendTextMessage(chatId, "Вес " + weight + " кг сохранён.");
        } catch (NumberFormatException e) {
            sendTextMessage(chatId, "Некорректный формат числа. Пример: /setweight 75.3");
        }
    }

    private void handleSetTarget(long chatId, String text) {
        logger.info("Setting target weight for chatId: {}", chatId);
        // Формат: /settarget 65.0
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sendTextMessage(chatId, "Укажите целевой вес. Пример: /settarget 65.0");
            return;
        }
        try {
            double target = Double.parseDouble(parts[1]);
            UserData data = userDataService.getOrDefault(chatId, new UserData());
            data.setTargetWeight(target);
            userDataService.put(chatId, data);

            sendTextMessage(chatId, "Целевой вес " + target + " кг установлен.");
        } catch (NumberFormatException e) {
            sendTextMessage(chatId, "Некорректный формат числа. Пример: /settarget 65.0");
        }
    }

    private void handleShowData(long chatId) {
        logger.info("Showing data for chatId: {}", chatId);
        UserData data = userDataService.get(chatId);
        if (data == null || data.getWeights().isEmpty()) {
            sendTextMessage(chatId, "Данных о весе ещё нет.");
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        StringBuilder sb = new StringBuilder();
        sb.append("История веса:\n");
        for (WeightEntry entry : data.getWeights()) {
            sb.append(formatter.format(entry.getDate()))
                    .append(": ")
                    .append(entry.getWeight())
                    .append(" кг\n");
        }
        if (data.getTargetWeight() != null) {
            sb.append("\nЦелевой вес: ").append(data.getTargetWeight()).append(" кг\n");
        }
        sendTextMessage(chatId, sb.toString());
    }

    private void handleForecast(long chatId) {
        logger.info("Calculating forecast for chatId: {}", chatId);
        UserData data = userDataService.get(chatId);
        if (data == null || data.getWeights().size() < 2) {
            sendTextMessage(chatId, "Недостаточно данных для прогноза (нужно минимум 2 записи).");
            return;
        }
        if (data.getTargetWeight() == null) {
            sendTextMessage(chatId, "Сначала установите целевой вес: /settarget <число>.");
            return;
        }

        LocalDate forecastDate = calculateForecast(data.getWeights(), data.getTargetWeight());
        if (forecastDate == null) {
            sendTextMessage(chatId, "Невозможно вычислить прогноз, проверьте данные.");
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            sendTextMessage(chatId, "Прогнозируемая дата достижения цели: " + formatter.format(forecastDate));
        }
    }

    private void handleChart(long chatId) {
        logger.info("Generating chart for chatId: {}", chatId);

        UserData data = userDataService.get(chatId);
        if (data == null || data.getWeights().isEmpty()) {
            sendTextMessage(chatId, "Нет данных для построения графика.");
            return;
        }

        // Генерируем картинку
        File chartFile = generateChart(data.getWeights());
        if (chartFile == null) {
            sendTextMessage(chatId, "Ошибка при генерации графика.");
            return;
        }

        // Отправляем фото
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(String.valueOf(chatId));
        sendPhoto.setPhoto(new InputFile(chartFile));
        sendPhoto.setCaption("График веса");

        try {
            execute(sendPhoto);
        } catch (TelegramApiException e) {
            logger.error("Error while sending chart: {}", e.getMessage());
            sendTextMessage(chatId, "Ошибка при отправке графика.");
        } finally {
            // Удалим временный файл (чтобы не копились)
            if (chartFile.exists()) {
                chartFile.delete();
            }
        }
    }

    // --------------------------------
    // Логика прогноза
    // --------------------------------

    /**
     * Простейший прогноз: считаем среднюю скорость изменения (кг/день)
     * и экстраполируем до целевого веса.
     */
    private LocalDate calculateForecast(List<WeightEntry> weightEntries, double targetWeight) {
        logger.info("Calculating forecast for {} entries, target weight: {}", weightEntries.size(), targetWeight);

        if (weightEntries.size() < 2) {
            return null;
        }

        // Сортируем по дате
        List<WeightEntry> sorted = weightEntries.stream()
                .sorted(Comparator.comparing(WeightEntry::getDate))
                .collect(Collectors.toList());

        WeightEntry first = sorted.get(0);
        WeightEntry last = sorted.get(sorted.size() - 1);

        // Кол-во дней между первой и последней записью
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(first.getDate(), last.getDate());
        if (daysBetween == 0) return null; // все записи в один день - нет динамики

        double weightDiff = last.getWeight() - first.getWeight();
        double dailyChange = weightDiff / daysBetween;

        // если dailyChange == 0, прогноз не имеет смысла (вес не меняется)
        if (Math.abs(dailyChange) < 1e-9) {
            return null;
        }

        // Насколько нужно изменить вес от последней записи до цели
        double diffToTarget = targetWeight - last.getWeight();

        // Примерное кол-во дней, чтобы дойти до цели
        double daysToTarget = diffToTarget / dailyChange;

        // Округлим до целых, но можно и более точно
        long daysRounded = Math.round(daysToTarget);

        // Если при подсчёте получилось, что идти будем назад (например, dailyChange положительный, а нам надо похудеть),
        // то daysRounded может выйти отрицательным. Можно добавить проверку, если нужно.
        // Но пока оставим как есть.

        // Прогноз - от последней даты:
        LocalDate forecastDate = last.getDate().plusDays(daysRounded);
        return forecastDate;
    }

    // --------------------------------
    // Генерация графика (через XChart)
    // --------------------------------

    /**
     * Генерирует PNG-файл с графиком и возвращает путь к нему.
     * В реальном проекте стоит аккуратнее работать с временными файлами (директория, имена и т.д.).
     */
    private File generateChart(List<WeightEntry> weightEntries) {
        logger.info("Generating chart for {} entries", weightEntries.size());
        try {
            // Сортируем по дате
            List<WeightEntry> sorted = weightEntries.stream()
                    .sorted(Comparator.comparing(WeightEntry::getDate))
                    .collect(Collectors.toList());

            // Для xData используем число дней (toEpochDay())
            List<Double> xData = sorted.stream()
                    .map(entry -> (double) entry.getDate().toEpochDay())
                    .collect(Collectors.toList());

            // Для yData - сам вес
            List<Double> yData = sorted.stream()
                    .map(WeightEntry::getWeight)
                    .collect(Collectors.toList());

            XYChart chart = new XYChartBuilder()
                    .width(800)
                    .height(600)
                    .title("Динамика веса")
                    .xAxisTitle("Дата (EpochDay)")
                    .yAxisTitle("Вес (кг)")
                    .build();

            // Добавляем серию
            chart.addSeries("Вес", xData, yData).setMarker(SeriesMarkers.CIRCLE);

            // Сохраняем во временный файл
            File file = File.createTempFile("weight_chart_", ".png");
            BitmapEncoder.saveBitmap(chart, file.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);

            return file;
        } catch (IOException e) {
            logger.error("Error while generating chart: {}", e.getMessage());
            return null;
        }
    }

}

