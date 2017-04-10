package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.BuildableItem;
import hudson.model.Item;
import io.github.triglav_dataflow.client.ApiException;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.apache.commons.lang.StringUtils.isBlank;

@Extension
public class ItemListener
    extends hudson.model.listeners.ItemListener
{
    private static final Logger logger = PollingTriglavTrigger.getLogger();
    /**
     *  ConcurrentHashMap<String, String> = {JenkinsJobFullName, TriglavJobId}
     *
     *  This ConcurrentHashMap is used for managing all jenkins job which has this plugin.
     *  It's because triglav job cannot be deleted if renaming and deleting are done at the same time.
     */
    private static final ConcurrentHashMap<String, String> tJobMap = new ConcurrentHashMap<>();

    /**
     * Called after a new job is created by copying from an existing job.
     * <p>
     * For backward compatibility, the default implementation of this method calls {@link #onCreated(Item)}.
     * If you choose to handle this method, think about whether you want to call super.onCopied or not.
     *
     * @param src The source item that the new one was copied from. Never null.
     * @param item The newly created item. Never null.
     * @since 1.325
     * Before this version, a copy triggered {@link #onCreated(Item)}.
     */
    @Override
    public void onCopied(Item src, Item item)
    {
        super.onCopied(src, item);

        if (!isProject(item) || !hasPollingTriglavTrigger(item)) {
            return;
        }

        logger.fine(String.format("Copy Job %s to %s", src.getFullName(), item.getFullName()));
        PollingTriglavTrigger t = getTrigger(item);
        t.parameters().initializeMinimumRequired();
        try {
            item.save();
        }
        catch (IOException e) {
            logger.throwing(getClass().getName(), "onCopied", e);
            throw new RuntimeException(
                    String.format(
                            "Job: %s, Job ID: %s, Error: %s",
                            item.getFullName(), t.parameters().jobId(), e.getMessage()
                    ), e);
        }
    }

    /**
     * Called right before a job is going to be deleted.
     * <p>
     * At this point the data files of the job is already gone.
     *
     * @param item
     */
    @Override
    public void onDeleted(Item item)
    {
        super.onDeleted(item);

        if (tJobMap.containsKey(item.getFullName())) {
            String jobId = tJobMap.get(item.getFullName());

            logger.fine(String.format("Job: %s Job ID: %s is deleted from Triglav.",
                    item.getFullName(), jobId));

            try {
                unregisterJobFromTriglav(jobId);
            }
            catch (ApiException e) {
                logger.throwing(getClass().getName(), "onDeleted", e);
                throw new RuntimeException(
                        String.format(
                                "Job: %s, Job ID: %s, Error: %s",
                                item.getFullName(), jobId, e.getMessage()
                        ), e);
            }
            tJobMap.remove(item.getFullName());
        }
    }

    /**
     * Called after an item’s fully-qualified location has changed.
     * This might be because:
     * <ul>
     * <li>This item was renamed.
     * <li>Some ancestor folder was renamed.
     * <li>This item was moved between folders (or from a folder to Jenkins root or vice-versa).
     * <li>Some ancestor folder was moved.
     * </ul>
     * Where applicable, {@link #onRenamed} will already have been called on this item or an ancestor.
     * And where applicable, {@link #onLocationChanged} will already have been called on its ancestors.
     * <p>This method should be used (instead of {@link #onRenamed}) by any code
     * which seeks to keep (absolute) references to tItems up to date:
     * if a persisted reference matches {@code oldFullName}, replace it with {@code newFullName}.
     *
     * @param item an item whose absolute position is now different
     * @param oldFullName the former {@link Item#getFullName}
     * @param newFullName the current {@link Item#getFullName}
     * @since 1.548
     */
    @Override
    public void onLocationChanged(Item item, String oldFullName, String newFullName)
    {
        super.onLocationChanged(item, oldFullName, newFullName);

        if (tJobMap.containsKey(oldFullName)) {
            logger.fine(String.format("Job: %s Job ID: %s is location changed to %s.",
                    oldFullName, tJobMap.get(oldFullName), newFullName));
            String jobId = tJobMap.remove(oldFullName);
            tJobMap.put(newFullName, jobId);
        }

        onUpdated(item); // Re-register to Triglav if needed.
    }

    /**
     * Called after a job has its configuration updated.
     *
     * @param item
     * @since 1.460
     */
    @Override
    public void onUpdated(Item item)
    {
        super.onUpdated(item);

        /*
         * case 1: Already registered on Triglav, the job url is the same as registered.
         * case 2: Already registered on Triglav, the job url is different from registered.
         *   case A: Copy by the job.
         *     #onCopied is called before #onUpdated, so just register same as case 4.
         *   case B: Rename the job url.
         *     #onLocationChanged is called before #onUpdated, so just update the job url registered on Triglav.
         * case 3: Already registered on Triglav, but the job doesn't applied this plugin.
         *   case A: Just remove this plugin.
         *   case B: Remove this plugin and rename the job url.
         *     #onLocationChanged is called before #onUpdated, and update tJobMap key to new job name in #onLocationChanged.
         * case 4: Not registered on Triglav.
         * case 5: Job is disabled.
         */

        if (!isProject(item) || !hasPollingTriglavTrigger(item)) {
            if (tJobMap.containsKey(item.getFullName())) {
                String jobId = tJobMap.get(item.getFullName());

                // case 3A or case 3B
                logger.fine(String.format("Trigger is removed from %s. Triglav job id was %s.",
                        item.getFullName(), jobId));

                try {
                    unregisterJobFromTriglav(jobId);
                }
                catch (ApiException e) {
                    logger.throwing(getClass().getName(), "onUpdated", e);
                    throw new RuntimeException(
                            String.format(
                                    "Job: %s, Job ID: %s, Error: %s",
                                    item.getFullName(), jobId, e.getMessage()
                            ), e);
                }
                tJobMap.remove(item.getFullName());
            }
            return;
        }

        JenkinsJob jenkinsJob = createJenkinsJob(item);
        PollingTriglavTrigger t = getTrigger(item);
        if (jenkinsJob.isDisabled()) {
            // case 5
            onDeleted(item);
            t.parameters().initializeMinimumRequired();
            try {
                jenkinsJob.save();
            }
            catch (IOException e) {
                logger.throwing(getClass().getName(), "onUpdated", e);
                throw new RuntimeException(
                        String.format(
                                "Job: %s, Job ID: %s, Error: %s",
                                item.getFullName(), t.parameters().jobId(), e.getMessage()
                        ), e);
            }
            return;
        }

        boolean isRegistering = t.parameters().jobId() == null || t.parameters().jobId().isEmpty();

        if (isRegistering) {
            // case 2A or case 4
            logger.fine(String.format("Register Job: %s as a new Triglav Job.", item.getFullName()));
        }
        else {
            // case 2B or case 1
            logger.fine(String.format("Update Job: %s.", item.getFullName()));
        }

        try {
            TriglavJob triglavJob = createTriglavJob(t.parameters());
            triglavJob.registerOrUpdate(jenkinsJob);
            jenkinsJob.save();
        }
        catch (ApiException | IOException e) {
            logger.throwing(getClass().getName(), "onUpdated", e);
            throw new RuntimeException(
                    String.format(
                            "Job: %s, Job ID: %s, Error: %s",
                            item.getFullName(), t.parameters().jobId(), e.getMessage()
                    ), e);
        }

        if (isRegistering) {
            // case 2A or case 4
            logger.fine(String.format("Registered Job: %s as a Triglav Job ID: %s.",
                    item.getFullName(), t.parameters().jobId()));
            tJobMap.put(item.getFullName(), t.parameters().jobId());
        }
        else {
            // case 2B or case 1
            logger.fine(String.format("Updated Job: %s as a Triglav Job ID: %s.",
                    item.getFullName(), t.parameters().jobId()));
            if (!tJobMap.containsKey(item.getFullName())) {
                logger.fine(String.format(
                        "Updated Job ID: from %s to %s",
                        tJobMap.get(item.getFullName()),
                        t.parameters().jobId()));
                tJobMap.put(item.getFullName(), t.parameters().jobId());
            }
            else if (!tJobMap.get(item.getFullName()).contentEquals(t.parameters().jobId())) {
                logger.fine(String.format(
                        "Updated Job ID: from %s to %s",
                        tJobMap.get(item.getFullName()),
                        t.parameters().jobId()));
                tJobMap.replace(item.getFullName(), t.parameters().jobId());
            }
        }
    }

    /**
     * Called after all the jobs are loaded from disk into {@link jenkins.model.Jenkins}
     * object.
     */
    public void onLoaded()
    {
        super.onLoaded();

        tJobMap.clear();

        for (Item item : jenkins().getAllItems()) {
            if (!isProject(item) || !hasPollingTriglavTrigger(item)) {
                continue;
            }

            PollingTriglavTrigger t = getTrigger(item);
            String jobId = t.parameters().jobId();
            if (jobId == null || jobId.isEmpty()) {
                // Not be registered to Triglav Server.
                return;
            }
            logger.fine(String.format("Find Job: %s, Triglav Job ID: %s.",
                    item.getFullName(), jobId));

            if (tJobMap.containsKey(item.getFullName())) {
                logger.warning(String.format("Already registered Job: %s, Triglav Job ID: %s",
                        item.getFullName(), jobId));
            }

            tJobMap.put(item.getFullName(), jobId);
        }
    }

    /**
     * @since 1.446
     *      Called at the begenning of the orderly shutdown sequence to
     *      allow plugins to clean up stuff
     *
     * NOTE: This method called without log messages if Jenkins is unexpectedly stopped.
     */
    @Override
    public void onBeforeShutdown()
    {
        super.onBeforeShutdown();

        /*
         * In order to unregister the missing job among the Triglav jobs that were registered in this Jenkins in the past,
         * first, delete the job registered in Jenkins from the correspondence table of Jenkins job and Triglav job managed by ItemListener.
         * After that, delete jobs left in the correspondence table from Triglav.
         */
        for (Item item : jenkins().getAllItems()) {
            if (!isProject(item) || !hasPollingTriglavTrigger(item)) {
                continue;
            }

            PollingTriglavTrigger t = getTrigger(item);
            if (!tJobMap.containsKey(item.getFullName())) {
                // new Job case
                if (isBlank(t.parameters().jobId())) {
                    logger.fine(String.format(
                            "Job has the trigger, but the job is not registered to Triglav: Job: %s",
                            item.getFullName()));
                    continue;
                }

                // Illegal State case
                logger.warning(String.format(
                        "Illegal state: Job has the trigger, but the job is not managed by ItemListener: Job: %s, Triglav Job ID: %s",
                        item.getFullName(), t.parameters().jobId()));

                onUpdated(item); // Register Triglav
            }

            String jobId = tJobMap.remove(item.getFullName());
            if (isBlank(jobId) && !isBlank(t.parameters().jobId())) {
                logger.warning(String.format(
                        "Illegal state: Job has the trigger, but the job id is different from ItemListener managed: Job: %s, Triglav Job ID: %s",
                        item.getFullName(), t.parameters().jobId()));
                continue;
            }

            if (!jobId.contentEquals(t.parameters().jobId())) {
                logger.warning(String.format(
                        "Illegal state: Job: %s, Triglav Job ID: %s, but ItemLister manages Job ID: %s.",
                        item.getFullName(), t.parameters().jobId(), jobId));
            }
        }

        for (Map.Entry<String, String> entry : tJobMap.entrySet()) {
            logger.warning(String.format(
                    "Unregister Job: %s, Triglav Job ID: %s because the job should had been already unregistered.",
                    entry.getKey(), entry.getValue()));
            try {
                unregisterJobFromTriglav(entry.getValue());
            }
            catch (ApiException e) {
                logger.throwing(getClass().getName(), "onUpdated", e);
                throw new RuntimeException(
                        String.format(
                                "Job: %s, Job ID: %s, Error: %s",
                                entry.getKey(), entry.getValue(), e.getMessage()
                        ), e);
            }
        }
    }

    private void unregisterJobFromTriglav(String jobId)
            throws ApiException
    {
        TriglavClient c = TriglavClient.fromTriggerAdminParameter();
        c.unregisterJob(jobId);
    }

    private TriglavJob createTriglavJob(PollingTriglavTrigger.Parameters parameters)
            throws ApiException
    {
        return new TriglavJob(parameters, TriglavClient.fromTriggerParameter(parameters));
    }

    private JenkinsJob createJenkinsJob(Item item)
    {
        return new JenkinsJob((BuildableItem) item);
    }

    private PollingTriglavTrigger getTrigger(Item item)
    {
        return (PollingTriglavTrigger) ((AbstractProject) item).getTrigger(PollingTriglavTrigger.class);
    }

    private boolean hasPollingTriglavTrigger(Item item)
    {
        return getTrigger(item) != null;
    }

    /**
     * Only AbstractProject can have trigger plugins
     * @param item
     * @return
     */
    private boolean isProject(Item item)
    {
        return item instanceof AbstractProject;
    }

    private Jenkins jenkins()
    {
        return Jenkins.getInstance();
    }
}
