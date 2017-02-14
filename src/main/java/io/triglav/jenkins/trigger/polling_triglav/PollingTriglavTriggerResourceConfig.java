package io.triglav.jenkins.trigger.polling_triglav;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.mapper.Mapper;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import hudson.util.RobustReflectionConverter;
import io.triglav.jenkins.trigger.polling_triglav.unit.TimeUnit;
import io.triglav.jenkins.trigger.polling_triglav.unit.TimeZoneConverter;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.TimeZone;

public class PollingTriglavTriggerResourceConfig
        implements Describable<PollingTriglavTriggerResourceConfig>
{
    public static DescriptorImpl getClassDescriptor()
    {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(
                PollingTriglavTriggerResourceConfig.class);
    }

    @Override
    public Descriptor<PollingTriglavTriggerResourceConfig> getDescriptor()
    {
        return getClassDescriptor();
    }

    private String resourceId;
    private final String resourceUri;

    @DataBoundConstructor
    public PollingTriglavTriggerResourceConfig(String resourceId, String resourceUri)
    {
        this.resourceId = resourceId;
        this.resourceUri = resourceUri;
    }

    /**
     * Constructor intended to be called by XStream only. Sets the default field
     * values, which will then be overridden if these fields exist in the
     * configuration file.
     */
    @SuppressWarnings("unused") // called reflectively by XStream
    public PollingTriglavTriggerResourceConfig()
    {
        this.resourceId = "";
        this.resourceUri = "";
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public String getResourceId()
    {
        return resourceId;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public synchronized void setResourceId(String resourceId)
    {
        this.resourceId = resourceId;
    }

    @SuppressWarnings("unused") // called reflectively by XStream
    public String getResourceUri()
    {
        return resourceUri;
    }

    /**
     * {@link Converter} implementation for XStream. This converter uses the
     * {@link PureJavaReflectionProvider}, which ensures that the default
     * constructor is called.
     */
    @SuppressWarnings("unused")
    public static final class ConverterImpl
            extends RobustReflectionConverter
    {

        /**
         * Class constructor.
         *
         * @param mapper
         *          the mapper
         */
        @SuppressWarnings("unused")
        public ConverterImpl(Mapper mapper) {
            super(mapper, new PureJavaReflectionProvider());
        }
    }

    @Extension
    public static final class DescriptorImpl
            extends Descriptor<PollingTriglavTriggerResourceConfig>
    {
        public DescriptorImpl()
        {
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            save();
            return super.configure(req, formData);
        }

        @Override
        public String getDisplayName()
        {
            // Not used.
            return "";
        }
    }
}
