package name.slava.weighttelebot;

import jakarta.annotation.PostConstruct;
import name.slava.weighttelebot.enums.BotState;
import name.slava.weighttelebot.model.UserData;
import name.slava.weighttelebot.model.WeightEntry;
import name.slava.weighttelebot.serivce.ChartGenerator;
import name.slava.weighttelebot.serivce.ForecastService;
import name.slava.weighttelebot.serivce.UserDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MainBotClass extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(MainBotClass.class);

    // Замените на свои значения (или берите из переменных окружения)
    private static final String BOT_USERNAME = System.getenv("BOT_USERNAME");
    private static final String BOT_TOKEN = System.getenv("BOT_TOKEN");

    private final Map<Long, BotState> userStates = new HashMap<>();

    private final UserDataService userDataService;
    private final ForecastService forecastService;
    private final ChartGenerator chartGenerator;

    // Конструктор бота (можно оставить пустым, если всё нужное инициализируем статически)
    public MainBotClass(UserDataService userDataService, ForecastService forecastService, ChartGenerator chartGenerator) {
        super();
        this.userDataService = userDataService;
        this.forecastService = forecastService;
        this.chartGenerator = chartGenerator;
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
            botsApi.registerBot(new MainBotClass(userDataService, forecastService, chartGenerator));
            logger.info("==============Bot started successfully!==============");
        } catch (TelegramApiException e) {
            logger.error("Error while starting bot {}", e.getMessage());
        }
        List<BotCommand> commands = List.of(
                new BotCommand("/start", "Начать работу"),
                new BotCommand("/setweight", "Установить текущий вес"),
                new BotCommand("/settarget", "Установить целевой вес"),
                new BotCommand("/showdata", "Показать данные"),
                new BotCommand("/forecast", "Получить прогноз"),
                new BotCommand("/chart", "Получить график")
        );
        SetMyCommands setMyCommands = new SetMyCommands();
        setMyCommands.setCommands(commands);
        try{
            execute(setMyCommands);
            logger.info("Commands set successfully");
        } catch (TelegramApiException e) {
            logger.error("Error while setting commands: {}", e.getMessage());
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

            var userState = userStates.getOrDefault(message.getChatId(), BotState.IDLE);

            if(userState == BotState.WAITING_WEIGHT){
                handleSetWeightOnButtonClick(message.getChatId(), message.getText());
                return;
            }

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
                } else if (text.startsWith("Show data")) {
                    handleShowData(chatId);
                } else if (text.startsWith("Set weight")) {
                    userStates.put(chatId, BotState.WAITING_WEIGHT);
                    sendTextMessage(chatId, "Введите ваш вес:");
                } else {
                    // Если нужно - обрабатываем произвольный текст
                    sendTextMessage(chatId, "Неизвестная команда. Попробуйте /start");
                }
            }
        }

        // Можно также проверять наличие CallbackQuery (кнопок), фото и т.д., если нужно.
    }

    private void handleSetWeightOnButtonClick(Long chatId, String text) {
        try{
            double weight = Double.parseDouble(text);

            UserData data = userDataService.getOrDefault(chatId, new UserData());
            data.getWeights().add(new WeightEntry(LocalDate.now(), weight));
            userDataService.put(chatId, data);

            sendTextMessage(chatId, "Вес " + weight + " кг сохранён.");

            userStates.put(chatId, BotState.IDLE);
        } catch (NumberFormatException e){
            sendTextMessage(chatId, "Некорректный формат числа. Пример: 75.3");
        }

    }

    /**
     * Отправка текстового сообщения пользователю.
     */
    private void sendTextMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(getReplyButtons(chatId));
//        message.setReplyMarkup(getInlineButtons(chatId));
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error while sending message: {}", e.getMessage());
        }
    }

    private ReplyKeyboard getInlineButtons(long chatId) {
        // Создаём кнопку
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("Нажми меня!");
        button.setCallbackData("BUTTON_HELLO_CALLBACK");

        // Один ряд кнопок
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button);

        // Список рядов (каждый ряд – список кнопок)
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row);

        // Создаём объект разметки
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        inlineKeyboardMarkup.setKeyboard(rows);
        return inlineKeyboardMarkup;
    }


    private ReplyKeyboard getReplyButtons(long chatId) {
        // Создаём объект клавиатуры
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true); // Клавиатура будет подстраиваться по высоте
        keyboardMarkup.setSelective(true); // Отображать клавиатуру только нужным пользователям (в группах)

        // Создаём кнопки
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Set weight"));
        row1.add(new KeyboardButton("Show data"));

        // Собираем список строк
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        keyboardRows.add(row1);

        // Устанавливаем клавиатуру
        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
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
    private LocalDate calculateForecast(List<WeightEntry> weightEntries, double targetWeight) {
        return forecastService.calculateForecast(weightEntries, targetWeight);
    }


    // --------------------------------
    // Генерация графика (через XChart)
    // --------------------------------
    /**
     * Генерирует PNG-файл с графиком и возвращает путь к нему.
     */
    private File generateChart(List<WeightEntry> weightEntries) {
        return chartGenerator.generateChart(weightEntries);
    }

}

