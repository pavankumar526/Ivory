/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ivory.converter;

import junit.framework.Assert;
import org.apache.hadoop.fs.Path;
import org.apache.ivory.IvoryException;
import org.apache.ivory.cluster.util.EmbeddedCluster;
import org.apache.ivory.entity.ClusterHelper;
import org.apache.ivory.entity.store.ConfigurationStore;
import org.apache.ivory.entity.v0.Entity;
import org.apache.ivory.entity.v0.EntityType;
import org.apache.ivory.entity.v0.cluster.Cluster;
import org.apache.ivory.entity.v0.cluster.Interfacetype;
import org.apache.ivory.entity.v0.feed.Feed;
import org.apache.ivory.oozie.coordinator.CONFIGURATION.Property;
import org.apache.ivory.oozie.coordinator.COORDINATORAPP;
import org.apache.ivory.oozie.coordinator.SYNCDATASET;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.xml.bind.Unmarshaller;
import java.util.Collection;
import java.util.List;

import static org.testng.Assert.assertEquals;

public class OozieFeedMapperTest {
	private EmbeddedCluster srcMiniDFS;
	private EmbeddedCluster trgMiniDFS;
	ConfigurationStore store = ConfigurationStore.get();
	Cluster srcCluster;
	Cluster trgCluster;
	Feed feed;

	private static final String SRC_CLUSTER_PATH = "/src-cluster.xml";
	private static final String TRG_CLUSTER_PATH = "/trg-cluster.xml";
	private static final String FEED = "/feed.xml";

	@BeforeClass
	public void setUpDFS() throws Exception {
		srcMiniDFS = EmbeddedCluster.newCluster("cluster1", false);
		String srcHdfsUrl = srcMiniDFS.getConf().get("fs.default.name");

		trgMiniDFS = EmbeddedCluster.newCluster("cluster2", false);
		String trgHdfsUrl = trgMiniDFS.getConf().get("fs.default.name");

		cleanupStore();

		srcCluster = (Cluster) storeEntity(EntityType.CLUSTER, SRC_CLUSTER_PATH);
		ClusterHelper.getInterface(srcCluster, Interfacetype.WRITE).setEndpoint(srcHdfsUrl);

		trgCluster = (Cluster) storeEntity(EntityType.CLUSTER, TRG_CLUSTER_PATH);
        ClusterHelper.getInterface(trgCluster, Interfacetype.WRITE).setEndpoint(trgHdfsUrl);

		feed = (Feed) storeEntity(EntityType.FEED, FEED);

	}

	protected Entity storeEntity(EntityType type, String path) throws Exception {
		Unmarshaller unmarshaller = type.getUnmarshaller();
		Entity entity = (Entity) unmarshaller
				.unmarshal(OozieFeedMapperTest.class.getResource(path));
		store.publish(type, entity);
		return entity;
	}

	protected void cleanupStore() throws IvoryException {
		for (EntityType type : EntityType.values()) {
			Collection<String> entities = store.getEntities(type);
			for (String entity : entities)
				store.remove(type, entity);
		}
	}

	@AfterClass
	public void stopDFS() {
		srcMiniDFS.shutdown();
		trgMiniDFS.shutdown();
	}

	@Test
	public void testReplicationCoords() throws IvoryException {
		OozieFeedMapper feedMapper = new OozieFeedMapper(feed);
		List<COORDINATORAPP> coords = feedMapper.getCoordinators(trgCluster,
				new Path("/projects/ivory/"));
		COORDINATORAPP coord = coords.get(0);
		Assert.assertEquals("2010-01-01T00:40Z", coord.getStart());		
		Assert.assertEquals("${nameNode}/projects/ivory/REPLICATION", coord
				.getAction().getWorkflow().getAppPath());
		Assert.assertEquals("IVORY_FEED_REPLICATION_" + feed.getName() + "_"
				+ srcCluster.getName(), coord.getName());
		Assert.assertEquals("${coord:minutes(20)}", coord.getFrequency());
		SYNCDATASET inputDataset = (SYNCDATASET) coord.getDatasets()
				.getDatasetOrAsyncDataset().get(0);
		SYNCDATASET outputDataset = (SYNCDATASET) coord.getDatasets()
				.getDatasetOrAsyncDataset().get(1);

		Assert.assertEquals("${coord:minutes(20)}", inputDataset.getFrequency());
		Assert.assertEquals("input-dataset", inputDataset.getName());
		Assert.assertEquals(
				ClusterHelper.getStorageUrl(srcCluster)
						+ "/examples/input-data/rawLogs/${YEAR}/${MONTH}/${DAY}/${HOUR}/${MINUTE}",
				inputDataset.getUriTemplate());

		Assert.assertEquals("${coord:minutes(20)}",
				outputDataset.getFrequency());
		Assert.assertEquals("output-dataset", outputDataset.getName());
		Assert.assertEquals(
				"${nameNode}"
						+ "/examples/input-data/rawLogs/${YEAR}/${MONTH}/${DAY}/${HOUR}/${MINUTE}",
				outputDataset.getUriTemplate());
		
		String inEventName =coord.getInputEvents().getDataIn().get(0).getName();
		String inEventDataset =coord.getInputEvents().getDataIn().get(0).getDataset();
		String inEventInstance = coord.getInputEvents().getDataIn().get(0).getInstance().get(0);
		Assert.assertEquals("input", inEventName);
		Assert.assertEquals("input-dataset", inEventDataset);
		Assert.assertEquals("${now(0,-40)}", inEventInstance);

		String outEventInstance = coord.getOutputEvents().getDataOut().get(0).getInstance();
		Assert.assertEquals("${now(0,-40)}", outEventInstance);
		
        for(Property prop:coord.getAction().getWorkflow().getConfiguration().getProperty()){
        	if(prop.getName().equals("mapred.job.priority")){
        		assertEquals(prop.getValue(), "NORMAL");
        		break;
        	}
        }

	}
}
