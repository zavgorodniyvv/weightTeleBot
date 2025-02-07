package name.slava.weighttelebot.serivce;

import name.slava.weighttelebot.MainBotClass;
import name.slava.weighttelebot.model.WeightEntry;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

// --------------------------------
// Chart generation (using XChart)
// --------------------------------

/**
 * Generates a PNG file with the chart and returns its path.
 */
@Service
public class ChartGeneratorXChartImpl implements ChartGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ChartGeneratorXChartImpl.class);

    @Override
    public File generateChart(List<WeightEntry> weightEntries) {
        logger.info("Generating chart for {} entries", weightEntries.size());
        try {
            // Sort by date
            List<WeightEntry> sorted = weightEntries.stream()
                    .sorted(Comparator.comparing(WeightEntry::getDate))
                    .collect(Collectors.toList());

            // For xData, use the number of days (toEpochDay())
            List<Double> xData = sorted.stream()
                    .map(entry -> (double) entry.getDate().toEpochDay())
                    .collect(Collectors.toList());

            // For yData - the weight itself
            List<Double> yData = sorted.stream()
                    .map(WeightEntry::getWeight)
                    .collect(Collectors.toList());

            XYChart chart = new XYChartBuilder()
                    .width(800)
                    .height(600)
                    .title("Weight Dynamics")
                    .xAxisTitle("Date (EpochDay)")
                    .yAxisTitle("Weight (kg)")
                    .build();

            // Add series
            chart.addSeries("Weight", xData, yData).setMarker(SeriesMarkers.CIRCLE);

            // Save to a temporary file
            File file = File.createTempFile("weight_chart_", ".png");
            BitmapEncoder.saveBitmap(chart, file.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);

            return file;
        } catch (IOException e) {
            logger.error("Error while generating chart: {}", e.getMessage());
            return null;
        }
    }
}