package name.slava.weighttelebot.serivce;

import name.slava.weighttelebot.model.WeightEntry;

import java.time.LocalDate;
import java.util.List;

public interface ForecastService {
    LocalDate calculateForecast(List<WeightEntry> weightEntries, double targetWeight);
}
