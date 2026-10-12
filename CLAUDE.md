# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Telegram bot for weight tracking (Spring Boot 3.4, Java 21, Maven, MongoDB). Users log weight entries, set a target weight, view history, edit recent entries, get a PNG chart (XChart) and a forecast date for reaching the target. All user-facing bot text is in Russian.

## Commands

```bash
mvn clean package -DskipTests           # build jar (target/*.jar)
mvn test                                # run tests
mvn test -Dtest=WeightTeleBotApplicationTests              # single test class
mvn test -Dtest=WeightTeleBotApplicationTests#contextLoads   # single test method
mvn spring-boot:run                     # run locally
docker build -t weighttelebot . && docker run -e BOT_USERNAME=... -e BOT_TOKEN=... -e MONGODB_URI=... -p 10000:10000 weighttelebot
```

`mvnw` is committed but `.mvn/wrapper/` is not, so the wrapper does not work — use a system `mvn`.

Required environment variables: `BOT_USERNAME`, `BOT_TOKEN` (read directly via `System.getenv` in `MainBotClass`, not via Spring properties) and `MONGODB_URI` (resolved through `spring.data.mongodb.uri=${MONGODB_URI}`). The only test is a `@SpringBootTest` context-load test, so it needs `MONGODB_URI` set and will also try to register the bot with Telegram on startup.

## Deployment

There is no CI/CD workflow. The app is shipped as the multi-stage `Dockerfile` (runtime image is `eclipse-temurin:21-jre`, memory-saving JVM flags are set via `JAVA_TOOL_OPTIONS`). There is no `spring-boot-starter-web`/Tomcat (removed to save RAM) — `HealthServer` runs the JDK built-in `HttpServer` on `server.port` (10000) and answers `200 OK` on any path, for hosting health checks. Since the app is not a web app, `spring.main.keep-alive=true` keeps the JVM running.

## Architecture

- `MainBotClass` (`TelegramLongPollingBot`, annotated `@Service`) holds essentially all bot logic: command dispatch in `onUpdateReceived`, handlers, keyboards, and message sending. In `@PostConstruct init()` it registers itself (`registerBot(this)`) with `TelegramBotsApi` and sets the bot command menu via `SetMyCommands`. When adding a command, update the `if/else` dispatch in `handleCommand`, the `SetMyCommands` list, and the `/start` help text.
- Conversation state is kept in-memory per chat (`Map<Long, BotState> userStates`, `Map<Long, String> userDates`) and is lost on restart. States in `enums/BotState` (`WAITING_WEIGHT`, `WAITING_TARGET_WEIGHT`, `WAITING_NEW_WEIGHT`) make the next non-command message be treated as the input value. Commands (`/...`) and reply-keyboard button texts are checked **first** and reset the state to `IDLE`, so a user can't get stuck. Weight input goes through `parseWeight` (accepts `,` or `.`, rejects non-finite/≤0/≥1000).
- Reply-keyboard buttons ("Set weight", "Show data") are dispatched by matching message text. Inline buttons use `::`-separated callback data: `edit::<entryId>::<date>::<weight>` (parsed in both the callback handler and `setNewWeight`) and `chart::<ChartPeriod name>`.
- Services live in package `serivce` (misspelled — keep it consistent): interfaces `UserDataService`, `ForecastService`, `ChartGenerator` with single implementations (`UserDataServiceImpl`, `ForecastSimpleExtrapolationServiceImpl` — linear extrapolation between first and last entry, `ChartGeneratorXChartImpl` — renders the PNG in memory and returns `byte[]`). The chart X axis is numeric epoch-day so spacing is proportional to time; period filtering (`enums/ChartPeriod`) happens in `MainBotClass` before calling the generator.
- Persistence: one Mongo document `UserData` per chat (looked up by `chatId` via `UserDataRepository.findByChatId`) with an embedded `List<WeightEntry>`. `WeightEntry.id` is generated from `System.nanoTime()` in its constructor and is used to identify entries for editing. Updates are read-modify-write of the whole `UserData` document. Long texts (e.g. `/showdata`) go through `sendLongTextMessage`, which splits them at line breaks to stay under Telegram's 4096-char limit.
