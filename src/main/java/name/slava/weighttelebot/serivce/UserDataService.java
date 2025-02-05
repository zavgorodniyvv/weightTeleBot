package name.slava.weighttelebot.serivce;

import name.slava.weighttelebot.model.UserData;

public interface UserDataService {
    UserData getOrDefault(long chatId, UserData userData);

    void put(long chatId, UserData data);

    UserData get(long chatId);


}
