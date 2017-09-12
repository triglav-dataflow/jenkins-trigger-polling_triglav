package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import com.google.common.collect.Lists;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class JenkinsJob
{
    private static Logger logger = PollingTriglavTrigger.getLogger();
    private final AbstractProject item;
    private List<ParameterValue> buildParameters = Lists.newArrayList();

    public JenkinsJob(BuildableItem item)
    {
        if (!(item instanceof AbstractProject)) {
            IllegalArgumentException e = new IllegalArgumentException(
                String.format("Error: Expected instance of %s, but got %s. Job: %s", AbstractProject.class.getName(), item.getClass().getName(), item.getFullName()));
            logger.throwing(JenkinsJob.class.getName(), "build", e);
            throw e;
        }

        this.item = (AbstractProject) item;
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

    public void setBuildParameters(List<ParameterValue> buildParameters)
    {
        this.buildParameters = buildParameters;
    }

    public void build(Cause cause) throws RuntimeException
    {
        item.scheduleBuild(
            0,
            cause,
            new ParametersAction(buildParameters()));
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
        return item.isDisabled();
    }

    public Date getLastBuildDate()
    {
        AbstractBuild lastBuild = item.getLastBuild();

        if (null != lastBuild) {
            return lastBuild.getTime();
        }

        return null;
    }

    public void save()
            throws IOException
    {
        try {
            item.save();
        }
        catch (IOException e) {
            logger.throwing(getClass().getName(), "save", e);
            throw e;
        }
    }

    public void buildAndSave(Cause cause)
    {
        build(cause);
        try {
            save();
        }
        catch (IOException e) {
            logger.throwing(JenkinsJob.class.getName(), "buildAndSave", e);
            throw new RuntimeException(
                String.format("Error: %s, Job: %s", e.getMessage(), name()), e);
        }
    }

    private Jenkins jenkins()
    {
        return Jenkins.getInstance();
    }
}
