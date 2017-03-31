package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import io.github.triglav_dataflow.client.Credential;
import io.github.triglav_dataflow.jenkins.trigger.polling_triglav.unit.TimeZoneConverter;

import javax.annotation.Nullable;

import java.util.List;

/*
 This class provides getter, setter, and utilities for plugin parameters about PollingTriglavTriggerPlugin.
 */

public class PluginParameter
{
    private final PollingTriglavTriggerPlugin plugin;

    PluginParameter(PollingTriglavTriggerPlugin plugin)
    {
        this.plugin = plugin;
    }

    public String triglavApiUrl()
    {
        return PollingTriglavTriggerPlugin.getTriglavApiUrl();
    }

    /*
     section : PollingTriglavTriggerPlugin
     */

    public String jobId()
    {
        return plugin.getJobId();
    }

    public void setJobId(String jobId)
    {
        plugin.setJobId(jobId);
    }

    public void setJobId(long jobId)
    {
        setJobId(String.valueOf(jobId));
    }

    public void setEmptyJobId()
    {
        setJobId("");
    }

    public String username()
    {
        return plugin.getUsername();
    }

    public String password()
    {
        return plugin.getPassword();
    }

    public String apiKey()
    {
        return plugin.getApiKey();
    }

    public void setApiKey(String apiKey)
    {
        plugin.setApiKey(apiKey);
    }

    public Credential.AuthenticatorEnum authenticator()
    {
        return Credential.AuthenticatorEnum.valueOf(plugin.getAuthenticator());
    }

    public long jobMessageOffset()
    {
        return plugin.getJobMessageOffset();
    }

    public void setJobMessageOffset(long jobMessageOffset)
    {
        plugin.setJobMessageOffset(jobMessageOffset);
    }

    // Triglav execute that kind of query : SELECT  `job_messages`.* FROM `job_messages` WHERE (id >= '1') AND `job_messages`.`job_id` = 1 ORDER BY `job_messages`.`id` ASC LIMIT 1
    // So, need to set incremented id after finding job_message.
    public void setNextJobMessageOffset(long jobMessageOffset)
    {
        setJobMessageOffset(jobMessageOffset + 1L);
    }

    public String timeZone()
    {
        return TimeZoneConverter.convertToThreeLetterISO8601TimeZone(plugin.getTimeZone());
    }

    public String timeUnit()
    {
        return plugin.getTimeUnit();
    }

    public String logicalOp()
    {
        return plugin.getLogicalOp();
    }

    /*
     section : PollingTriglavTriggerResourceConfig
      */

    /*
      True if this resource should be consumed.
      Input resources are automatically set to true,
      and output resources are set to false
    */
    public boolean isConsumable()
    {
        return true;
    }

    /*
      True if a job notifies its end of task to triglav for this resource,
      that is, monitoring in agent is not necessary
    */
    public boolean isNotifiable()
    {
        return false;
    }

    private Optional<PollingTriglavTriggerResourceConfig> lookupResourceConfig(String resourceUri)
    {
        for (PollingTriglavTriggerResourceConfig rConfig : plugin.getResourceConfigs()) {
            if (rConfig.getResourceUri().contentEquals(resourceUri)) {
                return Optional.of(rConfig);
            }
        }
        return Optional.absent();
    }

    public Optional<String> lookupResourceId(String resourceUri)
    {
        Optional<PollingTriglavTriggerResourceConfig> rConfig = lookupResourceConfig(resourceUri);
        if (rConfig.isPresent()) {
            return Optional.of(rConfig.get().getResourceId());
        }
        else {
            return Optional.absent();
        }
    }

    public void setResourceId(String resourceUri, String resourceId)
    {
        Optional<PollingTriglavTriggerResourceConfig> rConfig = lookupResourceConfig(resourceUri);
        if (rConfig.isPresent()) {
            rConfig.get().setResourceId(resourceId);
        }
        else {
            throw Throwables.propagate(new IllegalStateException(String.format("ResourceUri:%s is not found.", resourceUri)));
        }
    }

    public void setResourceId(String resourceUri, long resourceId)
    {
        setResourceId(resourceUri, String.valueOf(resourceId));
    }


    public List<String> resourceUris()
    {
        return Lists.transform(plugin.getResourceConfigs(), new Function<PollingTriglavTriggerResourceConfig, String>()
        {
            @Nullable
            @Override
            public String apply(@Nullable PollingTriglavTriggerResourceConfig input)
            {
                assert input != null;
                return input.getResourceUri();
            }
        });
    }
}
