package io.triglav.jenkins.trigger.polling_triglav;

import com.google.common.collect.Lists;
import hudson.model.BuildableItem;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import jenkins.model.Jenkins;

import java.util.List;

public class PollingTriglavTriggerJobWrapper
{
    private final FreeStyleProject job;
    private List<ParameterValue> buildParameters = Lists.newArrayList();

    public PollingTriglavTriggerJobWrapper(BuildableItem job)
    {
        this.job = (FreeStyleProject) job;
    }

    public FreeStyleProject job()
    {
        return job;
    }

    public List<ParameterValue> buildParameters()
    {
        return buildParameters;
    }

    public String url()
    {
        return Jenkins.getInstance().getRootUrl() + job.getSearchUrl();
    }

    /*
     * A job must be blocked if its own previous build is in progress,
     * or if the blockBuildWhenUpstreamBuilding option is true and an upstream
     * job is building, but derived classes can also check other conditions.
     */
    public boolean isJobBuildBlocked()
    {
        return job.isBuildBlocked();
    }

    public boolean isJobDisabled()
    {
        return job.isDisabled();
    }

    public synchronized void setBuildParameter(String key, String value)
    {
        buildParameters.add(new StringParameterValue(key, value));
    }

    public void buildWithBuildParameters()
    {
        job().scheduleBuild(
                0,
                new PollingTriglavTriggerCause(),
                new ParametersAction(buildParameters()));
    }
}
