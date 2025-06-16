package name.slava.weighttelebot.serivce;

import name.slava.weighttelebot.model.WeightEntry;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.internal.chartpart.Axis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Генерирует PNG-файл с графиком и возвращает его путь.
 */
@Service
public class ChartGeneratorXChartImpl implements ChartGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ChartGeneratorXChartImpl.class);

    @Override
    public File generateChart(List<WeightEntry> weightEntries) {
        logger.info("Generating chart for {} entries", weightEntries.size());
        try {
            // Сортируем по дате
            List<WeightEntry> sorted = weightEntries.stream()
                    .sorted(Comparator.comparing(WeightEntry::getDate))
                    .collect(Collectors.toList());

            // xData — индексы (позиция точки), yData — значения веса
            List<Double> xData = new java.util.ArrayList<>();
            List<Double> yData = new java.util.ArrayList<>();
            List<String> xLabels = new java.util.ArrayList<>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yy");

            for (int i = 0; i < sorted.size(); i++) {
                WeightEntry entry = sorted.get(i);
                xData.add((double)i);
                yData.add(entry.getWeight());
                xLabels.add(entry.getDate().format(formatter));
            }

            XYChart chart = new XYChartBuilder()
                    .width(800)
                    .height(600)
                    .title("Weight Dynamics")
                    .xAxisTitle("Date")
                    .yAxisTitle("Weight (kg)")
                    .build();

            chart.getStyler().setAxisTitleFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 16));
            chart.getStyler().setAxisTickLabelsFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 12));
            chart.getStyler().setXAxisLabelRotation(45); // Повернуть подписи дат для читаемости
            chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);

            // Добавляем серию
            chart.addSeries("Weight", xData, yData).setMarker(SeriesMarkers.CIRCLE);

            // Устанавливаем кастомные подписи оси X для дат
            chart.setCustomXAxisTickLabelsFormatter(x -> {
                int idx = x.intValue();
                if (idx >= 0 && idx < xLabels.size()) {
                    return xLabels.get(idx);
                } else {
                    return "";
                }
            });

            // Сохраняем во временный файл
            File file = File.createTempFile("weight_chart_", ".png");
            BitmapEncoder.saveBitmap(chart, file.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);

            return file;
        } catch (IOException e) {
            logger.error("Error while generating chart: {}", e.getMessage());
            return null;
        }
    }
}