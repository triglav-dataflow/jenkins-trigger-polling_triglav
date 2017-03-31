package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import antlr.ANTLRException;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.mapper.Mapper;
import hudson.Extension;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.ListBoxModel;
import hudson.util.RobustReflectionConverter;
import io.github.triglav_dataflow.client.Credential;
import io.github.triglav_dataflow.jenkins.trigger.polling_triglav.unit.TimeUnit;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;

public class PollingTriglavTriggerPlugin
        extends Trigger<BuildableItem>
{
    public static DescriptorImpl getClassDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(PollingTriglavTriggerPlugin.class);
    }

    public static String getCrontabSpec()
    {
        return getClassDescriptor().getCrontabSpec();
    }

    public static String getTriglavApiUrl()
    {
        return getClassDescriptor().getTriglavApiUrl();
    }

    @Override
    public DescriptorImpl getDescriptor()
    {
        return getClassDescriptor();
    }

    private static final Logger logger = Logger.getLogger(PollingTriglavTriggerPlugin.class.getName());
    private String jobId;
    private final String username;
    private final String password;
    private final String authenticator;
    private String apiKey;
    private long jobMessageOffset;
    private final String timeZone;
    private final String timeUnit;
    private final String logicalOp;
    private final List<PollingTriglavTriggerResourceConfig> resourceConfigs;

    static Logger getLogger()
    {
        return logger;
    }

    @DataBoundConstructor
    public PollingTriglavTriggerPlugin(
            String jobId,
            String username,
            String password,
            String authenticator,
            String apiKey,
            long jobMessageOffset,
            String timeZone,
            String timeUnit,
            String logicalOp,
            List<PollingTriglavTriggerResourceConfig> resourceConfigs)
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
    }

    /**
     * Constructor intended to be called by XStream only. Sets the default field
     * values, which will then be overridden if these fields exist in the
     * configuration file.
     */
    @SuppressWarnings("unused") // called reflectively by XStream
    public PollingTriglavTriggerPlugin()
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
    public String getTimeZone() {
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
    public List<PollingTriglavTriggerResourceConfig> getResourceConfigs()
    {
        return resourceConfigs;
    }

    @Override
    public void run()
    {
        PollingTriglavTriggerRunner.runIfRunnable(this, job);
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
     * Registers {@link PollingTriglavTriggerPlugin} as a {@link Trigger} extension.
     */
    @SuppressWarnings("unused")
    @Extension
    public static final class DescriptorImpl
            extends TriggerDescriptor
    {
        private String crontabSpec = "* * * * *";
        private String triglavApiUrl = "http://localhost:7800/api/v1/";

        public String getCrontabSpec()
        {
            return crontabSpec;
        }

        public String getTriglavApiUrl()
        {
            return triglavApiUrl;
        }

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            if (formData.getString("crontabSpec") != null) {
                crontabSpec = formData.getString("crontabSpec");
            }
            if (formData.getString("triglavApiUrl") != null) {
                triglavApiUrl = formData.getString("triglavApiUrl");
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
        public boolean isApplicable(Item item) {
            return item instanceof BuildableItem;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            // TODO: https://wiki.jenkins-ci.org/display/JENKINS/Internationalization
            return "Polling Triglav Trigger";
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillAuthenticatorItems(@QueryParameter String authenticator)
        {
            ListBoxModel options = new ListBoxModel();
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
            for (TimeUnit unit : TimeUnit.values()) {
                ListBoxModel.Option option = new ListBoxModel.Option(
                        unit.toString(), unit.toString(), timeUnit.contentEquals(unit.toString()));
                options.add(option);
            }
            return options;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillTimeZoneItems(@QueryParameter String timeZone)
        {
            ListBoxModel options = new ListBoxModel();
            for (String zoneID : Ordering.natural().sortedCopy(Lists.newArrayList(TimeZone.getAvailableIDs()))) {
                ListBoxModel.Option option = new ListBoxModel.Option(
                        zoneID, zoneID, timeZone.contentEquals(zoneID));
                options.add(option);
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