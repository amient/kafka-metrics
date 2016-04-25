/*
 * Copyright 2015 Michal Harish, michal.harish@gmail.com
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.amient.kafka.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;

public class InfluxDbLoaderMain {
    private static Logger log = LoggerFactory.getLogger(InfluxDbLoaderMain.class);

    public static void main(String[] args) {
        try {
            java.util.Properties props = new java.util.Properties();
            if (args.length == 0) {
                props.load(System.in);
                log.info("Configuring InfluxDBLoader from STDIN");
            } else {
                log.info("Configuring InfluxDBLoader from property file: " + args[0]);
                props.load(new FileInputStream(args[0]));
            }
            props.list(System.out);
            try {
                MeasurementPublisher publisher = new InfluxDbPublisher(props);
                JMXScanner jmxScannerInstance = new JMXScanner(props, publisher);
                ConsumerMetrics consumer = props.containsKey(ConsumerMetrics.COFNIG_CONSUMER_TOPIC)
                        ? new ConsumerMetrics(props) : null;
                while (!jmxScannerInstance.isTerminated() || (consumer != null && !consumer.isTerminated())) {
                    Thread.sleep(5000);
                }
            } catch (Throwable e) {
                log.error("Failed to launch KafkaMetrics JMX Scanner", e);
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
