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
package io.micrometer.core.tck;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.micrometer.core.instrument.MockClock.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

interface TimerTest {
    Duration step();

    @DisplayName("record throwables")
    @Test
    default void recordThrowable() {
        MeterRegistry registry = new SimpleMeterRegistry();

        Supplier<String> timed = () -> registry.timer("timer").record(() -> "");
        timed.get();
    }

    @Test
    @DisplayName("total time and count are preserved for a single timing")
    default void record(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");
        t.record(42, TimeUnit.MILLISECONDS);
        clock(registry).add(step());

        assertAll(() -> assertEquals(1L, t.count()),
                () -> assertEquals(42, t.totalTime(TimeUnit.MILLISECONDS), 1.0e-12));
    }

    @Test
    @DisplayName("record durations")
    default void recordDuration(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");
        t.record(Duration.ofMillis(42));
        clock(registry).add(step());

        assertAll(() -> assertEquals(1L, t.count()),
            () -> assertEquals(42, t.totalTime(TimeUnit.MILLISECONDS), 1.0e-12));
    }

    @Test
    @DisplayName("negative times are discarded by the Timer")
    default void recordNegative(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");
        t.record(-42, TimeUnit.MILLISECONDS);

        assertAll(() -> assertEquals(0L, t.count()),
                () -> assertEquals(0, t.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
    }

    @Test
    @DisplayName("zero times contribute to the count of overall events but do not add to total time")
    default void recordZero(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");
        t.record(0, TimeUnit.MILLISECONDS);
        clock(registry).add(step());

        assertAll(() -> assertEquals(1L, t.count()),
                () -> assertEquals(0L, t.totalTime(TimeUnit.NANOSECONDS)));
    }

    @Test
    @DisplayName("record a runnable task")
    default void recordWithRunnable(MeterRegistry registry) throws Exception {
        Timer t = registry.timer("myTimer");

        try {
            t.record(() -> clock(registry).add(10, TimeUnit.NANOSECONDS));
            clock(registry).add(step());
        } finally {
            assertAll(() -> assertEquals(1L, t.count()),
                    () -> assertEquals(10, t.totalTime(TimeUnit.NANOSECONDS) ,1.0e-12));
        }
    }

    @Test
    default void recordMax(MeterRegistry registry) {
        Timer timer = registry.timer("my.timer");
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(1, TimeUnit.SECONDS);

        clock(registry).add(step()); // for Atlas, which is step rather than ring-buffer based
        assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(1000);

        clock(registry).add(Duration.ofMillis(step().toMillis() * HistogramConfig.DEFAULT.getHistogramBufferLength()));
        assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(0);
    }

    @Test
    @DisplayName("callable task that throws exception is still recorded")
    default void recordCallableException(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");

        assertThrows(Exception.class, () -> {
            t.recordCallable(() -> {
                clock(registry).add(10, TimeUnit.NANOSECONDS);
                throw new Exception("uh oh");
            });
        });

        clock(registry).add(step());

        assertAll(() -> assertEquals(1L, t.count()),
                () -> assertEquals(10, t.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
    }
}
