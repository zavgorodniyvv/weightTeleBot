package name.slava.weighttelebot.model;

import name.slava.weighttelebot.MainBotClass;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Document
public class UserData {
    @Id
    private String id;
    private Long chatId;
    private Double targetWeight;
    private List<WeightEntry> weights;

    public UserData() {
        this.weights = new ArrayList<>();
    }

    public Double getTargetWeight() {
        return targetWeight;
    }

    public void setTargetWeight(Double targetWeight) {
        this.targetWeight = targetWeight;
    }

    public List<WeightEntry> getWeights() {
        return weights;
    }

    public void setWeights(List<WeightEntry> weights) {
        this.weights = weights;
    }

    public String getId() {
        return id;
    }


    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        UserData userData = (UserData) o;
        return Objects.equals(id, userData.id) && Objects.equals(chatId, userData.chatId) && Objects.equals(targetWeight, userData.targetWeight) && Objects.equals(weights, userData.weights);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, chatId, targetWeight, weights);
    }

    @Override
    public String toString() {
        return "UserData{" +
                "id='" + id + '\'' +
                ", chatId=" + chatId +
                ", targetWeight=" + targetWeight +
                ", weights=" + weights +
                '}';
    }
}
