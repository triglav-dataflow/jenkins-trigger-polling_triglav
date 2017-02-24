package io.github.triglav_dataflow.jenkins.trigger.polling_triglav.unit;

import java.util.Locale;

public enum TimeUnit
{
    HOURLY,
    DAILY,
    SINGULAR;

    @Override
    public String toString()
    {
        return name().toLowerCase(Locale.ENGLISH);
    }

    @SuppressWarnings("unused")
    public TimeUnit fromString(String unit)
    {
        return TimeUnit.valueOf(unit.toUpperCase(Locale.ENGLISH));
    }
}
