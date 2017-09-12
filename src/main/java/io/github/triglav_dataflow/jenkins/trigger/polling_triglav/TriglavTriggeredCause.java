package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import hudson.model.Cause;
import hudson.model.ParameterValue;

import java.util.List;

public class TriglavTriggeredCause extends Cause
{
    private List<ParameterValue> buildParameters;

    public TriglavTriggeredCause(List<ParameterValue> buildParameters)
    {
        this.buildParameters = buildParameters;
    }

    @Override
    public String getShortDescription()
    {
        StringBuilder sb = new StringBuilder(String.format("Built by %s (TriglavTriggered) with Parameters: ", PollingTriglavTrigger.class.getName()));
        for (ParameterValue parameterValue : buildParameters) {
            sb.append(parameterValue);
        }
        return sb.toString();
    }
}
