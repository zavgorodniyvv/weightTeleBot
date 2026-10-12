package name.slava.weighttelebot.enums;

import java.time.LocalDate;
import java.time.Period;

public enum ChartPeriod {
    WEEK("1 неделя", Period.ofWeeks(1)),
    MONTH("1 месяц", Period.ofMonths(1)),
    HALF_YEAR("6 месяцев", Period.ofMonths(6)),
    ALL("Весь период", null);

    private final String label;
    private final Period period;

    ChartPeriod(String label, Period period) {
        this.label = label;
        this.period = period;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Начальная дата периода или null, если период не ограничен.
     */
    public LocalDate startDate(LocalDate today) {
        return period == null ? null : today.minus(period);
    }
}
