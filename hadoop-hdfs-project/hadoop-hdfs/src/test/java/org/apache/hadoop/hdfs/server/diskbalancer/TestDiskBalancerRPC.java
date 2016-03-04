/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.hadoop.hdfs.server.diskbalancer;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.datanode.DiskBalancerWorkStatus;
import org.apache.hadoop.hdfs.server.diskbalancer.connectors.ClusterConnector;
import org.apache.hadoop.hdfs.server.diskbalancer.connectors.ConnectorFactory;
import org.apache.hadoop.hdfs.server.diskbalancer.datamodel.DiskBalancerCluster;
import org.apache.hadoop.hdfs.server.diskbalancer.datamodel.DiskBalancerDataNode;
import org.apache.hadoop.hdfs.server.diskbalancer.planner.GreedyPlanner;
import org.apache.hadoop.hdfs.server.diskbalancer.planner.NodePlan;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.apache.hadoop.hdfs.server.datanode.DiskBalancerWorkStatus.Result.NO_PLAN;
import static org.apache.hadoop.hdfs.server.datanode.DiskBalancerWorkStatus.Result.PLAN_DONE;
import static org.apache.hadoop.hdfs.server.datanode.DiskBalancerWorkStatus.Result.PLAN_UNDER_PROGRESS;

public class TestDiskBalancerRPC {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private MiniDFSCluster cluster;
  private Configuration conf;

  @Before
  public void setUp() throws Exception {
    conf = new HdfsConfiguration();
    conf.setBoolean(DFSConfigKeys.DFS_DISK_BALANCER_ENABLED, true);
    cluster = new MiniDFSCluster.Builder(conf).numDataNodes(2).build();
    cluster.waitActive();
  }

  @After
  public void tearDown() throws Exception {
    if (cluster != null) {
      cluster.shutdown();
    }
  }

  @Test
  public void testSubmitPlan() throws Exception {
    RpcTestHelper rpcTestHelper = new RpcTestHelper().invoke();
    DataNode dataNode = rpcTestHelper.getDataNode();
    String planHash = rpcTestHelper.getPlanHash();
    int planVersion = rpcTestHelper.getPlanVersion();
    NodePlan plan = rpcTestHelper.getPlan();
    dataNode.submitDiskBalancerPlan(planHash, planVersion, 10, plan.toJson());
  }

  @Test
  public void testSubmitPlanWithInvalidHash() throws Exception {
    RpcTestHelper rpcTestHelper = new RpcTestHelper().invoke();
    DataNode dataNode = rpcTestHelper.getDataNode();
    String planHash = rpcTestHelper.getPlanHash();
    char hashArray[] = planHash.toCharArray();
    hashArray[0]++;
    planHash = String.valueOf(hashArray);
    int planVersion = rpcTestHelper.getPlanVersion();
    NodePlan plan = rpcTestHelper.getPlan();
    thrown.expect(DiskBalancerException.class);
    dataNode.submitDiskBalancerPlan(planHash, planVersion, 10, plan.toJson());
  }

  @Test
  public void testSubmitPlanWithInvalidVersion() throws Exception {
    RpcTestHelper rpcTestHelper = new RpcTestHelper().invoke();
    DataNode dataNode = rpcTestHelper.getDataNode();
    String planHash = rpcTestHelper.getPlanHash();
    int planVersion = rpcTestHelper.getPlanVersion();
    planVersion++;
    NodePlan plan = rpcTestHelper.getPlan();
    thrown.expect(DiskBalancerException.class);
    dataNode.submitDiskBalancerPlan(planHash, planVersion, 10, plan.toJson());
  }

  @Test
  public void testSubmitPlanWithInvalidPlan() throws Exception {
    RpcTestHelper rpcTestHelper = new RpcTestHelper().invoke();
    DataNode dataNode = rpcTestHelper.getDataNode();
    String planHash = rpcTestHelper.getPlanHash();
    int planVersion = rpcTestHelper.getPlanVersion();
    NodePlan plan = rpcTestHelper.getPlan();
    thrown.expect(DiskBalancerException.class);
    dataNode.submitDiskBalancerPlan(planHash, planVersion, 10, "");
  }

