package name.slava.weighttelebot.serivce;

    import name.slava.weighttelebot.model.UserData;
    import name.slava.weighttelebot.repository.UserDataRepository;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.stereotype.Service;

    @Service
    public class UserDataServiceImpl implements UserDataService {

        private static final Logger logger = LoggerFactory.getLogger(UserDataServiceImpl.class);

        private final UserDataRepository userDataRepository;

        public UserDataServiceImpl(UserDataRepository userDataRepository) {
            this.userDataRepository = userDataRepository;
        }

        @Override
        public UserData getOrDefault(long chatId, UserData userData) {
            logger.info("Fetching user data for chatId: {}", chatId);
            UserData usedDataFromDb = userDataRepository.findByChatId(chatId);
            if (usedDataFromDb == null) {
                logger.info("No user data found for chatId: {}, creating new UserData", chatId);
                usedDataFromDb = new UserData();
                usedDataFromDb.setChatId(chatId);
            }
            return usedDataFromDb;
        }

        @Override
        public void put(long chatId, UserData data) {
            logger.info("Saving user data for chatId: {}", chatId);
            userDataRepository.save(data);
        }

        @Override
        public UserData get(long chatId) {
            logger.info("Fetching user data for chatId: {}", chatId);
            return userDataRepository.findByChatId(chatId);
        }
    }