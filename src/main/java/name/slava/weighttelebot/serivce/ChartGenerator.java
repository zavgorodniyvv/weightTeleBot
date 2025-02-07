package name.slava.weighttelebot.serivce;

import name.slava.weighttelebot.model.WeightEntry;

import java.io.File;
import java.util.List;

public interface ChartGenerator {
    File generateChart(List<WeightEntry> weightEntries);
}
