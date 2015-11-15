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

public class ServerMain {

  static final Logger log = LogManager.getLogger(ServerMain.class);
  public static final int DEFAULT_SERVER_PORT = 8888;

  public static void main(String[] args) throws Exception {
    ServletContextHandler context = new ServletContextHandler(
        ServletContextHandler.SESSIONS);
    context.setContextPath("/");

    // Add Gzip compression capability.
    FilterHolder holder = new FilterHolder(GzipFilter.class);
    holder.setInitParameter("deflateCompressionLevel", "9");
    holder.setInitParameter("minGzipSize", "0");
    holder.setInitParameter("mimeTypes", "application/json");
    context.addFilter(holder, "/*", EnumSet.of(DispatcherType.REQUEST));

    int port = DEFAULT_SERVER_PORT;
    String env = System.getenv("PORT");  // Heroku passes port via environment.
    if (!StringUtils.isBlank(env)) {
      try { port = Integer.valueOf(env); }
      catch (NumberFormatException e) {}
    }
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
}
