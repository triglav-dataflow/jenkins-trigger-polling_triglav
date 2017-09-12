package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import hudson.model.Cause;

public class AlternativeExecutionTimeTriggeredCause extends Cause
{
    @Override
    public String getShortDescription()
    {
        return String.format("Built by %s (AlternativeExecutionTimeTriggered)", PollingTriglavTrigger.class.getName());
    }
}
