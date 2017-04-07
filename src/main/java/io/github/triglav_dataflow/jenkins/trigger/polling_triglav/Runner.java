package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import hudson.model.BuildableItem;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class Runner
{
    private Runner()
    {
    }

    private static Logger logger = PollingTriglavTrigger.getLogger();

    private static ListeningExecutorService createThreadPool(int numThreads)
    {
        return MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(numThreads));
    }
    // TODO: make num threads configurable
    private static final ListeningExecutorService runnerPool = createThreadPool(100);

    public static void runIfPossible(final PollingTriglavTrigger plugin, final BuildableItem job)
    {
        final ListenableFuture<?> future = runnerPool.submit(new Runnable()
        {
            @Override
            public void run()
            {
                JenkinsJob jenkinsJob = new JenkinsJob(job);
                if (jenkinsJob.isDisabled()) {
                    // no log
                    return;
                }
                if (jenkinsJob.isBuildBlocked()) {
                    logger.fine(String.format("Job: %s is blocked.", jenkinsJob.name()));
                    return;
                }

                TriglavClient triglavClient = TriglavClient.fromTriggerParameter(plugin.parameters());
                TriglavJob triglavJob = new TriglavJob(plugin.parameters(), triglavClient);

                if (!triglavJob.hasId()) {
                    logger.fine(String.format(
                            "Job: %s is skipped because Job does not have Triglav Job ID.",
                            jenkinsJob.name()));
                    return;
                }

                int i = 0;
                while (triglavJob.poll()) {
                    i++;
                    logger.fine(String.format("Enqueue Job %s. Enqueue count %d.", jenkinsJob.name(), i));
                    jenkinsJob.setBuildParameters(triglavJob.currentMassage());
                    jenkinsJob.build();
                    jenkinsJob.save();
                    if (i >= PollingTriglavTrigger.getMaxEnqueueCount()) {
                        logger.fine(String.format("Max enqueue count %d is exceeded. Wait until next enqueue chance.", i));
                        break;
                    }
                }
                if (i > 0) {
                    return;
                }

                logger.fine(String.format(
                        "Job: %s is skipped because of no consumable messages larger than %s.",
                        jenkinsJob.name(),
                        triglavJob.messageOffset()));
            }
        });

        future.addListener(new Runnable()
        {
            @Override
            public void run()
            {
                while (true) {
                    if (future.isDone() || future.isCancelled()) {
                        try {
                            future.get();
                            break;
                        }
                        catch (InterruptedException | ExecutionException e) {
                            throw Throwables.propagate(e);
                        }
                    }
                    Thread.yield();
                }
            }
        }, runnerPool);
    }

    public static void shutdown()
    {
        if (!runnerPool.isShutdown()) {
            runnerPool.shutdown();
        }
    }
}
