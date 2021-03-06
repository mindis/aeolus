package de.hub.cs.dbis.lrb.operators;

/*
 * #%L
 * lrb
 * %%
 * Copyright (C) 2014 - 2015 Humboldt-Universität zu Berlin
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.utils.Utils;
import de.hub.cs.dbis.aeolus.queries.utils.TimestampMerger;





/**
 * @author Matthias J. Sax
 */
public class FileReaderSpoutITCase {
	private long seed;
	private Random r;
	
	
	
	@Before
	public void prepare() {
		this.seed = System.currentTimeMillis();
		this.r = new Random(this.seed);
		System.out.println("Test seed: " + this.seed);
	}
	
	
	
	@Test(timeout = 15000)
	public void test() throws AlreadyAliveException, InvalidTopologyException, IOException {
		Config conf = new Config();
		
		if(System.getProperty("user.dir").endsWith("JUnitLoop")) {
			conf.put(FileReaderSpout.INPUT_FILE_NAME, "../aeolus/queries/lrb/src/test/resources/xway-");
			conf.put(SpoutDataFileOutputBolt.OUTPUT_DIR_NAME, "../aeolus/queries/lrb/src/test/resources");
		} else {
			conf.put(FileReaderSpout.INPUT_FILE_NAME, "src/test/resources/xway-");
			conf.put(SpoutDataFileOutputBolt.OUTPUT_DIR_NAME, "src/test/resources");
		}
		
		LinkedList<String> inputFiles = new LinkedList<String>();
		for(int i = 0; i < 10; ++i) {
			inputFiles.add(i + "-sample.dat");
		}
		conf.put(FileReaderSpout.INPUT_FILE_SUFFIXES, inputFiles);
		
		
		
		TopologyBuilder builder = new TopologyBuilder();
		final int dop = 1 + this.r.nextInt(10);
		builder.setSpout("Spout", new FileReaderSpout(), new Integer(dop));
		SpoutDataFileOutputBolt sink = new SpoutDataFileOutputBolt();
		builder.setBolt("Sink", new TimestampMerger(sink, 0), new Integer(1)).shuffleGrouping("Spout");
		
		
		
		LocalCluster cluster = new LocalCluster();
		cluster.submitTopology("LR-SpoutTest", conf, builder.createTopology());
		Utils.sleep(10000);
		cluster.killTopology("LR-SpoutTest");
		cluster.shutdown();
		
		
		
		BufferedReader reader = new BufferedReader(new FileReader(
			(String)conf.get(SpoutDataFileOutputBolt.OUTPUT_DIR_NAME) + File.separator + "result.dat"));
		LinkedList<String> result = new LinkedList<String>();
		String line;
		while((line = reader.readLine()) != null) {
			result.add(line);
		}
		reader.close();
		
		LinkedList<String> expectedResult = new LinkedList<String>();
		for(String file : inputFiles) {
			reader = new BufferedReader(new FileReader((String)conf.get(FileReaderSpout.INPUT_FILE_NAME) + file));
			while((line = reader.readLine()) != null) {
				int p1 = line.indexOf(",");
				int p2 = line.indexOf(",", p1 + 1);
				expectedResult.add(line.substring(p1 + 1, p2) + "," + line);
			}
			reader.close();
		}
		Collections.sort(expectedResult);
		for(int i = 1; i < dop; ++i) {
			expectedResult.removeLast();
		}
		
		Assert.assertEquals(expectedResult, result);
	}
	
}
