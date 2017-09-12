package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.ParameterValue;
import io.github.triglav_dataflow.client.ApiException;

import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ItemRunnable implements Runnable
{
    private static Logger logger = PollingTriglavTrigger.getLogger();
    JenkinsJob jenkinsJob;
    private PollingTriglavTrigger plugin;

    public ItemRunnable(PollingTriglavTrigger plugin, BuildableItem job)
    {
        this.plugin = plugin;
        this.jenkinsJob = new JenkinsJob(job);
    }

    public void run()
    {
        if (jenkinsJob.isDisabled()) {
            // no log
            return;
        }
        if (jenkinsJob.isBuildBlocked()) {
            logger.fine(String.format("Job: %s is blocked.", jenkinsJob.name()));
            return;
        }

        TriglavClient triglavClient = initializeTriglavClient();

        if (null != triglavClient) {
            TriglavJob triglavJob = new TriglavJob(plugin.parameters(), triglavClient);

            if (!triglavJob.hasId()) {
                logger.fine(String.format(
                    "Job: %s is skipped because Job does not have Triglav Job ID.",
                    jenkinsJob.name()));
                return;
            }

            boolean builtJenkinsJobs = performTriglavPolling(triglavJob);

            if (builtJenkinsJobs) {
                return;
            }
        }

        performAlternativeExecutionTimeCheck();
    }

    private TriglavClient initializeTriglavClient()
    {
        TriglavClient triglavClient = null;

        try {
            triglavClient = TriglavClient.fromTriggerParameter(plugin.parameters());
        }
        catch (ApiException e) {
            logger.warning(String.format("Error in %s: %s ", ItemRunnable.class.getName(), e));
        }

        return triglavClient;
    }

    private boolean performTriglavPolling(TriglavJob triglavJob)
    {
        int i = 0;
        while (triglavJob.poll()) {
            i++;
            logger.fine(String.format("Enqueue Job %s triggered by Triglav. Enqueue count %d.", jenkinsJob.name(), i));
            List<ParameterValue> buildParameters = new JobBuildParametersBuilder(triglavJob.currentMessage()).build();
            jenkinsJob.setBuildParameters(buildParameters);
            Cause cause = new TriglavTriggeredCause(buildParameters);
            jenkinsJob.buildAndSave(cause);

            if (i >= PollingTriglavTrigger.getMaxEnqueueCount()) {
                logger.fine(String.format("Max enqueue count %d is exceeded. Wait until next enqueue chance.", i));
                break;
            }
        }

        return i > 0;
    }

    private void performAlternativeExecutionTimeCheck()
    {
        Date alternativeExecutionTime = plugin.parameters().alternativeExecutionTime();
        TimeUnit timeUnit = plugin.parameters().timeUnit();

        if (AlternativeExecutionTime.shouldBuild(alternativeExecutionTime, jenkinsJob, timeUnit)) {
            logger.fine(String.format("Enqueue Job %s using alternativeExecutionTime.", jenkinsJob.name()));
            Cause cause = new AlternativeExecutionTimeTriggeredCause();
            jenkinsJob.buildAndSave(cause);
        }
    }
}
