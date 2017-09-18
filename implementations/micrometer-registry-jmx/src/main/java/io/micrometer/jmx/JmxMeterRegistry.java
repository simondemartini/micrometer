/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.jmx;

import com.codahale.metrics.JmxReporter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

/**
 * @author Jon Schneider
 */
public class JmxMeterRegistry extends DropwizardMeterRegistry {
    private final JmxReporter reporter;

    public JmxMeterRegistry() {
        this(HierarchicalNameMapper.DEFAULT, Clock.SYSTEM);
    }

    public JmxMeterRegistry(HierarchicalNameMapper nameMapper, Clock clock) {
        super(nameMapper, clock);

        this.reporter = JmxReporter.forRegistry(getDropwizardRegistry()).build();
        this.reporter.start();
    }

    public void stop() {
        this.reporter.stop();
    }

    public void start() {
        this.reporter.start();
    }
}
