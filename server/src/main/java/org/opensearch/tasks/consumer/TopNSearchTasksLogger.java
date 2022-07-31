/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.tasks.consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchShardTask;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.logging.Loggers;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.tasks.ResourceStats;
import org.opensearch.tasks.Task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A simple listener that logs resource information of high memory consuming search tasks
 */
public class TopNSearchTasksLogger implements Consumer<Task> {
    public static final String TASK_DETAILS_LOG_PREFIX = "task.detailslog";
    public static final String LOG_TOP_QUERIES_SIZE = "cluster.task.consumers.topn.size";
    public static final String LOG_TOP_QUERIES_FREQUENCY = "cluster.task.consumers.topn.frequency";

    private static final Logger SEARCH_TASK_DETAILS_LOGGER = LogManager.getLogger(TASK_DETAILS_LOG_PREFIX + ".search");

    // number of memory expensive search tasks that are logged
    private static final Setting<Integer> LOG_TOP_QUERIES_SIZE_SETTING = Setting.intSetting(
        LOG_TOP_QUERIES_SIZE,
        10,
        Setting.Property.Dynamic,
        Setting.Property.NodeScope
    );

    // frequency in which memory expensive search tasks are logged
    private static final Setting<TimeValue> LOG_TOP_QUERIES_FREQUENCY_SETTING = Setting.timeSetting(
        LOG_TOP_QUERIES_FREQUENCY,
        TimeValue.timeValueSeconds(60L),
        Setting.Property.Dynamic,
        Setting.Property.NodeScope
    );

    private final ScheduledExecutorService logExecutor = Executors.newSingleThreadScheduledExecutor(
        OpenSearchExecutors.daemonThreadFactory("topNlogger")
    );
    private final int topQueriesSize;
    private final PriorityBlockingQueue<Tuple<Long, Task>> topQueries1;
    private final PriorityBlockingQueue<Tuple<Long, Task>> topQueries2;

    private final AtomicReference<Queue<Tuple<Long, Task>>> activeQueue = new AtomicReference<>();

    public TopNSearchTasksLogger(Settings settings) {
        this.topQueriesSize = LOG_TOP_QUERIES_SIZE_SETTING.get(settings);
        this.topQueries1 = new PriorityBlockingQueue<>(topQueriesSize, Comparator.comparingLong(Tuple::v1));
        this.topQueries2 = new PriorityBlockingQueue<>(topQueriesSize, Comparator.comparingLong(Tuple::v1));
        Loggers.setLevel(SEARCH_TASK_DETAILS_LOGGER, "info");
        activeQueue.set(topQueries1);

        ScheduledFuture<?> scheduledFuture = logExecutor.scheduleAtFixedRate(
            this::publishTopNEvents,
            TimeValue.timeValueSeconds(60L).getNanos(),
            LOG_TOP_QUERIES_FREQUENCY_SETTING.get(settings).getNanos(),
            TimeUnit.NANOSECONDS
        );
    }

    /**
     * Called when task is unregistered and task has resource stats present.
     */
    @Override
    public void accept(Task task) {
        if (task instanceof SearchShardTask) {
            recordSearchTask(task);
        }
    }

    private void recordSearchTask(Task searchTask) {
        final long memory_in_bytes = searchTask.getTotalResourceUtilization(ResourceStats.MEMORY);
        synchronized (activeQueue) {
            Queue<Tuple<Long, Task>> currentActiveQueue = activeQueue.get();
            if (currentActiveQueue.size() >= topQueriesSize) {
                if (currentActiveQueue.peek() != null && currentActiveQueue.peek().v1() < memory_in_bytes) {
                    // evict the element
                    currentActiveQueue.poll();
                }
            }
            if (currentActiveQueue.size() < topQueriesSize) {
                currentActiveQueue.offer(new Tuple<>(memory_in_bytes, searchTask));
            }
        }
    }

    private void publishTopNEvents() {
        List<Tuple<Long, Task>> elementsToLog = new ArrayList<>();
        if (activeQueue.compareAndSet(topQueries1, topQueries2)) {
            topQueries1.drainTo(elementsToLog);
            logTopResourceConsumingQueries(elementsToLog);
        } else if (activeQueue.compareAndSet(topQueries2, topQueries1)) {
            topQueries2.drainTo(elementsToLog);
            logTopResourceConsumingQueries(elementsToLog);
        }
    }

    private void logTopResourceConsumingQueries(List<Tuple<Long, Task>> logTasks) {
        for (Tuple<Long, Task> topQuery : logTasks) {
            SEARCH_TASK_DETAILS_LOGGER.info(new TaskDetailsLogMessage(topQuery.v2()));
        }
    }
}
