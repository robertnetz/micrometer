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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.HistogramSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.histogram.pause.PauseDetector;
import io.micrometer.core.instrument.noop.NoopTimer;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

class CompositeTimer extends AbstractCompositeMeter<Timer> implements Timer {
    private final Clock clock;
    private final HistogramConfig histogramConfig;
    private final PauseDetector pauseDetector;

    CompositeTimer(Id id, Clock clock, HistogramConfig histogramConfig, PauseDetector pauseDetector) {
        super(id);
        this.clock = clock;
        this.histogramConfig = histogramConfig;
        this.pauseDetector = pauseDetector;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        forEachChild(ds -> ds.record(amount, unit));
    }

    @Override
    public void record(Duration duration) {
        forEachChild(ds -> ds.record(duration));
    }

    @Override
    public <T> T record(Supplier<T> f) {
        final long s = clock.monotonicTime();
        try {
            return f.get();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public <T> T recordCallable(Callable<T> f) throws Exception {
        final long s = clock.monotonicTime();
        try {
            return f.call();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public void record(Runnable f) {
        final long s = clock.monotonicTime();
        try {
            f.run();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public long count() {
        return firstChild().count();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return firstChild().totalTime(unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return firstChild().max(unit);
    }

    @Override
    public double percentile(double percentile, TimeUnit unit) {
        return firstChild().percentile(percentile, unit);
    }

    @Override
    public double histogramCountAtValue(long valueNanos) {
        return firstChild().histogramCountAtValue(valueNanos);
    }

    @Override
    public HistogramSnapshot takeSnapshot(boolean supportsAggregablePercentiles) {
        return firstChild().takeSnapshot(supportsAggregablePercentiles);
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return firstChild().baseTimeUnit();
    }

    @Override
    Timer newNoopMeter() {
        return new NoopTimer(getId());
    }

    @Override
    Timer registerNewMeter(MeterRegistry registry) {
        final long[] slaNanos = histogramConfig.getSlaBoundaries();

        Duration[] sla = null;
        if(slaNanos != null) {
            sla = new Duration[slaNanos.length];
            for (int i = 0; i < slaNanos.length; i++) {
                sla[i] = Duration.ofNanos(slaNanos[i]);
            }
        }

        return Timer.builder(getId().getName())
                    .tags(getId().getTags())
                    .description(getId().getDescription())
                    .maximumExpectedValue(Duration.ofNanos(histogramConfig.getMaximumExpectedValue()))
                    .minimumExpectedValue(Duration.ofNanos(histogramConfig.getMinimumExpectedValue()))
                    .publishPercentiles(histogramConfig.getPercentiles())
                    .publishPercentileHistogram(histogramConfig.isPercentileHistogram())
                    .histogramBufferLength(histogramConfig.getHistogramBufferLength())
                    .histogramExpiry(histogramConfig.getHistogramExpiry())
                    .sla(sla)
                    .pauseDetector(pauseDetector)
                    .register(registry);
    }
}
