package ppb.qrattend.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class AppClock {

    public static final ZoneId PHT_ZONE = ZoneId.of("Asia/Manila");
    public static final String TIME_ZONE_LABEL = "PHT";

    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    private AppClock() {
    }

    public static LocalDate today() {
        return LocalDate.now(PHT_ZONE);
    }

    public static LocalTime nowTime() {
        return LocalTime.now(PHT_ZONE).withNano(0);
    }

    public static LocalDateTime nowDateTime() {
        return LocalDateTime.now(PHT_ZONE).withNano(0);
    }

    public static DayOfWeek todayDay() {
        return today().getDayOfWeek();
    }

    public static String nowLabel() {
        return nowDateTime().format(CLOCK_FORMAT) + " " + TIME_ZONE_LABEL;
    }
}
