/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.core.aop;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.lang.NonNullApi;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Function;

/**
 * AspectJ aspect for intercepting types or methods annotated with {@link Timed @Timed}.
 *
 * @author David J. M. Karlsen
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Nejc Korasa
 * @since 1.0.0
 */
@Aspect
@NonNullApi
@Incubating(since = "1.0.0")
public class TimedAspect {
    public static final String DEFAULT_METRIC_NAME = "method.timed";

    /**
     * Tag key for an exception.
     *
     * @since 1.1.0
     */
    public static final String EXCEPTION_TAG = "exception";

    private final MeterRegistry registry;
    private final Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint;

    public TimedAspect() {
        this(Metrics.globalRegistry);
    }
    
    public TimedAspect(MeterRegistry registry) {
        this(registry, pjp ->
                Tags.of("class", pjp.getStaticPart().getSignature().getDeclaringTypeName(),
                        "method", pjp.getStaticPart().getSignature().getName())
        );
    }

    public TimedAspect(MeterRegistry registry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint) {
        this.registry = registry;
        this.tagsBasedOnJoinPoint = tagsBasedOnJoinPoint;
    }

    @Around("execution (@io.micrometer.core.annotation.Timed * *.*(..))")
    public Object timedMethod(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Timed timed = method.getAnnotation(Timed.class);
        if (timed == null) {
            method = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            timed = method.getAnnotation(Timed.class);
        }

        final String metricName = timed.value().isEmpty() ? DEFAULT_METRIC_NAME : timed.value();

        if (!timed.longTask()) {
            return processWithTimer(pjp, timed, metricName);
        } else {
            return processWithLongTaskTimer(pjp, timed, metricName);
        }
    }

    private Object processWithTimer(ProceedingJoinPoint pjp, Timed timed, String metricName) throws Throwable {
        Timer.Sample sample = Timer.start(registry);
        String exceptionClass = "none";

        try {
            return pjp.proceed();
        } catch (Exception ex) {
            exceptionClass = ex.getClass().getSimpleName();
            throw ex;
        } finally {
            try {
                sample.stop(Timer.builder(metricName)
                                    .description(timed.description().isEmpty() ? null : timed.description())
                                    .tags(timed.extraTags())
                                    .tags(EXCEPTION_TAG, exceptionClass)
                                    .tags(tagsBasedOnJoinPoint.apply(pjp))
                                    .publishPercentileHistogram(timed.histogram())
                                    .publishPercentiles(timed.percentiles().length == 0 ? null : timed.percentiles())
                                    .register(registry));
            } catch (Exception e) {
                // ignoring on purpose
            }
        }
    }

    private Object processWithLongTaskTimer(ProceedingJoinPoint pjp, Timed timed, String metricName) throws Throwable {

        Optional<LongTaskTimer.Sample> sample = buildLongTaskTimer(pjp, timed, metricName).map(LongTaskTimer::start);

        try {
            return pjp.proceed();
        } finally {
            try {
                sample.ifPresent(LongTaskTimer.Sample::stop);
            } catch (Exception e) {
                // ignoring on purpose
            }
        }
    }

    /**
     * Secure long task timer creation - it should not disrupt the application flow in case of exception
     */
    private Optional<LongTaskTimer> buildLongTaskTimer(ProceedingJoinPoint pjp, Timed timed, String metricName) {
        try {
            return Optional.of(LongTaskTimer.builder(metricName)
                                       .description(timed.description().isEmpty() ? null : timed.description())
                                       .tags(timed.extraTags())
                                       .tags(tagsBasedOnJoinPoint.apply(pjp))
                                       .register(registry));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
