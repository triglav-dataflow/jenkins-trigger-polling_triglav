package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

public enum TimeUnit
{
    HOURLY("hourly"),
    DAILY("daily"),
    SINGULAR("singular");

    private String timeUnit;

    private TimeUnit(String timeUnit)
    {
        this.timeUnit = timeUnit;
    }

    public String getValue()
    {
        return timeUnit;
    }
}
