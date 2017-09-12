package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import hudson.model.BuildableItem;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class ItemRunner
{
    private ItemRunner()
    {
    }

    private static ListeningExecutorService createThreadPool(int numThreads)
    {
        return MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(numThreads));
    }
    // TODO: make num threads configurable
    private static final ListeningExecutorService runnerPool = createThreadPool(100);

    public static void runIfPossible(final PollingTriglavTrigger plugin, final BuildableItem job)
    {
        final ListenableFuture<?> future = runnerPool.submit(new ItemRunnable(plugin, job));

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
                            throw new RuntimeException(String.format("Error: %s", e.getMessage()), e);
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
