package name.slava.weighttelebot.serivce;

import name.slava.weighttelebot.model.WeightEntry;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

public interface ChartGenerator {
    /**
     * @param from начало оси X или null, чтобы диапазон определялся по данным
     * @param to   конец оси X или null, чтобы диапазон определялся по данным
     */
    File generateChart(List<WeightEntry> weightEntries, LocalDate from, LocalDate to);
}
