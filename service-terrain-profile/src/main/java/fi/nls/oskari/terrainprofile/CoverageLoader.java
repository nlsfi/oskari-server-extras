package fi.nls.oskari.terrainprofile;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import fi.nls.oskari.service.ServiceException;
import fi.nls.oskari.service.ServiceRuntimeException;
import fi.nls.oskari.util.IOHelper;
import fi.nls.oskari.util.PropertyUtil;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

public class CoverageLoader {
    private static final String GROUP_KEY = "terrainprofile";
    private static final String COMMAND_NAME = "getCoverage";
    private static final int MAX_RETRIES = 5;
    private static final int SLEEP_BETWEEN_RETRY_MS = 100;

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final ThreadPoolBulkhead bulkhead;
    private final ScheduledExecutorService executor;

    public CoverageLoader() {
        int failRequests = PropertyUtil.getOptional("oskari." + GROUP_KEY + ".failrequests", 10);
        int rollingWindowMs = PropertyUtil.getOptional("oskari." + GROUP_KEY + ".rollingwindow", 100000);
        int waitDuration = PropertyUtil.getOptional("oskari." + GROUP_KEY + ".sleepwindow", 10000);
        int slidingWindow = rollingWindowMs / 1000;

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .waitDurationInOpenState(Duration.ofMillis(waitDuration))
                .permittedNumberOfCallsInHalfOpenState(failRequests/2)
                .minimumNumberOfCalls(failRequests)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(slidingWindow)
                .build();
        circuitBreaker = CircuitBreakerRegistry.of(circuitBreakerConfig).circuitBreaker(COMMAND_NAME);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(MAX_RETRIES)
                .waitDuration(Duration.ofMillis(SLEEP_BETWEEN_RETRY_MS))
                // don't retry if runSupplier throws ignored
                .ignoreExceptions(ServiceRuntimeException.class)
                .failAfterMaxAttempts(true)
                .build();
        retry = RetryRegistry.of(retryConfig).retry(COMMAND_NAME);

        int poolSize = PropertyUtil.getOptional("oskari." + GROUP_KEY + ".job.pool.size", 4);
        int poolLimit = PropertyUtil.getOptional("oskari." + GROUP_KEY + ".job.pool.limit", 100);
        int queueSize = PropertyUtil.getOptional("oskari." + GROUP_KEY + ".job.pool.queue", 100);
        ThreadPoolBulkheadConfig bulkheadConfig = ThreadPoolBulkheadConfig.custom()
                .maxThreadPoolSize(poolSize)
                .coreThreadPoolSize(poolSize/2)
                .queueCapacity(queueSize)
                .build();
        ThreadPoolBulkheadRegistry registry = ThreadPoolBulkheadRegistry.of(bulkheadConfig);
        bulkhead = registry.bulkhead(GROUP_KEY);

        executor = Executors.newScheduledThreadPool(3);

        int timeout = PropertyUtil.getOptional("oskari." + GROUP_KEY + ".job.timeoutms", 15000);
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(timeout)).build();
        timeLimiter = TimeLimiterRegistry.of(timeLimiterConfig).timeLimiter(GROUP_KEY);
    }

    private byte[] runSupplier (Supplier<HttpURLConnection> connectionSupplier) {
        try {
            HttpURLConnection conn = connectionSupplier.get();
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return IOHelper.readBytes(conn);
            }
        } catch (IOException ignored) {}
        throw new ServiceRuntimeException("Unexpected response to GetCoverage");
    }

    public byte[] getCoverage (Supplier<HttpURLConnection> supplier) throws ServiceException {
        try {
            return Decorators.ofSupplier(() -> runSupplier(supplier))
                    .withThreadPoolBulkhead(bulkhead)
                    .withTimeLimiter(timeLimiter, executor)
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry, executor)
                    .get().toCompletableFuture().join();
        } catch (Exception e) { // CompletionException
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                throw new ServiceRuntimeException("Timeout");
            }
            if (cause instanceof CallNotPermittedException) {
                throw new ServiceRuntimeException("WCS service disabled temporarily");
            }
            if (cause instanceof ServiceRuntimeException) {
                throw (ServiceRuntimeException) cause;
            }
            throw new ServiceException("Failed to retrieve data from WCS", e);
        }
    }
}
