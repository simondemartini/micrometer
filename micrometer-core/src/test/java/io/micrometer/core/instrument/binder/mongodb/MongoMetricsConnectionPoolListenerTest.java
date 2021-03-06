/**
 * Copyright 2019 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.mongodb;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.ServerAddress;
import com.mongodb.event.ClusterListenerAdapter;
import com.mongodb.event.ClusterOpeningEvent;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static de.flapdoodle.embed.mongo.MongodStarter.getDefaultInstance;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MongoMetricsConnectionPoolListener}.
 *
 * @author Christophe Bornet
 */
class MongoMetricsConnectionPoolListenerTest {

    private static final String HOST = "localhost";
    private int port;

    private MongodExecutable mongodExecutable = null;

    @BeforeEach
    void setup() throws IOException {
        MongodStarter starter = getDefaultInstance();

        port = Network.getFreeServerPort();

        IMongodConfig mongodConfig = new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .net(new Net(HOST, port, Network.localhostIsIPv6()))
                .build();
        mongodExecutable = starter.prepare(mongodConfig);
        mongodExecutable.start();
    }

    @Test
    void shouldCreatePoolMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        AtomicReference<String> clusterId = new AtomicReference<>();

        MongoClientOptions options = MongoClientOptions.builder()
                .minConnectionsPerHost(2)
                .addConnectionPoolListener(new MongoMetricsConnectionPoolListener(registry))
                .addClusterListener(new ClusterListenerAdapter() {
                    @Override
                    public void clusterOpening(ClusterOpeningEvent event) {
                        clusterId.set(event.getClusterId().getValue());
                    }
                })
                .build();

        MongoClient mongo = new MongoClient(new ServerAddress(HOST, port), options);
        mongo.getDatabase("test")
                .createCollection("testCol");

        Tags tags = Tags.of(
                "cluster.id", clusterId.get(),
                "server.address", String.format("%s:%s", HOST, port)
        );

        assertThat(registry.get("mongodb.driver.pool.size").tags(tags).gauge().value()).isEqualTo(2);
        assertThat(registry.get("mongodb.driver.pool.checkedout").gauge().value()).isZero();
        assertThat(registry.get("mongodb.driver.pool.waitqueuesize").gauge().value()).isZero();

        mongo.close();

        assertThat(registry.find("mongodb.driver.pool.size").tags(tags).gauge())
                .describedAs("metrics should be removed when the connection pool is closed")
                .isNull();
    }

    @AfterEach
    void destroy() {
        if (mongodExecutable != null) {
            mongodExecutable.stop();
        }
    }

}