  @Test
  public void testCancelPlan() throws Exception {
    RpcTestHelper rpcTestHelper = new RpcTestHelper().invoke();
    DataNode dataNode = rpcTestHelper.getDataNode();
    String planHash = rpcTestHelper.getPlanHash();
    int planVersion = rpcTestHelper.getPlanVersion();
    NodePlan plan = rpcTestHelper.getPlan();
    dataNode.submitDiskBalancerPlan(planHash, planVersion, 10, plan.toJson());
    dataNode.cancelDiskBalancePlan(planHash);
  }

  @Test
  public void testCancelNonExistentPlan() throws Exception {
    RpcTestHelper rpcTestHelper = new RpcTestHelper().invoke();
    DataNode dataNode = rpcTestHelper.getDataNode();
    String planHash = rpcTestHelper.getPlanHash();
    char hashArray[] = planHash.toCharArray();
    hashArray[0]++;
    planHash = String.valueOf(hashArray);
    NodePlan plan = rpcTestHelper.getPlan();
    thrown.expect(DiskBalancerException.class);
    dataNode.cancelDiskBalancePlan(planHash);
  }

  @Test
  public void testCancelEmptyPlan() throws Exception {
    RpcTestHelper rpcTestHelper = new RpcTestHelper().invoke();
    DataNode dataNode = rpcTestHelper.getDataNode();
    String planHash = "";
    NodePlan plan = rpcTestHelper.getPlan();
    thrown.expect(DiskBalancerException.class);
    dataNode.cancelDiskBalancePlan(planHash);
  }


  @Test
  public void testQueryPlan() throws Exception {
    RpcTestHelper rpcTestHelper = new RpcTestHelper().invoke();
    DataNode dataNode = rpcTestHelper.getDataNode();
    String planHash = rpcTestHelper.getPlanHash();
    int planVersion = rpcTestHelper.getPlanVersion();
    NodePlan plan = rpcTestHelper.getPlan();

    dataNode.submitDiskBalancerPlan(planHash, planVersion, 10, plan.toJson());
    DiskBalancerWorkStatus status = dataNode.queryDiskBalancerPlan();
    Assert.assertTrue(status.getResult() == PLAN_UNDER_PROGRESS ||
        status.getResult() == PLAN_DONE);
  }

  @Test
  public void testQueryPlanWithoutSubmit() throws Exception {
    RpcTestHelper rpcTestHelper = new RpcTestHelper().invoke();
    DataNode dataNode = rpcTestHelper.getDataNode();

    DiskBalancerWorkStatus status = dataNode.queryDiskBalancerPlan();
    Assert.assertTrue(status.getResult() == NO_PLAN);
  }

  @Test
  public void testGetDiskBalancerSetting() throws Exception {
    final int dnIndex = 0;
    DataNode dataNode = cluster.getDataNodes().get(dnIndex);
    thrown.expect(DiskBalancerException.class);
    dataNode.getDiskBalancerSetting(
        DiskBalancerConstants.DISKBALANCER_BANDWIDTH);
  }

  private class RpcTestHelper {
    private NodePlan plan;
    private int planVersion;
    private DataNode dataNode;
    private String planHash;

    public NodePlan getPlan() {
      return plan;
    }

    public int getPlanVersion() {
      return planVersion;
    }

    public DataNode getDataNode() {
      return dataNode;
    }

    public String getPlanHash() {
      return planHash;
    }

    public RpcTestHelper invoke() throws Exception {
      final int dnIndex = 0;
      cluster.restartDataNode(dnIndex);
      cluster.waitActive();
      ClusterConnector nameNodeConnector =
          ConnectorFactory.getCluster(cluster.getFileSystem(0).getUri(), conf);

      DiskBalancerCluster diskBalancerCluster =
          new DiskBalancerCluster(nameNodeConnector);
      diskBalancerCluster.readClusterInfo();
      Assert.assertEquals(cluster.getDataNodes().size(),
          diskBalancerCluster.getNodes().size());
      diskBalancerCluster.setNodesToProcess(diskBalancerCluster.getNodes());
      dataNode = cluster.getDataNodes().get(dnIndex);
      DiskBalancerDataNode node = diskBalancerCluster.getNodeByUUID(
          dataNode.getDatanodeUuid());
      GreedyPlanner planner = new GreedyPlanner(10.0f, node);
      plan = new NodePlan(node.getDataNodeName(), node.getDataNodePort());
      planner.balanceVolumeSet(node, node.getVolumeSets().get("DISK"), plan);
      planVersion = 1;
      planHash = DigestUtils.sha512Hex(plan.toJson());
      return this;
    }
  }
}