package com.contextsmith.api.service;

import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.mortbay.servlet.GzipFilter;

import com.contextsmith.utils.ProcessUtil;

public class ServerMain {

  static final Logger log = LogManager.getLogger(ServerMain.class);

  public static void main(String[] args) throws Exception {
    Integer port = getPortOrDie(args);

    ServletContextHandler context = new ServletContextHandler(
        ServletContextHandler.SESSIONS);
    context.setContextPath("/");

    // Add Gzip compression capability.
    FilterHolder holder = new FilterHolder(GzipFilter.class);
    holder.setInitParameter("deflateCompressionLevel", "9");
    holder.setInitParameter("minGzipSize", "0");
    holder.setInitParameter("mimeTypes", "application/json");
    context.addFilter(holder, "/*", EnumSet.of(DispatcherType.REQUEST));

    Server server = new Server(port);
    log.info("Server started listening on port {}", port);
    server.setHandler(context);

    ServletHolder servlet = context.addServlet(
        org.glassfish.jersey.servlet.ServletContainer.class, "/*");
    servlet.setInitOrder(0);

    // Tells the Jersey Servlet which REST service/class to load.
    servlet.setInitParameter(
        "jersey.config.server.provider.classnames",
        NewsFeeder.class.getCanonicalName());

    try {
      server.start();
      server.join();
    } finally {
      server.destroy();
    }
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
}
