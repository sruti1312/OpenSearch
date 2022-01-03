/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.tasks;

import org.opensearch.common.ParseField;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ConstructingObjectParser;
import org.opensearch.common.xcontent.ToXContentFragment;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.opensearch.common.xcontent.ConstructingObjectParser.constructorArg;
import static org.opensearch.tasks.TaskResourceStats.Fields.RESOURCE_STATS;
import static org.opensearch.tasks.TaskResourceStats.Fields.STATS_TYPE;

public class TaskResourceStats implements Writeable, ToXContentFragment {
    public final String taskStatType;
    public final Map<String, Long> resourceStats;

    public TaskResourceStats(String taskStatType, Map<String, Long> resourceStats) {
        this.taskStatType = taskStatType;
        if (resourceStats != null) {
            this.resourceStats = new HashMap<>(resourceStats);
        } else {
            this.resourceStats = Collections.emptyMap();
        }
    }

    public TaskResourceStats(StreamInput in) throws IOException {
        this.taskStatType = in.readString();
        this.resourceStats = in.readMap(StreamInput::readString, StreamInput::readLong);
    }

    public String getTaskStatType() {
        return taskStatType;
    }

    public Map<String, Long> getResourceStats() {
        return resourceStats;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(taskStatType);
        out.writeMap(resourceStats, StreamOutput::writeString, StreamOutput::writeLong);
    }

    static final class Fields {
        static final String STATS_TYPE = "stats_type";
        static final String RESOURCE_STATS = "resource_stats";
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field(STATS_TYPE, taskStatType);
        if (resourceStats.size() > 0) {
            builder.startObject("resource_stats");
            for (Map.Entry<String, Long> attribute : resourceStats.entrySet()) {
                builder.field(attribute.getKey(), attribute.getValue());
            }
            builder.endObject();
        }
        return builder;
    }

    @SuppressWarnings("unchecked")
    public static final ConstructingObjectParser<TaskResourceStats, Void> PARSER = new ConstructingObjectParser<>(
        "task_resource_stats",
        a -> new TaskResourceStats((String) a[0], (Map<String, Long>) a[1])
    );

    static {
        PARSER.declareString(constructorArg(), new ParseField(STATS_TYPE));
        PARSER.declareObject(constructorArg(), (p, c) -> p.map(), new ParseField(RESOURCE_STATS));
    }

    // Implements equals and hashcode for testing
    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != TaskResourceStats.class) {
            return false;
        }
        TaskResourceStats other = (TaskResourceStats) obj;
        return other.taskStatType.equals(this.taskStatType) && other.resourceStats.equals(this.resourceStats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskStatType, resourceStats);
    }
}
