package name.slava.weighttelebot;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import name.slava.weighttelebot.enums.BotState;
import name.slava.weighttelebot.enums.ChartPeriod;
import name.slava.weighttelebot.model.UserData;
import name.slava.weighttelebot.model.WeightEntry;
import name.slava.weighttelebot.serivce.ChartGenerator;
import name.slava.weighttelebot.serivce.ForecastService;
import name.slava.weighttelebot.serivce.UserDataService;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.longpolling.util.TelegramOkHttpClientFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MainBotClass implements LongPollingSingleThreadUpdateConsumer {
    private static final Logger logger = LoggerFactory.getLogger(MainBotClass.class);

    private static final String BOT_TOKEN = System.getenv("BOT_TOKEN");

    private static final String BUTTON_SET_WEIGHT = "Set weight";
    private static final String BUTTON_SHOW_DATA = "Show data";
    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final double MAX_WEIGHT = 1000;

    private final Map<Long, BotState> userStates = new ConcurrentHashMap<>();
    private final Map<Long, String> userDates = new ConcurrentHashMap<>();

    private final UserDataService userDataService;
    private final ForecastService forecastService;
    private final ChartGenerator chartGenerator;

    // Один HTTP-клиент на получение апдейтов и на отправку сообщений: меньше потоков и соединений
    private final OkHttpClient httpClient = new TelegramOkHttpClientFactory.DefaultOkHttpClientCreator().get();
    private final TelegramClient telegramClient = new OkHttpTelegramClient(httpClient, BOT_TOKEN);
    private final TelegramBotsLongPollingApplication botsApplication =
            new TelegramBotsLongPollingApplication(ObjectMapper::new, () -> httpClient);

    public MainBotClass(UserDataService userDataService, ForecastService forecastService, ChartGenerator chartGenerator) {
        this.userDataService = userDataService;
        this.forecastService = forecastService;
        this.chartGenerator = chartGenerator;
    }

    /**
     * Точка входа. Запускаем бота.
     */
    @PostConstruct
    public void init() {
        try {
            // Сессия long polling сама удаляет webhook перед началом получения апдейтов
            botsApplication.registerBot(BOT_TOKEN, this);
            logger.info("==============Bot started successfully!==============");
        } catch (TelegramApiException e) {
            logger.error("Error while starting bot", e);
        }
        List<BotCommand> commands = List.of(
                new BotCommand("/start", "Начать работу"),
                new BotCommand("/settarget", "Установить целевой вес"),
                new BotCommand("/showdata", "Показать данные"),
                new BotCommand("/editdata", "Редактировать данные"),
                new BotCommand("/forecast", "Получить прогноз"),
                new BotCommand("/chart", "Получить график")
        );
        try{
            telegramClient.execute(new SetMyCommands(commands));
            logger.info("Commands set successfully");
        } catch (TelegramApiException e) {
            logger.error("Error while setting commands: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            botsApplication.close();
        } catch (Exception e) {
            logger.error("Error while stopping bot: {}", e.getMessage());
        }
    }

    /**
     * Главный метод, который вызывается при каждом обновлении (входящем сообщении) от Telegram.
     */
    @Override
    public void consume(Update update) {

        // Проверяем, что апдейт содержит сообщение
        if (update.hasMessage()) {
            Message message = update.getMessage();
            // id чата (или пользователя)
            long chatId = message.getChatId();
            String text = message.hasText() ? message.getText().trim() : null;

            // Команды и кнопки клавиатуры работают всегда и сбрасывают ожидание ввода,
            // чтобы пользователь не застревал в режиме ввода веса
            if (text != null && isCommand(text)) {
                userStates.put(chatId, BotState.IDLE);
                handleCommand(chatId, text);
                return;
            }

            var userState = userStates.getOrDefault(chatId, BotState.IDLE);
            if (userState != BotState.IDLE && text == null) {
                sendTextMessage(chatId, "Ожидается число. Пример: 75.3");
                return;
            }

            switch (userState) {
                case WAITING_WEIGHT -> handleSetWeightOnButtonClick(chatId, text);
                case WAITING_TARGET_WEIGHT -> setTargetWeight(chatId, text);
                case WAITING_NEW_WEIGHT -> setNewWeight(chatId, text);
                case IDLE -> {
                    if (text != null) {
                        sendTextMessage(chatId, "Неизвестная команда. Попробуйте /start");
                    }
                }
            }
        }

        if (update.hasCallbackQuery()) {
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            String data = update.getCallbackQuery().getData();
            answerCallbackQuery(update.getCallbackQuery().getId());
            if (data.startsWith("chart::")) {
                handleChartPeriod(chatId, data.split("::")[1]);
            } else if (data.startsWith("edit:")) {
                String[] parts = data.split("::");
                String date = parts[2];
                userDates.put(chatId, data);
                userStates.put(chatId, BotState.WAITING_NEW_WEIGHT);
                sendTextMessage(chatId, "Введите новый вес для даты " + date + ":");
            }
        }

        // Можно также проверять наличие CallbackQuery (кнопок), фото и т.д., если нужно.
    }

    private boolean isCommand(String text) {
        return text.startsWith("/") || text.equals(BUTTON_SET_WEIGHT) || text.equals(BUTTON_SHOW_DATA);
    }

    private void handleCommand(long chatId, String text) {
        if (text.startsWith("/start")) {
            handleStartCommand(chatId);
        } else if (text.startsWith("/setweight")) {
            handleSetWeight(chatId, text);
        } else if (text.startsWith("/settarget")) {
            handleSetTarget(chatId, text);
        } else if (text.startsWith("/showdata") || text.equals(BUTTON_SHOW_DATA)) {
            handleShowData(chatId);
        } else if (text.startsWith("/editdata")) {
            handleEditData(chatId);
        } else if (text.startsWith("/forecast")) {
            handleForecast(chatId);
        } else if (text.startsWith("/chart")) {
            handleChart(chatId);
        } else if (text.equals(BUTTON_SET_WEIGHT)) {
            userStates.put(chatId, BotState.WAITING_WEIGHT);
            sendTextMessage(chatId, "Введите ваш вес:");
        } else {
            sendTextMessage(chatId, "Неизвестная команда. Попробуйте /start");
        }
    }

    /**
     * Разбирает вес из текста пользователя. Принимает и точку, и запятую: "75.3", "75,3".
     */
    private static double parseWeight(String text) {
        double weight = Double.parseDouble(text.trim().replace(',', '.'));
        if (!Double.isFinite(weight) || weight <= 0 || weight >= MAX_WEIGHT) {
            throw new NumberFormatException("Invalid weight: " + text);
        }
        return weight;
    }

    private void setNewWeight(long chatId, String text) {
        try {
            double weight = parseWeight(text);
            UserData userData = userDataService.get(chatId);
            if(userData == null){
                sendTextMessage(chatId, "Нет данных для редактирования.");
                return;
            }
            String data = userDates.get(chatId);
            if (data == null) {
                sendTextMessage(chatId, "Запись для редактирования не выбрана. Попробуйте /editdata");
                return;
            }
            var dataParts = data.split("::");

            String localDate = dataParts[2];
            String id = dataParts[1];

            List<WeightEntry> weights = userData.getWeights();
            for (WeightEntry entry : weights) {
                String entryId = entry.getId();
                if (entryId.equals(id)) {
                    entry.setWeight(weight);
                    sendTextMessage(chatId, "Вес для даты " + localDate + " изменён на " + weight + " кг.");
                    break;
                }
            }
            userDataService.put(chatId, userData);
        } catch (NumberFormatException e) {
            sendTextMessage(chatId, "Некорректный формат числа. Пример: 75.3");
        }
        finally {
            userStates.put(chatId, BotState.IDLE);
        }
    }

    private void handleEditData(long chatId) {
        UserData userData = userDataService.get(chatId);
        if(userData == null){
            sendTextMessage(chatId, "Нет данных для редактирования.");
            return;
        }
        List<WeightEntry> weights = userData.getWeights();
        int lastN = 10;
        List<WeightEntry> lastWeightEntries = weights.subList(
                Math.max(0, weights.size() - lastN),
                weights.size()
        );

        if(lastWeightEntries.isEmpty()){
            sendTextMessage(chatId, "Нет данных для редактирования.");
            return;
        }
        List<InlineKeyboardRow> rows = new ArrayList<>();
        lastWeightEntries.forEach(entry -> rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                .text(entry.getDate() + " -> " + entry.getWeight())
                .callbackData("edit::" + entry.getId() + "::" + entry.getDate() + "::" + entry.getWeight())
                .build())));

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup(rows);

        sendTextMessage(chatId, "Выберите запись для редактирования:", inlineKeyboardMarkup);

    }

    private void handleSetWeightOnButtonClick(Long chatId, String text) {
        try{
            double weight = parseWeight(text);

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
        sendTextMessage(chatId, text, getReplyButtons(chatId));
    }

    /**
     * Отправка длинного текста несколькими сообщениями: Telegram ограничивает сообщение 4096 символами.
     * Текст режется по переводам строк.
     */
    private void sendLongTextMessage(long chatId, String text) {
        int start = 0;
        while (text.length() - start > MAX_MESSAGE_LENGTH) {
            int end = text.lastIndexOf('\n', start + MAX_MESSAGE_LENGTH - 1);
            if (end <= start) {
                end = start + MAX_MESSAGE_LENGTH - 1;
            }
            sendTextMessage(chatId, text.substring(start, end + 1));
            start = end + 1;
        }
        if (!text.substring(start).isBlank()) {
            sendTextMessage(chatId, text.substring(start));
        }
    }

    private void sendTextMessage(long chatId, String text, ReplyKeyboard replyKeyboard) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(replyKeyboard)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error while sending message: {}", e.getMessage());
        }
    }

    private ReplyKeyboard getReplyButtons(long chatId) {
        // Создаём кнопки
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(BUTTON_SET_WEIGHT));
        row1.add(new KeyboardButton(BUTTON_SHOW_DATA));

        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(row1))
                .resizeKeyboard(true) // Клавиатура будет подстраиваться по высоте
                .selective(true) // Отображать клавиатуру только нужным пользователям (в группах)
                .build();
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
        sb.append("/editdata - Редактировать данные.\n");
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
            double weight = parseWeight(parts[1]);

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
            userStates.put(chatId, BotState.WAITING_TARGET_WEIGHT);
            return;
        }

            setTargetWeight(chatId, parts[1]);

    }

    private void setTargetWeight(long chatId, String weight) {
        try {
            double target = parseWeight(weight);
            UserData data = userDataService.getOrDefault(chatId, new UserData());
            data.setTargetWeight(target);
            userDataService.put(chatId, data);
            sendTextMessage(chatId, "Целевой вес " + target + " кг установлен.");
        } catch (NumberFormatException e) {
            sendTextMessage(chatId, "Некорректный формат числа. Пример: /settarget 65.0");
        }
        finally {
            userStates.put(chatId, BotState.IDLE);
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
        sendLongTextMessage(chatId, sb.toString());
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
            sendTextMessage(chatId, "Невозможно вычислить прогноз: вес не меняется или движется в сторону от цели.");
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            sendTextMessage(chatId, "Прогнозируемая дата достижения цели: " + formatter.format(forecastDate));
        }
    }

    private void handleChart(long chatId) {
        sendTextMessage(chatId, "Выберите период для графика:", getChartPeriodButtons());
    }

    private InlineKeyboardMarkup getChartPeriodButtons() {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        InlineKeyboardRow row2 = new InlineKeyboardRow();
        for (ChartPeriod period : ChartPeriod.values()) {
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(period.getLabel())
                    .callbackData("chart::" + period.name())
                    .build();
            (row1.size() < 2 ? row1 : row2).add(button);
        }

        return new InlineKeyboardMarkup(List.of(row1, row2));
    }

    private void handleChartPeriod(long chatId, String periodName) {
        ChartPeriod period;
        try {
            period = ChartPeriod.valueOf(periodName);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown chart period: {}", periodName);
            return;
        }
        logger.info("Generating chart for chatId: {}, period: {}", chatId, period);

        UserData data = userDataService.get(chatId);
        if (data == null || data.getWeights().isEmpty()) {
            sendTextMessage(chatId, "Нет данных для построения графика.");
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate from = period.startDate(today);
        List<WeightEntry> entries = data.getWeights().stream()
                .filter(entry -> from == null || !entry.getDate().isBefore(from))
                .toList();
        if (entries.isEmpty()) {
            sendTextMessage(chatId, "Нет данных за период \"" + period.getLabel() + "\".", getChartPeriodButtons());
            return;
        }

        // Генерируем картинку
        byte[] chart = generateChart(entries, from, from == null ? null : today);
        if (chart == null) {
            sendTextMessage(chatId, "Ошибка при генерации графика.");
            return;
        }

        // Отправляем фото
        SendPhoto sendPhoto = SendPhoto.builder()
                .chatId(chatId)
                .photo(new InputFile(new ByteArrayInputStream(chart), "weight_chart.png"))
                .caption("График веса: " + period.getLabel())
                .replyMarkup(getChartPeriodButtons())
                .build();

        try {
            telegramClient.execute(sendPhoto);
        } catch (TelegramApiException e) {
            logger.error("Error while sending chart: {}", e.getMessage());
            sendTextMessage(chatId, "Ошибка при отправке графика.");
        }
    }

    private void answerCallbackQuery(String callbackQueryId) {
        try {
            telegramClient.execute(new AnswerCallbackQuery(callbackQueryId));
        } catch (TelegramApiException e) {
            logger.error("Error while answering callback query: {}", e.getMessage());
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
     * Генерирует PNG-картинку с графиком.
     */
    private byte[] generateChart(List<WeightEntry> weightEntries, LocalDate from, LocalDate to) {
        return chartGenerator.generateChart(weightEntries, from, to);
    }

}

