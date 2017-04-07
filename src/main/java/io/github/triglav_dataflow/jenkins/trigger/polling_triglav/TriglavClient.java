package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.squareup.okhttp.OkHttpClient;
import io.github.triglav_dataflow.client.ApiClient;
import io.github.triglav_dataflow.client.ApiException;
import io.github.triglav_dataflow.client.Configuration;
import io.github.triglav_dataflow.client.Credential;
import io.github.triglav_dataflow.client.JobMessageEachResponse;
import io.github.triglav_dataflow.client.JobRequest;
import io.github.triglav_dataflow.client.JobResponse;
import io.github.triglav_dataflow.client.ResourceRequest;
import io.github.triglav_dataflow.client.TokenResponse;
import io.github.triglav_dataflow.client.api.AuthApi;
import io.github.triglav_dataflow.client.api.JobMessagesApi;
import io.github.triglav_dataflow.client.api.JobsApi;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class TriglavClient // TODO: HTTP Request requires Retriable?
{
    private static Logger logger = PollingTriglavTrigger.getLogger();

    interface OkHttpClientConfigurable
    {
        void configure(OkHttpClient httpClient);
    }

    public static OkHttpClientConfigurable getDefaultConfigurable()
    {
        return new OkHttpClientConfigurable() {
            @Override
            public void configure(OkHttpClient httpClient)
            {
                httpClient.setReadTimeout(5L, TimeUnit.SECONDS);
                httpClient.setConnectTimeout(5L, TimeUnit.SECONDS);
                httpClient.setWriteTimeout(5L, TimeUnit.SECONDS);
                httpClient.setRetryOnConnectionFailure(true);
                httpClient.setFollowRedirects(true);
                httpClient.setFollowSslRedirects(true);
            }
        };
    }

    public static TriglavClient fromTriggerParameter(PollingTriglavTrigger.Parameters parameters)
    {
        TriglavClient client = new TriglavClient(parameters.triglavApiUrl(), getDefaultConfigurable());
        try {
            client.authenticate(
                    parameters.username(),
                    parameters.password(),
                    parameters.authenticator(),
                    parameters.apiKey());
        }
        catch (ApiException e) {
            throw Throwables.propagate(e);
        }
        parameters.setApiKey(client.getApiKey());
        return client;
    }

    public static TriglavClient fromTriggerAdminParameter()
    {
        TriglavClient client = new TriglavClient(PollingTriglavTrigger.getTriglavApiUrl(), getDefaultConfigurable());
        try {
            client.authenticate(
                    PollingTriglavTrigger.getAdminUsername(),
                    PollingTriglavTrigger.getAdminPassword(),
                    Credential.AuthenticatorEnum.LOCAL, // TODO: from UI?
                    PollingTriglavTrigger.getAdminApiKey());
        }
        catch (ApiException e) {
            throw Throwables.propagate(e);
        }
        PollingTriglavTrigger.setAdminApiKey(client.getApiKey());
        return client;
    }

    private final ApiClient client;
    private String apiKey;

    public TriglavClient(String apiUrl, OkHttpClientConfigurable configurable)
    {
        ApiClient c = Configuration.getDefaultApiClient();
        c.setBasePath(apiUrl);
        configurable.configure(c.getHttpClient());
        this.client = c;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
        synchronized (client) { // io.github.triglav_dataflow.client.ApiClient#apiKey is not defined with `synchronized`.
            client.setApiKey(apiKey);
        }
    }

    /**
     * @param username
     * @param password
     * @param apiKey
     * @return String apiKey
     */
    public void authenticate(
            String username,
            String password,
            Credential.AuthenticatorEnum authenticator,
            String apiKey)
            throws ApiException
    {
        if (!apiKey.isEmpty()) {
            setApiKey(apiKey);
            if (isValidApiKey()) {
                return;
            }
        }

        logger.fine(String.format("Create new api key."));

        Credential credential = new Credential();
        credential.setUsername(username);
        credential.setPassword(password);
        credential.setAuthenticator(authenticator);
        TokenResponse token = new AuthApi(client).createToken(credential);
        setApiKey(token.getAccessToken());
    }

    private boolean isValidApiKey()
    {
        try {
            new AuthApi(client).me();
            return true;
        }
        catch (ApiException e) {
            logger.warning(String.format("Api Key: %s is expired. Message: %s", apiKey, e.getMessage()));
            return false;
        }
    }

    public void unregisterJob(String jobId)
            throws ApiException
    {
        if (jobId == null || jobId.isEmpty()) {
            throw new IllegalArgumentException("Job ID must has value.");
        }
        new JobsApi(client).deleteJob(jobId);
    }

    public ResourceRequest createResourceRequest(
            Long resourceId,
            String resourceUri,
            String timeZone,
            String timeUnit,
            boolean isConsumable,
            boolean isNotifiable)
    {
        ResourceRequest rr = new ResourceRequest();
        if (resourceId != null) {
            rr.setId(resourceId);
        }
        rr.setUri(resourceUri);
        rr.setTimezone(timeZone);
        rr.setUnit(timeUnit);
        rr.setConsumable(isConsumable);
        rr.setNotifiable(isNotifiable);
        return rr;
    }

    public JobResponse registerOrUpdateJob(
            Long jobId,
            String jobUrl,
            List<ResourceRequest> resources,
            String logicalOp)
            throws ApiException
    {
        JobRequest jr = new JobRequest();
        if (jobId != null) {
            jr.setId(jobId);
        }
        jr.setUri(jobUrl);
        jr.setLogicalOp(logicalOp);
        jr.setInputResources(resources);
        jr.setLogicalOp(logicalOp);

        return new JobsApi(client).createOrUpdateJob(jr);
    }

    public long getLastJobMessageId()
            throws ApiException
    {
        return new JobMessagesApi(client).getLastJobMessageId().getId();
    }

    public Optional<JobMessageEachResponse> consumeIfPossible(Long jobId, long jobMessageOffset)
    {
        if (jobId == null) {
            throw new IllegalArgumentException("Job ID must has value.");
        }

        logger.fine(String.format("Consume messages if possible: Job ID: %s, Offset: %s", jobId, jobMessageOffset));

        try {
            List<JobMessageEachResponse> responses = new JobMessagesApi(client).fetchJobMessages(jobMessageOffset, jobId, 1L);
            if (responses.isEmpty()) {
                return Optional.absent();
            }
            return Optional.of(responses.get(0));
        }
        catch (ApiException e) {
            logger.warning(e.getMessage());
            return Optional.absent();
        }
    }

}
