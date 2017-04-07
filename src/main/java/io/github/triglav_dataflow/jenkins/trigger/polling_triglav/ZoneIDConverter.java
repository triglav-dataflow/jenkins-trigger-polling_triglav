package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class ZoneIDConverter
{
    private ZoneIDConverter()
    {
    }

    private static Date dummyDate = new Date();
    private static String ThreeLetterISO8601TimeZoneFormat = "XXX";

    public static String to3LettersISO860(String zoneID)
    {
        SimpleDateFormat sdf = new SimpleDateFormat(ThreeLetterISO8601TimeZoneFormat);
        sdf.setTimeZone(TimeZone.getTimeZone(zoneID));
        return sdf.format(dummyDate);
    }
}
