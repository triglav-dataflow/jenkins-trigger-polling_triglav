package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import antlr.ANTLRException;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.mapper.Mapper;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.RobustReflectionConverter;
import io.github.triglav_dataflow.client.ApiException;
import io.github.triglav_dataflow.client.Credential;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;

import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;

import static io.github.triglav_dataflow.jenkins.trigger.polling_triglav.TriglavClient.getDefaultConfigurable;

public class PollingTriglavTrigger
        extends Trigger<BuildableItem>
{
    public static DescriptorImpl getClassDescriptor()
    {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(PollingTriglavTrigger.class);
    }

    public static String getCrontabSpec()
    {
        return getClassDescriptor().getCrontabSpec();
    }

    public static String getTriglavApiUrl()
    {
        return getClassDescriptor().getTriglavApiUrl();
    }

    public static String getAdminUsername()
    {
        return getClassDescriptor().getAdminUsername();
    }

    public static String getAdminPassword()
    {
        return getClassDescriptor().getAdminPassword();
    }

    public static String getAdminApiKey()
    {
        return getClassDescriptor().getAdminApiKey();
    }

    public static void setAdminApiKey(String adminApiKey)
    {
        getClassDescriptor().setAdminApiKey(adminApiKey);
    }

    public static int getMaxEnqueueCount()
    {
        return getClassDescriptor().getMaxEnqueueCount();
    }

    @Override
    public DescriptorImpl getDescriptor()
    {
        return getClassDescriptor();
    }

    private static final Logger logger = Logger.getLogger(PollingTriglavTrigger.class.getName());
    private String jobId;
    private final String username;
    private final String password;
    private final String authenticator;
    private String apiKey;
    private long jobMessageOffset;
    private final String timeZone;
    private final String timeUnit;
    private final String logicalOp;
    private final List<TriglavResourceConfig> resourceConfigs;
    private final Parameters parameters;

    static Logger getLogger()
    {
        return logger;
    }

    @DataBoundConstructor
    public PollingTriglavTrigger(
            String jobId,
            String username,
            String password,
            String authenticator,
            String apiKey,
            long jobMessageOffset,
            String timeZone,
            String timeUnit,
            String logicalOp,
            List<TriglavResourceConfig> resourceConfigs)
            throws ANTLRException
    {
        super(getCrontabSpec());
        this.jobId = jobId;
        this.username = username;
        this.password = password;
        this.authenticator = authenticator;
        this.apiKey = apiKey;
        this.jobMessageOffset = jobMessageOffset;
        this.timeZone = timeZone;
        this.timeUnit = timeUnit;
        this.logicalOp = logicalOp;
        this.resourceConfigs = resourceConfigs;
        this.parameters = new Parameters(this);
    }

    /**
     * Constructor intended to be called by XStream only. Sets the default field
     * values, which will then be overridden if these fields exist in the
     * configuration file.
     */
    @SuppressWarnings("unused") // called reflectively by XStream
    public PollingTriglavTrigger()
            throws ANTLRException
    {
        super(getCrontabSpec());
        this.jobId = "";
        this.username = "";
        this.password = "";
        this.authenticator = "LOCAL";
        this.apiKey = "";
        this.jobMessageOffset = 0L;
        this.timeZone = "Asia/Tokyo";
        this.timeUnit = "daily";
        this.logicalOp = "or";
        this.resourceConfigs = Lists.newArrayList();
        this.parameters = new Parameters(this);
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public String getJobId()
    {
        return jobId;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public String setJobId(String jobId)
    {
        return this.jobId = jobId;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public String getUsername()
    {
        return username;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public String getPassword()
    {
        return password;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public String getAuthenticator()
    {
        return authenticator;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public String getApiKey()
    {
        return apiKey;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public synchronized void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public long getJobMessageOffset()
    {
        return jobMessageOffset;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public synchronized void setJobMessageOffset(long jobMessageOffset)
    {
        this.jobMessageOffset = jobMessageOffset;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public String getTimeZone()
    {
        return timeZone;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public String getTimeUnit()
    {
        return timeUnit;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public String getLogicalOp()
    {
        return logicalOp;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public List<TriglavResourceConfig> getResourceConfigs()
    {
        return resourceConfigs;
    }

    @Override
    public void run()
    {
        Runner.runIfPossible(this, job);
    }

    public Parameters parameters()
    {
        return parameters;
    }

    /**
     * Wrap Plugin's parameters for usability, flexibility
     * and expandability of utility methods.
     */
    class Parameters
    {
        private final PollingTriglavTrigger plugin;

        private Parameters(PollingTriglavTrigger plugin)
        {
            this.plugin = plugin;
        }

        public String triglavApiUrl()
        {
            return PollingTriglavTrigger.getTriglavApiUrl();
        }

        public String jobId()
        {
            return plugin.getJobId();
        }

        public void setJobId(String jobId)
        {
            plugin.setJobId(jobId);
        }

        public void initializeJobId()
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

        public void initializeApiKey()
        {
            setApiKey("");
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

        public void initializeJobMessageOffset()
        {
            setJobMessageOffset(0);
        }

        // Triglav execute that kind of query : SELECT  `job_messages`.* FROM `job_messages` WHERE (id >= '1') AND `job_messages`.`job_id` = 1 ORDER BY `job_messages`.`id` ASC LIMIT 1
        // So, need to set incremented id after finding job_message.
        public void setNextJobMessageOffset(long jobMessageOffset)
        {
            setJobMessageOffset(jobMessageOffset + 1L);
        }

        public String timeZone()
        {
            return ZoneIDConverter.to3LettersISO860(plugin.getTimeZone());
        }

        public String timeUnit()
        {
            return plugin.getTimeUnit();
        }

        public String logicalOp()
        {
            return plugin.getLogicalOp();
        }

        public void initializeMinimumRequired()
        {
            initializeApiKey();
            initializeJobId();
            initializeJobMessageOffset();
            initializeResourceIds();
        }

        /*
         section : TriglavResourceConfig
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

        public List<TriglavResourceConfig> resourceConfigs()
        {
            if (plugin.getResourceConfigs() == null) {
                return Lists.newArrayList();
            }
            return plugin.getResourceConfigs();
        }

        private Optional<TriglavResourceConfig> lookupResourceConfig(String resourceUri)
        {
            for (TriglavResourceConfig rConfig : resourceConfigs()) {
                if (rConfig.getResourceUri().contentEquals(resourceUri)) {
                    return Optional.of(rConfig);
                }
            }
            return Optional.absent();
        }

        public void setResourceId(String resourceUri, String resourceId)
        {
            Optional<TriglavResourceConfig> rConfig = lookupResourceConfig(resourceUri);
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

        public void initializeResourceIds()
        {
            for (TriglavResourceConfig resourceConfig : resourceConfigs()) {
                resourceConfig.setResourceId("");
            }
        }
    }

    /**
     * {@link Converter} implementation for XStream. This converter uses the
     * {≥@link PureJavaReflectionProvider}, which ensures that the default
     * constructor is called.
     */
    @SuppressWarnings("unused")
    public static final class ConverterImpl
            extends RobustReflectionConverter
    {
        /**
         * Class constructor.
         *
         * @param mapper the mapper
         */
        @SuppressWarnings("unused")
        public ConverterImpl(Mapper mapper)
        {
            super(mapper, new PureJavaReflectionProvider());
        }
    }

    /**
     * Registers {@link PollingTriglavTrigger} as a {@link Trigger} extension.
     */
    @SuppressWarnings("unused")
    @Extension
    public static final class DescriptorImpl
            extends TriggerDescriptor
    {
        private String crontabSpec = "* * * * *";
        private String triglavApiUrl = "http://localhost:7800/api/v1/";
        private String adminUsername = "";
        private String adminPassword = "";
        private String adminApiKey = "";
        private int maxEnqueueCount = 10;

        public String getCrontabSpec()
        {
            return crontabSpec;
        }

        public String getTriglavApiUrl()
        {
            return triglavApiUrl;
        }

        public String getAdminUsername()
        {
            return adminUsername;
        }

        public String getAdminPassword()
        {
            return adminPassword;
        }

        public String getAdminApiKey()
        {
            return adminApiKey;
        }

        public synchronized void setAdminApiKey(String adminApiKey)
        {
            this.adminApiKey = adminApiKey;
        }

        public int getMaxEnqueueCount()
        {
            return maxEnqueueCount;
        }

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl()
        {
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData)
                throws FormException
        {
            // To persist global configuration information,
            // set that to properties and call save().
            if (formData.getString("crontabSpec") != null) {
                crontabSpec = formData.getString("crontabSpec");
            }
            if (formData.getString("triglavApiUrl") != null) {
                triglavApiUrl = formData.getString("triglavApiUrl");
            }
            if (formData.getString("adminUsername") != null) {
                adminUsername = formData.getString("adminUsername");
            }
            if (formData.getString("adminPassword") != null) {
                adminPassword = formData.getString("adminPassword");
            }
            if (formData.getString("adminApiKey") != null) {
                adminApiKey = formData.getString("adminApiKey");
            }
            if (formData.getInt("maxEnqueueCount") > 0) {
                maxEnqueueCount = formData.getInt("maxEnqueueCount");
            }
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req, formData);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Item item)
        {
            // TODO: Change `extends Trigger<BuildableItem>` to `extends Trigger<AbstractProject>` if can
            return item instanceof AbstractProject;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName()
        {
            // TODO: https://wiki.jenkins-ci.org/display/JENKINS/Internationalization
            return "Polling Triglav Trigger";
        }

        public FormValidation doCheckPassword(@QueryParameter String username, @QueryParameter String password, @QueryParameter String authenticator)
        {
            try {
                TriglavClient client = new TriglavClient(PollingTriglavTrigger.getTriglavApiUrl(), getDefaultConfigurable());
                client.authenticate(username, password, Credential.AuthenticatorEnum.valueOf(authenticator), "");
                return FormValidation.ok();
            }
            catch (ApiException e) {
                return FormValidation.error("Unable to authorize ");
            }
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillAuthenticatorItems(@QueryParameter String authenticator)
        {
            ListBoxModel options = new ListBoxModel();
            Credential.AuthenticatorEnum[] values = Credential.AuthenticatorEnum.values();
            List<String> availableAuthenticators = Lists.transform(Lists.newArrayList(Credential.AuthenticatorEnum.values()), new Function<Credential.AuthenticatorEnum, String>()
            {
                @Nullable
                @Override
                public String apply(@Nullable Credential.AuthenticatorEnum input)
                {
                    return input.name();
                }
            });

            for (Credential.AuthenticatorEnum e : Credential.AuthenticatorEnum.values()) {
                ListBoxModel.Option option = new ListBoxModel.Option(
                        e.name(), e.name(), e.name().contentEquals(authenticator));
                options.add(option);
            }
            return options;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillTimeUnitItems(@QueryParameter String timeUnit)
        {
            ListBoxModel options = new ListBoxModel();
            String[] availableUnits = {"hourly", "daily", "singular"};
            for (String unit : availableUnits) {
                options.add(new ListBoxModel.Option(unit, unit, timeUnit.contentEquals(unit)));
            }
            return options;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillTimeZoneItems(@QueryParameter String timeZone)
        {
            ListBoxModel options = new ListBoxModel();
            List<String> availableZoneIDs = Ordering.natural().sortedCopy(Lists.newArrayList(TimeZone.getAvailableIDs()));
            for (String zoneID : availableZoneIDs) {
                options.add(new ListBoxModel.Option(zoneID, zoneID, timeZone.contentEquals(zoneID)));
            }
            return options;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillLogicalOpItems(@QueryParameter String logicalOp)
        {
            ListBoxModel options = new ListBoxModel();
            options.add(new ListBoxModel.Option("or", "or", "or".contentEquals(logicalOp)));
            options.add(new ListBoxModel.Option("and", "and", "and".contentEquals(logicalOp)));
            return options;
        }
    }
}