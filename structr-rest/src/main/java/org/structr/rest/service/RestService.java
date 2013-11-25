package org.structr.rest.service;

import ch.qos.logback.access.jetty.RequestLogImpl;
import ch.qos.logback.access.servlet.TeeFilter;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.DispatcherType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.GzipFilter;
import org.eclipse.jetty.util.resource.JarResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.tooling.GlobalGraphOperations;
import org.structr.common.PropertyView;
import org.structr.common.error.FrameworkException;
import org.structr.core.Command;
import org.structr.core.GraphObject;
import org.structr.core.RunnableService;
import org.structr.core.Services;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.NodeService;
import org.structr.core.graph.SyncCommand;
import org.tuckey.web.filters.urlrewrite.UrlRewriteFilter;

/**
 *
 * @author Christian Morgner
 */
public class RestService implements RunnableService {
	
	private static final Logger logger            = Logger.getLogger(RestService.class.getName());
	private static final String INITIAL_SEED_FILE = "seed.zip";
	public static final String SERVLETS           = "RestService.servlets";

	private Server server                         = null;
	private String basePath                       = null;
	private String applicationName                = null;
	private String host                           = null;
	private String restUrl                        = null;
	private int httpPort                          = 8082;
	private int maxIdleTime                       = 30000;
	private int requestHeaderSize                 = 8192;
	

	@Override
	public void startService() {

		logger.log(Level.INFO, "Starting {0} (host={1}:{2}, maxIdleTime={3}, requestHeaderSize={4})", new Object[] { applicationName, host, httpPort, maxIdleTime, requestHeaderSize} );
		logger.log(Level.INFO, "Base path {0}", basePath);
		logger.log(Level.INFO, "{0} started:        http://{1}:{2}{3}", new Object[] { applicationName, host, httpPort, restUrl} );
		
		try {
			server.start();
			
		} catch (Exception ex) {
			
			logger.log(Level.SEVERE, "Unable to start REST server: {0}", ex.getMessage());
		}

		// The jsp directory is created by the container, but we don't need it
		removeDir(basePath, "jsp");

		// TODO: instantiate callbacks from configuration file
//		if (!callbacks.isEmpty()) {
//			
//			for (Callback callback : callbacks) {
//				
//				callback.execute();
//			}
//			
//		}
		
		// check for empty database and seed file
		File seedFile = new File(basePath + "/" + INITIAL_SEED_FILE);
		if (seedFile.exists()) {
			
			logger.log(Level.INFO, "Found initial seed file, checking database status..");
			
			GraphDatabaseService graphDb = Services.getInstance().getService(NodeService.class).getGraphDb();
			boolean hasApplicationNodes  = false;
			
			// check for application nodes (which have UUIDs)
			for (Node node : GlobalGraphOperations.at(graphDb).getAllNodes()) {
				
				if (node.hasProperty(GraphObject.uuid.dbName())) {
					
					hasApplicationNodes = true;
					break;
				}
			}
			
			if (!hasApplicationNodes) {
				
				logger.log(Level.INFO, "No application nodes found, applying initial seed..");
				
				Map<String, Object> attributes = new LinkedHashMap<>();
				
				attributes.put("mode", "import");
				attributes.put("validate", "false");
				attributes.put("file", seedFile.getAbsoluteFile().getAbsolutePath());
				
				try {
					StructrApp.getInstance().command(SyncCommand.class).execute(attributes);
					
				} catch (FrameworkException fex) {
					
					logger.log(Level.WARNING, "Unable to import initial seed file.", fex);
				}
				
			} else {
				
				logger.log(Level.INFO, "Applications nodes found, not applying initial seed.");
			
			}
		}
	}

	@Override
	public void stopService() {
	}

	@Override
	public boolean runOnStartup() {
		return true;
	}

	@Override
	public boolean isRunning() {
		return server != null && server.isRunning();
	}

	@Override
	public void injectArguments(Command command) {
	}

