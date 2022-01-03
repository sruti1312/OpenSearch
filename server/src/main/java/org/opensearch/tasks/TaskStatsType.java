/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.tasks;

public enum TaskStatsType {
    WORKER_STATS("worker_stats", false),
    // Used for indicating certain operator resource consumption for each worker
    OPERATION_STATS("operation_stats", true);

    private final String value;
    private final boolean onlyForAnalysis;

    TaskStatsType(String value, boolean onlyForAnalysis) {
        this.value = value;
        this.onlyForAnalysis = onlyForAnalysis;
    }

    @Override
    public String toString() {
        return value;
    }

    public boolean isOnlyForAnalysis() {
        return onlyForAnalysis;
    }
}
