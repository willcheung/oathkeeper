package com.contextsmith.utils;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.fluentd.logger.FluentLogger;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Fluent Appender for Google's cloud instance engine.
 * Created by beders on 5/4/17.
 */

@Plugin(name = "GoogleFluentAppender", category = "Core", elementType = "appender", printObject = true)
public class GoogleFluentAppender extends AbstractAppender {
    private final HashMap<String, String> serviceContextData;
    private FluentLogger fluentLogger;
    private String label;

    protected GoogleFluentAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions,
                                   String tag, String label,
                                   String service) {
        super(name, filter, layout, ignoreExceptions);
        fluentLogger = FluentLogger.getLogger(tag);
        serviceContextData = new HashMap<>();
        serviceContextData.put("service", service);
        this.label = label == null ? "" : label;
    }

    @Override
    public void stop() {
        super.stop();
        fluentLogger.close();
    }

    @Override
    public void append(LogEvent event) {
        Map<String, Object> message = new HashMap<>();

        //message.put("thread", event.getThreadName());
        if (event.getThrown() != null) {
            StringWriter exceptionWriter = new StringWriter();
            //exceptionWriter.append(event.getMessage().getFormat());
            event.getThrown().printStackTrace(new PrintWriter(exceptionWriter));
            message.put("message", exceptionWriter.toString());
        } else {
            message.put("message", "[" + event.getThreadName() + "] " + event.getMessage().getFormattedMessage());
            message.put("context", context(event));
        }
        message.put("serviceContext", serviceContextData);
        fluentLogger.log(label, message, event.getTimeMillis() / 1000); // thanks for not documenting that Fluent lib
    }

    private Object context(LogEvent event) {
        Map<String, Object> context = new HashMap<>();
        context.put("reportLocation", location(event));
        return context;
    }

    private Object location(LogEvent event) {
        StackTraceElement source = event.getSource();
        Map<String, Object> reportLocation = new HashMap<>();
        reportLocation.put("filePath", source.getFileName());
        reportLocation.put("lineNumber", source.getLineNumber());
        reportLocation.put("functionName", source.getMethodName());
        return reportLocation;
    }

    @PluginFactory
    public static GoogleFluentAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginElement("Filter") final Filter filter,
            @PluginAttribute("tag") String tag,
            @PluginAttribute("label") String label,
            @PluginAttribute("service") String service) {
        if (name == null) {
            LOGGER.error("No name provided for GoogleFluentAppender");
            return null;
        }
        if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
        }
        if (tag == null || tag.isEmpty()) {
            tag = "fluent";
        }
        if (service == null || service.isEmpty()) {
            service = "app";
        }
        if (label == null) {
            label = "";
        }

        return new GoogleFluentAppender(name, filter, layout, false, tag, label, service);
    }
}