package name.slava.weighttelebot.model;

import java.time.LocalDate;
import java.util.Objects;

public class WeightEntry {
    private String id;
    private LocalDate date;
    private double weight;

    // Для сериализации/десериализации
    public WeightEntry() {
    }

    public WeightEntry(LocalDate date, double weight) {
        this.date = date;
        this.weight = weight;
        this.id = Long.toString(System.nanoTime());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        WeightEntry that = (WeightEntry) o;
        return Double.compare(weight, that.weight) == 0 && Objects.equals(id, that.id) && Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, date, weight);
    }

    @Override
    public String toString() {
        return "WeightEntry{" +
                "id='" + id + '\'' +
                ", date=" + date +
                ", weight=" + weight +
                '}';
    }
}
