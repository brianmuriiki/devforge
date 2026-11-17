package com.devforge.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import com.devforge.core.paths.AppPaths;
import java.nio.file.Files;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * Adds a persistent log file next to the console (stderr) output and applies the configured level.
 * Logging problems never stop the application. Never log secrets or file contents.
 */
public final class LoggingManager {

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

            FileAppender<ILoggingEvent> appender = new FileAppender<>();
            appender.setContext(context);
            appender.setName("DEVFORGE_FILE");
            appender.setFile(paths.logDir().resolve("devforge.log").toString());
            appender.setAppend(true);
            appender.setEncoder(encoder);
            appender.start();

            Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            root.addAppender(appender);
            root.setLevel(Level.toLevel(level, Level.WARN));
        } catch (Throwable ignored) {
            // Logging is best-effort by design.
        }
    }
}
