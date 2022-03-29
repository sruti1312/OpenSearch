/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.rest.action.cat;

import org.opensearch.OpenSearchParseException;
import org.opensearch.Version;
import org.opensearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.opensearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.opensearch.action.admin.cluster.state.ClusterStateResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.common.settings.Settings;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.junit.Before;

import java.util.Collections;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RestNodesActionTests extends OpenSearchTestCase {

    private RestNodesAction action;

    @Before
    public void setUpAction() {
        action = new RestNodesAction();
    }

    public void testBuildTableDoesNotThrowGivenNullNodeInfoAndStats() {
        ClusterName clusterName = new ClusterName("cluster-1");
        DiscoveryNodes.Builder builder = DiscoveryNodes.builder();
        builder.add(new DiscoveryNode("node-1", buildNewFakeTransportAddress(), emptyMap(), emptySet(), Version.CURRENT));
        DiscoveryNodes discoveryNodes = builder.build();
        ClusterState clusterState = mock(ClusterState.class);
        when(clusterState.nodes()).thenReturn(discoveryNodes);

        ClusterStateResponse clusterStateResponse = new ClusterStateResponse(clusterName, clusterState, false);
        NodesInfoResponse nodesInfoResponse = new NodesInfoResponse(clusterName, Collections.emptyList(), Collections.emptyList());
        NodesStatsResponse nodesStatsResponse = new NodesStatsResponse(clusterName, Collections.emptyList(), Collections.emptyList());

        action.buildTable(false, new FakeRestRequest(), clusterStateResponse, nodesInfoResponse, nodesStatsResponse);
    }

    public void testCatNodesWithLocalDeprecationWarning() {
        TestThreadPool threadPool = new TestThreadPool(RestNodesActionTests.class.getName());
        NodeClient client = new NodeClient(Settings.EMPTY, threadPool);
        FakeRestRequest request = new FakeRestRequest();
        request.params().put("local", randomFrom("", "true", "false"));

        action.doCatRequest(request, client);
        assertWarnings(RestNodesAction.LOCAL_DEPRECATED_MESSAGE);

        terminate(threadPool);
    }

    /**
     * Validate both cluster_manager_timeout and its predecessor can be parsed correctly.
     * Remove the test along with MASTER_ROLE. It's added in version 2.0.0.
     */
    public void testCatNodesWithClusterManagerTimeout() {
        TestThreadPool threadPool = new TestThreadPool(RestNodesActionTests.class.getName());
        NodeClient client = new NodeClient(Settings.EMPTY, threadPool);
        FakeRestRequest request = new FakeRestRequest();
        request.params().put("cluster_manager_timeout", randomFrom("1h", "2m"));
        request.params().put("master_timeout", "3s");
        Exception e = assertThrows(OpenSearchParseException.class, () -> action.doCatRequest(request, client));
        assertThat(e.getMessage(), containsString("[master_timeout, cluster_manager_timeout] are required to be equal"));
        assertWarnings(RestNodesAction.MASTER_TIMEOUT_DEPRECATED_MESSAGE);
        terminate(threadPool);
    }
}
