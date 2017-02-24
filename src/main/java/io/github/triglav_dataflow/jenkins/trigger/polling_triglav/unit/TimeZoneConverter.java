package io.github.triglav_dataflow.jenkins.trigger.polling_triglav.unit;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class TimeZoneConverter
{
    private TimeZoneConverter()
    {
    }

    private static Date dummyDate = new Date();
    private static String ThreeLetterISO8601TimeZoneFormat = "XXX";

    public static String convertToThreeLetterISO8601TimeZone(String zoneID)
    {
        SimpleDateFormat sdf = new SimpleDateFormat(ThreeLetterISO8601TimeZoneFormat);
        sdf.setTimeZone(TimeZone.getTimeZone(zoneID));
        return sdf.format(dummyDate);
    }
}