	@Override
	public void initialize(final Properties configurationFile) {
		
		String sourceJarName = getClass().getProtectionDomain().getCodeSource().getLocation().toString();
		final boolean isTest = Boolean.parseBoolean(configurationFile.getProperty(Services.TESTING, "false"));
		
		if (!isTest && StringUtils.stripEnd(sourceJarName, System.getProperty("file.separator")).endsWith("classes")) {
			
			String jarFile = System.getProperty("jarFile");
			if (StringUtils.isEmpty(jarFile)) {
				throw new IllegalArgumentException(getClass().getName() + " was started in an environment where the classloader cannot determine the JAR file containing the main class.\n"
					+ "Please specify the path to the JAR file in the parameter -DjarFile.\n"
					+ "Example: -DjarFile=${project.build.directory}/${project.artifactId}-${project.version}.jar");
			}
			sourceJarName = jarFile;
		}

		// load configuration from properties file
		applicationName                = configurationFile.getProperty(Services.APPLICATION_TITLE);
		host                           = configurationFile.getProperty(Services.APPLICATION_HOST);
		basePath                       = configurationFile.getProperty(Services.BASE_PATH);
		httpPort                       = RestServiceServlet.parseInt(configurationFile.getProperty(Services.APPLICATION_HTTP_PORT), 8082);
		maxIdleTime                    = RestServiceServlet.parseInt(System.getProperty("maxIdleTime"), 30000);
		requestHeaderSize              = RestServiceServlet.parseInt(System.getProperty("requestHeaderSize"), 8192);

		// other properties
		String keyStorePath            = configurationFile.getProperty(Services.APPLICATION_KEYSTORE_PATH);
		String keyStorePassword        = configurationFile.getProperty(Services.APPLICATION_KEYSTORE_PASSWORD);
		String contextPath             = System.getProperty("contextPath", "/");
		String logPrefix               = "structr";
		boolean enableRewriteFilter    = true; // configurationFile.getProperty(Services.
		boolean enableHttps            = RestServiceServlet.parseBoolean(configurationFile.getProperty(Services.APPLICATION_HTTPS_ENABLED), false);
		boolean enableGzipCompression  = true; //
		boolean logRequests            = false; //
		int httpsPort                  = RestServiceServlet.parseInt(configurationFile.getProperty(Services.APPLICATION_HTTP_PORT), 8083);
		
		
		// get current base path
		basePath = System.getProperty("home", basePath);
		if (basePath.isEmpty()) {
			
			// use cwd and, if that fails, /tmp as a fallback
			basePath = System.getProperty("user.dir", "/tmp");
		}
		
		// create base directory if it does not exist
		File baseDir = new File(basePath);
		if (!baseDir.exists()) {
			baseDir.mkdirs();
		}
		
		server                               = new Server(httpPort);
		ContextHandlerCollection contexts    = new ContextHandlerCollection();
		
		contexts.addHandler(new DefaultHandler());
		

		final ServletContextHandler servletContext = new ServletContextHandler(server, contextPath, true, true);
		final List<Connector> connectors           = new LinkedList<>();

		// create resource collection from base path & source JAR
		try {
			servletContext.setBaseResource(new ResourceCollection(Resource.newResource(basePath), JarResource.newJarResource(Resource.newResource(sourceJarName))));
			
		} catch (Throwable t) {
			
			logger.log(Level.WARNING, "Base resource {0} not usable: {1}", new Object[] { basePath, t.getMessage() });
		}
		
		// this is needed for the filters to work on the root context "/"
		servletContext.addServlet("org.eclipse.jetty.servlet.DefaultServlet", "/");
		servletContext.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");

		if (enableGzipCompression) {

			FilterHolder gzipFilter = new FilterHolder(GzipFilter.class);
			gzipFilter.setInitParameter("mimeTypes", "text/html,text/plain,text/css,text/javascript,application/json");
			servletContext.addFilter(gzipFilter, "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD));

		}
		
		if (enableRewriteFilter) {
			
			FilterHolder rewriteFilter = new FilterHolder(UrlRewriteFilter.class);
			rewriteFilter.setInitParameter("confPath", "urlrewrite.xml");
			servletContext.addFilter(rewriteFilter, "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD));
		}

		contexts.addHandler(servletContext);
		
		
		// enable request logging
		if (logRequests || "true".equals(configurationFile.getProperty("log.requests", "false"))) {

			String etcPath = basePath + "/etc";
			File etcDir    = new File(etcPath);

			if (!etcDir.exists()) {

				etcDir.mkdir();
			}
		
			String logbackConfFilePath = basePath + "/etc/logback-access.xml";
			File logbackConfFile       = new File(logbackConfFilePath);

			if (!logbackConfFile.exists()) {

				// synthesize a logback accees log config file
				List<String> config = new LinkedList<>();

				config.add("<configuration>");
				config.add("  <appender name=\"FILE\" class=\"ch.qos.logback.core.rolling.RollingFileAppender\">");
				config.add("    <rollingPolicy class=\"ch.qos.logback.core.rolling.TimeBasedRollingPolicy\">");
				config.add("      <fileNamePattern>logs/" + logPrefix + "-%d{yyyy_MM_dd}.request.log.zip</fileNamePattern>");
				config.add("    </rollingPolicy>");
				config.add("    <encoder>");
				config.add("      <charset>UTF-8</charset>");
				config.add("      <pattern>%h %l %u %t \"%r\" %s %b %n%fullRequest%n%n%fullResponse</pattern>");
				config.add("    </encoder>");
				config.add("  </appender>");
				config.add("  <appender-ref ref=\"FILE\" />");
				config.add("</configuration>");
				
				try {
					logbackConfFile.createNewFile();
					FileUtils.writeLines(logbackConfFile, "UTF-8", config);
					
				} catch (IOException ioex) {
					
					logger.log(Level.WARNING, "Unable to write logback configuration.", ioex);
				}
			}

			FilterHolder loggingFilter = new FilterHolder(TeeFilter.class);
			servletContext.addFilter(loggingFilter, "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD));
			loggingFilter.setInitParameter("includes", "");
			
			RequestLogHandler requestLogHandler = new RequestLogHandler();
			String logPath                      = basePath + "/logs";
			File logDir                         = new File(logPath);

			// Create logs directory if not existing
			if (!logDir.exists()) {

				logDir.mkdir();

			}

			final RequestLogImpl requestLog = new RequestLogImpl();
			requestLogHandler.setRequestLog(requestLog);
			
			final HandlerCollection handlers = new HandlerCollection();
			
			handlers.setHandlers(new Handler[]{ contexts, new DefaultHandler(), requestLogHandler });
			
			server.setHandler(handlers);

		} else {
			
			server.setHandler(contexts);
			
		}

		final List<ContextHandler> resourceHandler = collectResourceHandlers(configurationFile);
		for (ContextHandler contextHandler : resourceHandler) {
			contexts.addHandler(contextHandler);
		}
		
		final Map<String, ServletHolder> servlets = collectServlets(configurationFile);

		// add servlet elements
		int position = 1;
		for (Map.Entry<String, ServletHolder> servlet : servlets.entrySet()) {
			
			ServletHolder servletHolder = servlet.getValue();
			String path                 = servlet.getKey();
			
			servletHolder.setInitOrder(position++);
			
			logger.log(Level.INFO, "Adding servlet {0} for {1}", new Object[] { servletHolder, path } );
			
			servletContext.addServlet(servletHolder, path);
		}
		
		contexts.addHandler(servletContext);
		
		// HTTPs can be disabled
		if (enableHttps) {
			
			if (httpsPort > -1 && keyStorePath != null && !keyStorePath.isEmpty() && keyStorePassword != null) {

				// setup HTTP connector
				SslSelectChannelConnector httpsConnector = null;
				SslContextFactory factory = new SslContextFactory(keyStorePath);

				factory.setKeyStorePassword(keyStorePassword);

				httpsConnector = new SslSelectChannelConnector(factory);

				httpsConnector.setHost(host);

				httpsConnector.setPort(httpsPort);
				httpsConnector.setMaxIdleTime(maxIdleTime);
				httpsConnector.setRequestHeaderSize(requestHeaderSize);

				connectors.add(httpsConnector);

			} else {

				logger.log(Level.WARNING, "Unable to configure SSL, please make sure that application.https.port, application.keystore.path and application.keystore.password are set correctly in structr.conf.");
			}
		}
		
		if (host != null && !host.isEmpty() && httpPort > -1) {

			SelectChannelConnector httpConnector = new SelectChannelConnector();

			httpConnector.setHost(host);
			httpConnector.setPort(httpPort);
			httpConnector.setMaxIdleTime(maxIdleTime);
			httpConnector.setRequestHeaderSize(requestHeaderSize);

			connectors.add(httpConnector);

		} else {

			logger.log(Level.WARNING, "Unable to configure REST port, please make sure that application.host, application.rest.port and application.rest.path are set correctly in structr.conf.");
		}
		
		if (!connectors.isEmpty()) {

			server.setConnectors(connectors.toArray(new Connector[0]));
			
		} else {
			
			logger.log(Level.SEVERE, "No connectors configured, aborting.");
			System.exit(0);
		}
		
		server.setGracefulShutdown(1000);
		server.setStopAtShutdown(true);
	}

	@Override
	public void shutdown() {
		
		if (server != null) {
			
			try {
				server.stop();
				
			} catch (Exception ex) {
				
				logger.log(Level.WARNING, "Error while stopping Jetty server: {0}", ex.getMessage());
			}
		}
	}

	@Override
	public String getName() {
		return RestService.class.getName();
	}

	// ----- private methods -----
	private List<ContextHandler> collectResourceHandlers(final Properties properties) {
		
		final List<ContextHandler> resourceHandlers = new LinkedList<>();
		
		// TODO: read context handlers from configuration file
//		public Structr addResourceHandler(String contextPath, String resourceBase, boolean directoriesListed, String[] welcomeFiles) {
//		
//		ResourceHandler resourceHandler = new ResourceHandler();
//		resourceHandler.setDirectoriesListed(directoriesListed);
//		resourceHandler.setWelcomeFiles(welcomeFiles);
//		resourceHandler.setResourceBase(resourceBase);		
//		ContextHandler staticResourceHandler = new ContextHandler();
//		staticResourceHandler.setContextPath(contextPath);
//		staticResourceHandler.setHandler(resourceHandler);
//		
//		this.resourceHandler.add(staticResourceHandler);

		
		return resourceHandlers;
	}
	
	private Map<String, ServletHolder> collectServlets(final Properties properties) {

		final Map<String, ServletHolder> servlets = new LinkedHashMap<>();
		final String servletNameList              = properties.getProperty(SERVLETS, "");
		
		if (servletNameList != null) {
			
			for(String servletName : servletNameList.split("[ \\t]+")) {
		
				try {
					
					final String servletClassName = properties.getProperty(servletName.concat(".class"));
					if (servletClassName != null) {
						
						final String servletPath = properties.getProperty(servletName.concat(".path"));
						if (servletPath != null) {

							final RestServiceServlet servlet = (RestServiceServlet)Class.forName(servletClassName).newInstance();
							servlet.initializeFromProperties(properties, servletName);
							
							if (servletPath.endsWith("*")) {
								
								servlets.put(servletPath, new ServletHolder(servlet));
								restUrl = servletPath;
								
							} else {
								
								servlets.put(servletPath + "/*", new ServletHolder(servlet));
								restUrl = servletPath + "/*";
							}

						} else {

							logger.log(Level.WARNING, "Unable to register servlet {0}, missing {0}.path", servletName);
						}

					} else {

						logger.log(Level.WARNING, "Unable to register servlet {0}, missing {0}.class", servletName);
					}
					
					
				} catch (Throwable t) {
					
					logger.log(Level.WARNING, "Unable to initialize servlet {0}: {1}", new Object[] { servletName, t.getMessage() });
				}
			}
			
		} else {
			
			logger.log(Level.WARNING, "No servlets configured for RestService.");
		}

		return servlets;
	}
	
	private void removeDir(final String basePath, final String directoryName) {

		String strippedBasePath = StringUtils.stripEnd(basePath, "/");
		
		File file = new File(strippedBasePath + "/" + directoryName);

		if (file.isDirectory()) {
			
			try {
				
				FileUtils.deleteDirectory(file);
				
			} catch (IOException ex) {
				
				logger.log(Level.SEVERE, "Unable to delete directory {0}: {1}", new Object[] { directoryName, ex.getMessage() });
			}
			
		} else {

			file.delete();
		}
	}
}