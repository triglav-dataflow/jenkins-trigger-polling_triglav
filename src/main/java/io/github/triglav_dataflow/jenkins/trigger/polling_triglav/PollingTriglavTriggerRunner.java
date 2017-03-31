package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import hudson.model.BuildableItem;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class PollingTriglavTriggerRunner
{
    private PollingTriglavTriggerRunner()
    {
    }

    private static Logger logger = PollingTriglavTriggerPlugin.getLogger();
    private static ListeningExecutorService createThreadPool(int numThreads)
    {
        return MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(numThreads));
    }
    // TODO: make num threads configurable
    private static final ListeningExecutorService runnerPool = createThreadPool(100);

    public static void runIfRunnable(final PollingTriglavTriggerPlugin plugin, final BuildableItem job)
    {
        final ListenableFuture<?> future = runnerPool.submit(new Runnable()
        {
            @Override
            public void run()
            {
                JenkinsJob jobWrapper = new JenkinsJob(job);
                PluginParameter parameterWrapper = new PluginParameter(plugin);
                TriglavPoller poller = new TriglavPoller(parameterWrapper);
                PollingTriglavTrigger trigger = new PollingTriglavTrigger(jobWrapper, poller);
                if (trigger.canKick()) {
                    logger.info(String.format("[%s] Job `%s` is started by io.github.triglav_dataflow.jenkins.trigger.polling_triglav", getClass().getName(), job.getDisplayName()));
                    trigger.kick();
                }
                else {
                    logger.warning(String.format("[%s] Job `%s` is skipped.", getClass().getName(), job.getDisplayName()));
                }
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

    // TODO: require shutdownNow method either?
    public static void shutdown()
    {
        if (!runnerPool.isShutdown()) {
            runnerPool.shutdown();
        }
    }
}
