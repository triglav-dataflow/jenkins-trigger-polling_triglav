package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import io.github.triglav_dataflow.client.ApiException;
import io.github.triglav_dataflow.client.JobMessageEachResponse;
import io.github.triglav_dataflow.client.JobResponse;
import io.github.triglav_dataflow.client.ResourceRequest;
import io.github.triglav_dataflow.client.ResourceResponse;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.apache.commons.lang.StringUtils.isBlank;

public class TriglavJob
{
    private static Logger logger = PollingTriglavTrigger.getLogger();
    private final PollingTriglavTrigger.Parameters parameters;
    private final TriglavClient client;

    private Map<String, String> currentMassage;

    public TriglavJob(PollingTriglavTrigger.Parameters parameters, TriglavClient client)
    {
        this.parameters = parameters;
        this.client = client;
    }

    public String id()
    {
        return parameters.jobId();
    }

    public long messageOffset()
    {
        return parameters.jobMessageOffset();
    }

    public Map<String, String> currentMassage()
    {
        return currentMassage;
    }

    public void setId(String id)
    {
        if (isBlank(id)) {
            throw new IllegalArgumentException("Job ID does not exist.");
        }
        parameters.setJobId(id);
    }

    public void setMessageOffset(long messageOffset)
    {
        parameters.setJobMessageOffset(messageOffset);
    }

    public void setNextMessageOffset(long messageOffset)
    {
        parameters.setNextJobMessageOffset(messageOffset);
    }

    public boolean hasId()
    {
        return !isBlank(id());
    }

    public void registerOrUpdate(JenkinsJob jenkinsJob)
            throws ApiException
    {
        boolean requireSetLastJobMessageOffset = false;
        Long jobId;
        if (isBlank(id())) {
            requireSetLastJobMessageOffset = true;
            jobId = null;
        }
        else {
            jobId = Long.valueOf(id());
        }

        logger.fine(String.format("Register Or Update Job: ID: %s, URL: %s", jobId, jenkinsJob.url()));
        JobResponse jr = client.registerOrUpdateJob(jobId, jenkinsJob.url(), resourceRequests(), parameters.logicalOp());

        parameters.setJobId(jr.getId().toString());
        if (requireSetLastJobMessageOffset) {
            setMessageOffset(client.getLastJobMessageId());
        }
        for (ResourceResponse rr : jr.getInputResources()) {
            parameters.setResourceId(rr.getUri(), rr.getId());
        }
    }

    public void destroy()
            throws ApiException
    {
        String jobId = id();
        if (isBlank(jobId)) {
            logger.warning(String.format("Job ID is absent."));
            return;
        }

        logger.fine(String.format("Unregister from Triglav: Job ID: %s", jobId));
        client.unregisterJob(jobId);
    }

    private Optional<ImmutableMap<String, String>> consumeIfPossible()
    {
        if (isBlank(id())) {
            logger.warning(String.format("Job ID does not exist."));
            return Optional.absent();
        }
        Optional<JobMessageEachResponse> optional = client.consumeIfPossible(Long.valueOf(id()), messageOffset());
        if (!optional.isPresent()) {
            return Optional.absent();
        }
        JobMessageEachResponse m = optional.get();
        setNextMessageOffset(m.getId());

        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        builder.put("TRIGLAV_JOB_MESSAGE_ID", m.getId().toString());
        builder.put("TRIGLAV_JOB_MESSAGE_TIME", m.getTime().toString());
        builder.put("TRIGLAV_JOB_MESSAGE_TIMEZONE", m.getTimezone());
        builder.put("TRIGLAV_JOB_ID", m.getJobId().toString());
        return Optional.of(builder.build());
    }

    public boolean poll()
    {
        Optional<ImmutableMap<String, String>> optional = consumeIfPossible();
        if (!optional.isPresent()) {
            currentMassage = null;
            return false;
        }
        currentMassage = optional.get();
        return true;
    }

    private List<ResourceRequest> resourceRequests()
    {
        return Lists.transform(
                Lists.newArrayList(
                        Iterables.filter(parameters.resourceConfigs(), new Predicate<TriglavResourceConfig>()
                        {
                            @Override
                            public boolean apply(@Nullable TriglavResourceConfig input)
                            {
                                if (input == null) {
                                    return false;
                                }
                                return !isBlank(input.getResourceUri());
                            }
                        })
                ),
                new Function<TriglavResourceConfig, ResourceRequest>()
                {
                    @Nullable
                    @Override
                    public ResourceRequest apply(@Nullable TriglavResourceConfig input)
                    {
                        assert input != null;
                        Long resourceId;
                        String _resourceId = input.getResourceId();
                        if (_resourceId == null || _resourceId.isEmpty()) {
                            resourceId = null;
                        }
                        else {
                            resourceId = Long.valueOf(_resourceId);
                        }
                        return client.createResourceRequest(
                                resourceId,
                                input.getResourceUri(),
                                parameters.timeZone(),
                                parameters.timeUnit(),
                                parameters.spanInDays(),
                                parameters.isConsumable(),
                                parameters.isNotifiable());
                    }
                });
    }
}
