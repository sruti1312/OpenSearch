/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.tasks;

import java.util.HashMap;
import java.util.Map;

public class TaskResourceStatsUtil {
    public static final String TOTAL_RESOURCE_CONSUMPTION = "total_resource_consumption";
    private final Map<TaskStatsType, TaskResourceStatsHelper> resourceUtil = new HashMap<>();
    private boolean isActive;

    public TaskResourceStatsUtil(boolean isActive, TaskStatsType statsType, TaskResourceMetric... taskResourceMetrics) {
        this.isActive = isActive;
        this.resourceUtil.put(statsType, new TaskResourceStatsHelper(taskResourceMetrics));
    }

    public boolean isActive() {
        return isActive;
    }

    public Map<TaskStatsType, TaskResourceStatsHelper> getResourceUtil() {
        return resourceUtil;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public void update(boolean isActive, TaskStatsType statsName, TaskResourceMetric... taskResourceMetrics) {
        TaskResourceStatsHelper resourceStats = resourceUtil.get(statsName);
        if (resourceStats == null) {
            resourceUtil.put(statsName, new TaskResourceStatsHelper(taskResourceMetrics));
        } else {
            resourceStats.updateStatsInfo(taskResourceMetrics);
        }
        setActive(isActive);
    }

    /*
    Helper class that contains the task stats information.
     */
    public static class TaskResourceStatsHelper {
        private final Map<TaskStats, TaskResourceMetric> statsInfo = new HashMap<>();

        public TaskResourceStatsHelper(TaskResourceMetric... taskResourceMetrics) {
            for (TaskResourceMetric taskResourceMetric : taskResourceMetrics) {
                statsInfo.put(taskResourceMetric.getStats(), taskResourceMetric);
            }
        }

        public Map<TaskStats, TaskResourceMetric> getStatsInfo() {
            return statsInfo;
        }

        public void updateStatsInfo(TaskResourceMetric... taskResourceMetrics) {
            for (TaskResourceMetric metric : taskResourceMetrics) {
                statsInfo.put(metric.getStats(), metric);
            }
        }
    }
}
