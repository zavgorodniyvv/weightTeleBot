package name.slava.weighttelebot.repository;

import name.slava.weighttelebot.model.UserData;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserDataRepository extends MongoRepository<UserData, String> {

    UserData findByChatId(long chatId);
}
