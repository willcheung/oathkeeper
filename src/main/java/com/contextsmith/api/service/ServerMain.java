package com.contextsmith.api.service;

import java.io.IOException;
import java.util.EnumSet;
import java.util.logging.LogManager;

import javax.servlet.DispatcherType;

import com.contextsmith.utils.Environment;
import com.contextsmith.utils.Mode;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.mortbay.servlet.GzipFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.utils.ProcessUtil;

import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J;

public class ServerMain {
  private static final Logger log = LoggerFactory.getLogger(ServerMain.class);

  public static void main(String[] args) throws Exception {
    initLogging();
    int port = getPortOrDie(args);
    //sendJavaUtilLoggerToSLF4J();
    SysOutOverSLF4J.sendSystemOutAndErrToSLF4J();

    ServletContextHandler contextHandler = new ServletContextHandler(
        ServletContextHandler.NO_SESSIONS);
    contextHandler.setContextPath("/");

    // Add Gzip compression capability.
    FilterHolder holder = new FilterHolder(GzipFilter.class);
    holder.setInitParameter("deflateCompressionLevel", "9");
    holder.setInitParameter("minGzipSize", "0");
    holder.setInitParameter("mimeTypes", "application/json");
    contextHandler.addFilter(holder, "/*", EnumSet.of(DispatcherType.REQUEST));

//    HandlerList handlers = new HandlerList();
//    RequestLogHandler requestLogHandler = new RequestLogHandler();
//    requestLogHandler.setRequestLog(new NCSARequestLog());
//    handlers.addHandler(requestLogHandler);

    Server server = new Server(port);
    log.info("Server started listening on port {}", port);

//    handlers.addHandler((Handler) contextHandler);
//    handlers.addHandler(requestLogHandler);
    server.setHandler(contextHandler);

    ServletHolder servlet = contextHandler.addServlet(
        org.glassfish.jersey.servlet.ServletContainer.class, "/*");
    servlet.setInitOrder(0);

    // Tells the Jersey Servlet which REST service/class to load.
    servlet.setInitParameter(
        "jersey.config.server.provider.packages",
        NewsFeeder.class.getPackage().getName());

    try {
      server.start();
      server.join();
    } finally {
      server.destroy();
    }
  }

  private static void initLogging() throws IOException {
    Mode m = Environment.mode;

    String logPath =
            m == Mode.production ? "/logging.properties" :
            m == Mode.dev ? "/logging-dev.properties" :
                    "/logging-test.properties";

    LogManager.getLogManager().readConfiguration(ServerMain.class.getResourceAsStream(logPath));
  }

  // Get port from command line first, then system environment.
  private static int getPortOrDie(String[] args) {
    Integer port = null;
    if (args.length > 0 && args[0] != null) {
      try { port = Integer.valueOf(args[0]); }
      catch (NumberFormatException e) {}
    }
    if (port == null) {
      // Heroku passes port via environment.
      String env = System.getenv("PORT");
      if (!StringUtils.isBlank(env)) {
        try { port = Integer.valueOf(env); }
        catch (NumberFormatException e) {}
      }
    }
    if (port == null) {
      ProcessUtil.die("Error: Missing port in first command-line argument "
                    + "(dev: 8888, release: 8889)");
    }
    return port;
  }

  /*private static void sendJavaUtilLoggerToSLF4J() {
    java.util.logging.LogManager.getLogManager().reset();

    // Optionally remove existing handlers attached to j.u.l root logger
    SLF4JBridgeHandler.removeHandlersForRootLogger();  // (since SLF4J 1.6.5)

    // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
    // the initialization phase of your application
    SLF4JBridgeHandler.install();

    java.util.logging.Logger.getLogger("global").setLevel(
        java.util.logging.Level.FINEST);
  }*/
}
