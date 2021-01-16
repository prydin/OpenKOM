/*
 * Created on Sep 17, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.boot;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.TimeZone;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import nu.rydin.kom.exceptions.ModuleException;
import nu.rydin.kom.exceptions.NoSuchModuleException;
import nu.rydin.kom.modules.Module;
import nu.rydin.kom.modules.Modules;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/** @author Pontus Rydin */
public class Bootstrap {
  private static final Logger LOG = LogManager.getLogger(Bootstrap.class);
  private static final long s_bootTime = System.currentTimeMillis();

  static {
    //	  We always run in UTC.
    //
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  private final String configFile;

  public Bootstrap(final String configFile) {
    this.configFile = configFile;
  }

  public static long getBootTime() {
    return Bootstrap.s_bootTime;
  }

  public static void main(final String[] args) {
    // First and only arg is path module config file, or null
    //
    final String config = args.length > 0 ? args[0] : null;
    final Bootstrap me = new Bootstrap(config);
    try {
      me.run();
    } catch (final Throwable t) {
      LOG.error("Unhandled exception", t);
    }
  }

  public void start() throws IOException, ParserConfigurationException, SAXException {
    // Load class names
    //
    final ModuleDefinition[] definitions = loadModuleList();
    for (final ModuleDefinition each : definitions) {
      LOG.info("Starting server " + each.getName());
      try {
        final Module module = each.newInstance();
        module.start(each.getParameters());
        Modules.registerModule(each.getName(), module);
      } catch (final ClassNotFoundException e) {
        LOG.error("Error locating server class " + each.getName(), e);
      } catch (final InstantiationException | ModuleException | IllegalAccessException e) {
        LOG.error("Error creating instance of server class " + each.getName(), e);
      }
    }
  }

  public void join() throws InterruptedException {
    for (final String moduleName : Modules.listModuleNames()) {
      try {
        Modules.getModule(moduleName).join();
      } catch (final NoSuchModuleException e) {
        // Just skip
      }
    }
  }

  public void run()
      throws InterruptedException, IOException, ParserConfigurationException, SAXException {
    // Start everything and wait for the world (as we know it) to end
    //
    start();
    join();
  }

  protected ModuleDefinition[] loadModuleList()
      throws IOException, SAXException, ParserConfigurationException {
    final ModuleDefinitionHandler mh = new ModuleDefinitionHandler();

    final XMLReader xr = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
    xr.setContentHandler(mh);
    try (final InputStream configInput =
        configFile == null
            ? ClassLoader.getSystemClassLoader().getResourceAsStream("modules.xml")
            : new FileInputStream(configFile)) {
      xr.parse(new InputSource(configInput));
    }
    return mh.getModuleDefinitions();
  }
}
