package storm.lrb.bolt;

/*
 * #%L
 * lrb
 * $Id:$
 * $HeadURL:$
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

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import storm.lrb.TopologyControl;

import storm.lrb.model.AccBalRequest;
import storm.lrb.model.DaiExpRequest;
import storm.lrb.model.LRBtuple;
import storm.lrb.model.PosReport;
import storm.lrb.model.TTEstRequest;
import storm.lrb.tools.StopWatch;

/**
 * This Bolt reduces the workload of the spout by taking over {@link Tuple}
 * generation and dispatching to the appropiate stream
 */
public class DispatcherBolt extends BaseRichBolt {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(DispatcherBolt.class);
    private OutputCollector collector;
    private StopWatch timer = new StopWatch();

    private volatile boolean firstrun = true;

    // TODO evtl buffered wschreiben
    @Override
    public void prepare(@SuppressWarnings("rawtypes") Map conf, 
            TopologyContext topologyContext,
            OutputCollector outputCollector) {
        collector = outputCollector;

    }

    @Override
    public void execute(Tuple tuple) {

        splitAndEmit(tuple);

        collector.ack(tuple);
    }

    private void splitAndEmit(Tuple tuple) {

        String line = tuple.getStringByField(TopologyControl.TUPLE_FIELD_NAME);
        if (firstrun) {
            firstrun = false;
            timer = (StopWatch) tuple.getValueByField(TopologyControl.TIMER_FIELD_NAME);
            LOG.info("Set timer: " + timer);
        }
        String typeString = line.substring(0, 1);
        if (!typeString.matches("^[0-4]")) {
            return;
        }

        try {
            int type = Integer.parseInt(typeString);
            switch (type) {
                case LRBtuple.TYPE_POSITION_REPORT:
                    PosReport pos = new PosReport(line, timer);

                    collector.emit(TopologyControl.POS_REPORTS_STREAM_ID, tuple,
                            pos);
                    break;
                case LRBtuple.TYPE_ACCOUNT_BALANCE:
                    AccBalRequest acc = new AccBalRequest(line, timer);
                    collector.emit(TopologyControl.ACCOUNT_BALANCE_REQUESTS_STREAM_ID, tuple, acc);
                    break;
                case LRBtuple.TYPE_DAILY_EXPEDITURE:
                    DaiExpRequest exp = new DaiExpRequest(line, timer);
                    collector.emit(TopologyControl.DAILY_EXPEDITURE_REQUESTS_STREAM_ID, tuple, exp);
                    break;
                case LRBtuple.TYPE_TRAVEL_TIME_REQUEST:
                    TTEstRequest est = new TTEstRequest(line, timer);
                    collector.emit(TopologyControl.TRAVEL_TIME_REQUEST_STREAM_ID, tuple, est);
                    break;
                default:
                    LOG.debug("Tuple does not match required LRB format" + line);

            }

        } catch (NumberFormatException e) {
            LOG.error("Error in line '%s'", line);
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            LOG.error("Error in line '%s'", line);
            throw new RuntimeException(e);
        }

    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {

        outputFieldsDeclarer.declareStream(TopologyControl.POS_REPORTS_STREAM_ID, 
                new Fields(
                        TopologyControl.XWAY_FIELD_NAME,
                        TopologyControl.SEGMENT_FIELD_NAME, 
                        TopologyControl.DIRECTION_FIELD_NAME,
                        TopologyControl.VEHICLE_ID_FIELD_NAME,
                        TopologyControl.POS_REPORT_FIELD_NAME));

        outputFieldsDeclarer.declareStream(TopologyControl.ACCOUNT_BALANCE_REQUESTS_STREAM_ID,
                new Fields(TopologyControl.VEHICLE_ID_FIELD_NAME,
                        TopologyControl.ACCOUNT_BALANCE_REQUEST_FIELD_NAME));
        outputFieldsDeclarer.declareStream(TopologyControl.DAILY_EXPEDITURE_REQUESTS_STREAM_ID,
                new Fields(TopologyControl.VEHICLE_ID_FIELD_NAME,
                        TopologyControl.DAILY_EXPEDITURE_REQUEST_FIELD_NAME));
        outputFieldsDeclarer.declareStream(TopologyControl.TRAVEL_TIME_REQUEST_STREAM_ID,
                new Fields(TopologyControl.VEHICLE_ID_FIELD_NAME,
                        TopologyControl.TRAVEL_TIME_REQUEST_FIELD_NAME));
    }

    @Override
    public void cleanup() {
        super.cleanup();
    }
}
