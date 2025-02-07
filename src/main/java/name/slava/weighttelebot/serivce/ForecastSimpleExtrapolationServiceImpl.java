package name.slava.weighttelebot.serivce;

import name.slava.weighttelebot.model.WeightEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;


import static java.time.temporal.ChronoUnit.DAYS;

/**
 * Simple forecast: calculate the average rate of change (kg/day)
 * and extrapolate to the target weight.
 */
@Service
public class ForecastSimpleExtrapolationServiceImpl implements ForecastService {
    private static final Logger logger = LoggerFactory.getLogger(ForecastSimpleExtrapolationServiceImpl.class);

    @Override
    public LocalDate calculateForecast(List<WeightEntry> weightEntries, double targetWeight) {
        logger.info("Calculating forecast for {} entries, target weight: {}", weightEntries.size(), targetWeight);

        if (weightEntries.size() < 2) {
            return null;
        }

        // Sort by date
        List<WeightEntry> sorted = weightEntries.stream()
                .sorted(Comparator.comparing(WeightEntry::getDate))
                .toList();

        WeightEntry first = sorted.getFirst();
        WeightEntry last = sorted.getLast();

        // Number of days between the first and last entry
        long daysBetween = DAYS.between(first.getDate(), last.getDate());
        if (daysBetween == 0){// all entries on the same day - no dynamics
            return null;
        }

        double weightDiff = last.getWeight() - first.getWeight();
        double dailyChange = weightDiff / daysBetween;

        // if dailyChange == 0, the forecast makes no sense (weight does not change)
        if (Math.abs(dailyChange) < 1e-9) {
            return null;
        }

        // How much weight needs to change from the last entry to the target
        double diffToTarget = targetWeight - last.getWeight();

        // Approximate number of days to reach the target
        double daysToTarget = diffToTarget / dailyChange;

        // Round to whole numbers, but can be more precise
        long daysRounded = Math.round(daysToTarget);

        // If the calculation shows that we will go back (for example, dailyChange is positive, but we need to lose weight),
        // then daysRounded may come out negative. You can add a check if needed.
        // But for now, leave it as is.

        // Forecast - from the last date:
        return last.getDate().plusDays(daysRounded);
    }
}