package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import hudson.model.AbstractProject;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class JenkinsJob
{
    private static Logger logger = PollingTriglavTrigger.getLogger();
    private final BuildableItem item;
    private List<ParameterValue> buildParameters = Lists.newArrayList();

    public JenkinsJob(BuildableItem item)
    {
        this.item = item;
    }

    public String name()
    {
        return item.getFullName();
    }

    public String url()
    {
        return jenkins().getRootUrl() + item.getSearchUrl();
    }

    public List<ParameterValue> buildParameters()
    {
        return buildParameters;
    }

    public synchronized void setBuildParameters(Map<String, String> parameters)
    {
        if (parameters == null) {
            buildParameters = Lists.newArrayList();
            return;
        }

        ImmutableList.Builder<ParameterValue> builder = ImmutableList.builder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            builder.add(new StringParameterValue(entry.getKey(), entry.getValue()));
        }
        buildParameters = builder.build();
    }

    public void build()
    {
        if (item instanceof AbstractProject) {
            ((AbstractProject) item).scheduleBuild(
                    0,
                    buildWithParametersCause(),
                    new ParametersAction(buildParameters()));
        }
        else {
            item.scheduleBuild(0, defaultCause());
        }
    }

    /*
     * A job must be blocked if its own previous build is in progress,
     * or if the blockBuildWhenUpstreamBuilding option is true and an upstream
     * job is building, but derived classes can also check other conditions.
     */
    public boolean isBuildBlocked()
    {
        return item.isBuildBlocked();
    }

    public boolean isDisabled()
    {
        if (item instanceof AbstractProject) {
            return ((AbstractProject) item).isDisabled();
        }
        return false;
    }

    public void save()
    {
        try {
            item.save();
        }
        catch (IOException e) {
            logger.throwing(getClass().getName(), "save", e);
            throw Throwables.propagate(e);
        }
    }

    private Jenkins jenkins()
    {
        return Jenkins.getInstance();
    }

    private Cause buildWithParametersCause()
    {
        return new Cause() {
            @Override
            public String getShortDescription()
            {
                StringBuilder sb = new StringBuilder(String.format("Built by %s with Parameters: ", PollingTriglavTrigger.class.getName()));
                for (ParameterValue parameterValue : buildParameters()) {
                    sb.append(parameterValue);
                }
                return sb.toString();
            }
        };
    }

    private Cause defaultCause()
    {
        return new Cause() {
            @Override
            public String getShortDescription()
            {
                return String.format("Built by %s.", PollingTriglavTrigger.class.getName());
            }
        };
    }
}
