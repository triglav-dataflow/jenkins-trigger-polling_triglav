package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;

import java.util.List;
import java.util.Map;

public class JobBuildParametersBuilder
{
    private Map<String, String> parameters;

    public JobBuildParametersBuilder(Map<String, String> parameters)
    {
        this.parameters = parameters;
    }

    public List<ParameterValue> build()
    {
        if (parameters == null) {
            return Lists.newArrayList();
        }

        ImmutableList.Builder<ParameterValue> builder = ImmutableList.builder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            builder.add(new StringParameterValue(entry.getKey(), entry.getValue()));
        }
        return builder.build();
    }
}
