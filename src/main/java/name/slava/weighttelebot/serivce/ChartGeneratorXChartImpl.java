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
    public File generateChart(List<WeightEntry> weightEntries, LocalDate from, LocalDate to) {
        logger.info("Generating chart for {} entries", weightEntries.size());
        try {
            // Сортируем по дате
            List<WeightEntry> sorted = weightEntries.stream()
                    .sorted(Comparator.comparing(WeightEntry::getDate))
                    .collect(Collectors.toList());

            // xData — номер дня (epoch day), чтобы расстояние между точками соответствовало прошедшему времени
            List<Double> xData = new java.util.ArrayList<>();
            List<Double> yData = new java.util.ArrayList<>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yy");

            for (WeightEntry entry : sorted) {
                xData.add((double) entry.getDate().toEpochDay());
                yData.add(entry.getWeight());
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

            if (from != null && to != null) {
                chart.getStyler().setXAxisMin((double) from.toEpochDay());
                chart.getStyler().setXAxisMax((double) to.toEpochDay());
            }

            // Подписи оси X: дата для целых дней, дробные деления оставляем без подписи
            chart.setCustomXAxisTickLabelsFormatter(x -> {
                double day = Math.rint(x);
                if (Math.abs(x - day) > 1e-6) {
                    return "";
                }
                return LocalDate.ofEpochDay((long) day).format(formatter);
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