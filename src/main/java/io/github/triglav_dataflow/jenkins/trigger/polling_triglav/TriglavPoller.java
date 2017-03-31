package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import io.github.triglav_dataflow.client.ApiClient;
import io.github.triglav_dataflow.client.ApiException;
import io.github.triglav_dataflow.client.Configuration;
import io.github.triglav_dataflow.client.Credential;
import io.github.triglav_dataflow.client.JobMessageEachResponse;
import io.github.triglav_dataflow.client.JobRequest;
import io.github.triglav_dataflow.client.JobResponse;
import io.github.triglav_dataflow.client.ResourceRequest;
import io.github.triglav_dataflow.client.ResourceResponse;
import io.github.triglav_dataflow.client.TokenResponse;
import io.github.triglav_dataflow.client.api.AuthApi;
import io.github.triglav_dataflow.client.api.JobMessagesApi;
import io.github.triglav_dataflow.client.api.JobsApi;
import io.github.triglav_dataflow.client.api.ResourcesApi;
import org.apache.commons.lang.builder.ToStringBuilder;

import javax.annotation.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class TriglavPoller
{
    private static Logger logger = PollingTriglavTriggerPlugin.getLogger();
    private final PluginParameter params;
    private final ApiClient client;

    TriglavPoller(PluginParameter params)
    {
        this.params = params;
        this.client = newApiClient();

        configure();
    }

    private ApiClient newApiClient()
    {
        ApiClient c = Configuration.getDefaultApiClient();
        c.setBasePath(params.triglavApiUrl());
        // TODO: make the below params configurable
        // TODO: appropriate the below params
        c.getHttpClient().setReadTimeout(5L, TimeUnit.SECONDS);
        c.getHttpClient().setConnectTimeout(5L, TimeUnit.SECONDS);
        c.getHttpClient().setWriteTimeout(5L, TimeUnit.SECONDS);
        c.getHttpClient().setRetryOnConnectionFailure(true);
        c.getHttpClient().setFollowRedirects(true);
        c.getHttpClient().setFollowSslRedirects(true);

        return c;
    }

    private void configure()
    {
        authenticate();
    }

    private void authenticate()
    {
        if (!params.apiKey().isEmpty()) {
            synchronized (client) // io.github.triglav_dataflow.client.ApiClient#setApiKey is not defined with `synchronized`.
            {
                client.setApiKey(params.apiKey());
            }
            if (isValidApiKey()) {
                return;
            }
        }

        try {
            TokenResponse token = new AuthApi(client).createToken(buildCredential());
            params.setApiKey(token.getAccessToken());
            synchronized (client) // io.github.triglav_dataflow.client.ApiClient#setApiKey is not synchronized.
            {
                client.setApiKey(token.getAccessToken());
            }
        }
        catch (ApiException e) {
            throw Throwables.propagate(e);
        }
    }

    private boolean isValidApiKey()
    {
        try {
            new AuthApi(client).me();
            return true;
        }
        catch (ApiException e) {
            logger.warning(String.format("[%s] apiKey:%s is expired.", getClass().getName(), params.apiKey()));
            logger.fine(e.getMessage());
            return false;
        }
    }

    private Credential buildCredential()
    {
        Credential c = new Credential();
        c.setUsername(params.username());
        c.setPassword(params.password());
        c.setAuthenticator(params.authenticator());
        return c;
    }

    public Optional<JobMessageEachResponse> poll()
    {
        Optional<JobResponse> job = findJob(params.jobId());
        if (!job.isPresent()) {
            throw new IllegalStateException(String.format("[%s] job_id: %s is not found.", getClass().getName(), params.jobId()));
        }
        try {
            List<JobMessageEachResponse> responses = new JobMessagesApi(client).fetchJobMessages(params.jobMessageOffset(), job.get().getId(), 1L);
            if (responses.isEmpty()) {
                return Optional.absent();
            }
            params.setNextJobMessageOffset(responses.get(0).getId());
            return Optional.of(responses.get(0));
        }
        catch (ApiException e) {
            throw Throwables.propagate(e);
        }
    }

    private Optional<JobResponse> findJob(String idOrUrl)
    {
        if (idOrUrl.isEmpty()) {
            return Optional.absent();
        }
        try {
            return Optional.of(new JobsApi(client).getJob(idOrUrl));
        }
        catch (ApiException e) {
            return Optional.absent();
        }
    }

    public void registerOrUpdateJob(String jobUrl)
    {
        final boolean[] isRegisteredOrUpdated = {false};

        JobRequest jr = new JobRequest();

        Optional<JobResponse> job = findJob(params.jobId());
        if (!job.isPresent()) {
            // new job
            isRegisteredOrUpdated[0] = true;
        }
        else {
            if (job.get().getUri().contentEquals(jobUrl)) {
                jr.setId(job.get().getId());
            }
            else {
                isRegisteredOrUpdated[0] = true;
                logger.warning(String.format(
                        "[%s] job Url: %s will be updated to %s with Job Id. The old job spec is %s",
                        getClass().getName(),
                        job.get().getUri(),
                        jobUrl,
                        ToStringBuilder.reflectionToString(job.get())));
            }
        }
        jr.setUri(jobUrl);
        jr.setLogicalOp(params.logicalOp());
        jr.setInputResources(Lists.transform(params.resourceUris(), new Function<String, ResourceRequest>()
        {
            @Nullable
            @Override
            public ResourceRequest apply(@Nullable String input)
            {
                assert input != null;
                ResourceRequest rr = new ResourceRequest();

                if (!params.lookupResourceId(input).isPresent()) {
                    isRegisteredOrUpdated[0] = true;
                }
                else {
                    Optional<ResourceResponse> r = findResource(params.lookupResourceId(input).get());
                    if (!r.isPresent()) {
                        isRegisteredOrUpdated[0] = true;
                    }
                    else {
                        rr.setId(r.get().getId());

                        // check differences
                        if (!r.get().getUri().contentEquals(input)) {
                            isRegisteredOrUpdated[0] = true;
                            logger.warning(String.format("resource Uri: %s will be updated to %s. The old resource spec is %s", r.get().getUri(), input, ToStringBuilder.reflectionToString(r.get())));
                        }
                        if (!r.get().getTimezone().contentEquals(params.timeZone())) {
                            isRegisteredOrUpdated[0] = true;
                            logger.warning(String.format("resource Timezone: %s will be updated to %s. The old resource spec is %s", r.get().getTimezone(), params.timeZone(), ToStringBuilder.reflectionToString(r.get())));
                        }
                        if (!r.get().getUnit().contentEquals(params.timeUnit())) {
                            isRegisteredOrUpdated[0] = true;
                            logger.warning(String.format("resource Unit: %s will be updated to %s. The old resource spec is %s", r.get().getUnit(), params.timeUnit(), ToStringBuilder.reflectionToString(r.get())));
                        }
                        if (r.get().getConsumable() != params.isConsumable()) {
                            isRegisteredOrUpdated[0] = true;
                            logger.warning(String.format("resource Consumable: %s will be updated to %s. The old resource spec is %s", r.get().getConsumable(), params.isConsumable(), ToStringBuilder.reflectionToString(r.get())));
                        }
                        if (r.get().getNotifiable() != params.isNotifiable()) {
                            isRegisteredOrUpdated[0] = true;
                            logger.warning(String.format("resource Notifiable: %s will be updated to %s. The old resource spec is %s", r.get().getNotifiable(), params.isNotifiable(), ToStringBuilder.reflectionToString(r.get())));
                        }
                    }
                }

                rr.setUri(input);
                rr.setTimezone(params.timeZone());
                rr.setUnit(params.timeUnit());
                rr.setConsumable(params.isConsumable());
                rr.setNotifiable(params.isNotifiable());
                return rr;
            }
        }));

        JobResponse jobResponse;
        try {
            jobResponse = new JobsApi(client).createOrUpdateJob(jr);
        }
        catch (ApiException e) {
            throw Throwables.propagate(e);
        }

        for (ResourceResponse r : jobResponse.getInputResources()) {
            params.setResourceId(r.getUri(), r.getId());
        }
        if (isRegisteredOrUpdated[0]) {
            params.setJobId(jobResponse.getId());
            params.setJobMessageOffset(getLastJobMessageId());
        }
    }

    private Optional<ResourceResponse> findResource(String idOrUrl)
    {
        if (idOrUrl.isEmpty()) {
            return Optional.absent();
        }
        try {
            return Optional.of(new ResourcesApi(client).getResource(idOrUrl));
        }
        catch (ApiException e) {
            return Optional.absent();
        }
    }

    private long getLastJobMessageId()
    {
        try {
            return new JobMessagesApi(client).getLastJobMessageId().getId();
        }
        catch (ApiException e) {
            throw Throwables.propagate(e);
        }
    }

    public void unregisterJob()
    {
        if (params.jobId().isEmpty()) {
            return;
        }

        Optional<JobResponse> job = findJob(params.jobId());
        if (!job.isPresent()) {
            params.setEmptyJobId();
            return;
        }

        try {
            new JobsApi(client).deleteJob(job.get().getId().toString());
        }
        catch (ApiException e) {
            throw Throwables.propagate(e);
        }
        params.setEmptyJobId();
    }
}
