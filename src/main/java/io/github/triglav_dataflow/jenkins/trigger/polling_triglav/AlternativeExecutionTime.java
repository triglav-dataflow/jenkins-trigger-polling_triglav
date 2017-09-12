package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import java.util.Calendar;
import java.util.Date;

public class AlternativeExecutionTime
{
    private AlternativeExecutionTime()
    {
    }

    public static final boolean shouldBuild(Date alternativeExecutionTime, JenkinsJob jenkinsJob, TimeUnit timeUnit)
    {
        if (TimeUnit.SINGULAR.equals(timeUnit) || null == alternativeExecutionTime) {
            // insufficient requirements
            return false;
        }

        if (alternativeExecutionTime.after(new Date())) {
            // alternativeExecutionTime has not passed
            return false;
        }

        Date lastBuildDate = jenkinsJob.getLastBuildDate();
        Calendar calendarLastBuild = null;

        if (null != lastBuildDate) {
            calendarLastBuild = Calendar.getInstance();
            calendarLastBuild.setTime(lastBuildDate);
        }

        Calendar calendarCheck = Calendar.getInstance();
        if (TimeUnit.HOURLY.equals(timeUnit)) {
            calendarCheck.set(Calendar.MINUTE, 0);
            calendarCheck.set(Calendar.SECOND, 0);
        }
        else if (TimeUnit.DAILY.equals(timeUnit)) {
            calendarCheck.set(Calendar.HOUR_OF_DAY, 0);
            calendarCheck.set(Calendar.MINUTE, 0);
            calendarCheck.set(Calendar.SECOND, 0);
        }

        // If last build was before TODAY (daily) or before CURRENT HOUR (hourly), or if there is no build
        if (null == calendarLastBuild || calendarLastBuild.before(calendarCheck)) {
            return true;
        }

        return false;
    }
}
