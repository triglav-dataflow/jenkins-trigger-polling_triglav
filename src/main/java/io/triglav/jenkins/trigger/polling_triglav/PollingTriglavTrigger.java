package io.triglav.jenkins.trigger.polling_triglav;

import com.google.common.base.Optional;
import io.triglav.client.JobMessageEachResponse;

import java.util.logging.Logger;

public class PollingTriglavTrigger
{
    private static Logger logger = PollingTriglavTriggerPlugin.getLogger();

    private final PollingTriglavTriggerJobWrapper job;
    private final PollingTriglavTriggerPoller poller;

    public PollingTriglavTrigger(PollingTriglavTriggerJobWrapper job, PollingTriglavTriggerPoller poller)
    {
        this.job = job;
        this.poller = poller;

        configure();
    }

    private void configure()
    {
        if (job.isJobDisabled()) {
            logger.info(String.format("[%s] Unregister Job :%s", getClass().getName(), job.url()));
            poller.unregisterJob();
            return;
        }

        logger.info(String.format("[%s] Register or Update a Job: %s", getClass().getName(), job.url()));
        poller.registerOrUpdateJob(job.url());
    }

    private void prepareKick(JobMessageEachResponse m)
    {
        job.setBuildParameter("TRIGLAV_JOB_MESSAGE_ID", m.getId().toString());
        job.setBuildParameter("TRIGLAV_JOB_MESSAGE_TIME", m.getTime().toString());
        job.setBuildParameter("TRIGLAV_JOB_MESSAGE_TIMEZONE", m.getTimezone());
        job.setBuildParameter("TRIGLAV_JOB_ID", m.getJobId().toString());
    }

    public boolean canKick()
    {
        if (job.isJobBuildBlocked() || job.isJobDisabled()) {
            return false;
        }

        Optional<JobMessageEachResponse> message = poller.poll();
        if (!message.isPresent()) {
            return false;
        }

        prepareKick(message.get());
        return true;
    }

    public void kick()
    {
        // TODO: handling errors & notify maintainers
        job.buildWithBuildParameters();
    }
}
