package com.devforge.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import com.devforge.core.paths.AppPaths;
import java.nio.file.Files;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * Adds a persistent log file next to the console (stderr) output and applies the configured level.
 * Logging problems never stop the application. Never log secrets or file contents.
 */
public final class LoggingManager {

    private static final String APPENDER_NAME = "DEVFORGE_FILE";
    private static final String ARCHIVE_PATTERN = "devforge.%d{yyyy-MM-dd}.%i.log.gz";
    private static final FileSize MAX_FILE_SIZE = FileSize.valueOf("10MB");
    private static final FileSize TOTAL_SIZE_CAP = FileSize.valueOf("100MB");
    private static final int MAX_HISTORY_DAYS = 14;

    private LoggingManager() {}

    public static void configure(AppPaths paths, String level) {
        try {
            ILoggerFactory factory = LoggerFactory.getILoggerFactory();
            if (!(factory instanceof LoggerContext context)) {
                return;
            }
            Files.createDirectories(paths.logDir());

            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern("%d{ISO8601} %-5level %logger{20} - %msg%n");
            encoder.start();

            Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            var existing = root.getAppender(APPENDER_NAME);
            if (existing != null) {
                root.detachAppender(existing);
                existing.stop();
            }

            RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
            appender.setContext(context);
            appender.setName(APPENDER_NAME);
            appender.setFile(paths.logDir().resolve("devforge.log").toString());
            appender.setAppend(true);
            appender.setEncoder(encoder);

            SizeAndTimeBasedRollingPolicy<ILoggingEvent> policy = new SizeAndTimeBasedRollingPolicy<>();
            policy.setContext(context);
            policy.setParent(appender);
            policy.setFileNamePattern(paths.logDir().resolve(ARCHIVE_PATTERN).toString());
            policy.setMaxFileSize(MAX_FILE_SIZE);
            policy.setMaxHistory(MAX_HISTORY_DAYS);
            policy.setTotalSizeCap(TOTAL_SIZE_CAP);
            policy.start();
            appender.setRollingPolicy(policy);
            appender.start();

            root.addAppender(appender);
            root.setLevel(Level.toLevel(level, Level.WARN));
        } catch (Throwable ignored) {
            // Logging is best-effort by design.
        }
    }
}
