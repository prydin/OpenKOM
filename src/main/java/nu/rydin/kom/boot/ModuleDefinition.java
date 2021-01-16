/*
 * Created on Sep 18, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.boot;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import nu.rydin.kom.modules.Module;

/**
 * Represents a module definition.
 *
 * @author Pontus Rydin
 */
class ModuleDefinition {
  /** The name of the module */
  private final String name;

  /** Class of module implementation */
  private final String className;

  /** Module specific class path */
  private final List<String> classPath;

  /** Module specific parameters */
  private final Map<String, String> parameters;

  /**
   * Consructs a new module definition.
   *
   * @param name The name of the module
   * @param className The module implementation class
   * @param parameters Module specific parameters
   */
  public ModuleDefinition(
          final String name, final String className, final List<String> classPath, final Map<String, String> parameters) {
    this.name = name;
    this.className = className;
    this.classPath = classPath;
    this.parameters = parameters;
  }

  /** Returns the module implementation class name. */
  public String getClassName() {
    return className;
  }

  /** Returns the module instance name */
  public String getName() {
    return name;
  }

  /** Returns the module specific class path */
  public List<String> getClassPath() {
    return classPath;
  }

  /** Returns module specific parameters */
  public Map<String, String> getParameters() {
    return parameters;
  }

  public Module newInstance()
      throws ClassNotFoundException, IllegalAccessException, InstantiationException,
          MalformedURLException {
    // Build a ClassLoader based on specified classpath (if any)
    //
    final ClassLoader loader;
    if (classPath != null) {
      final int top = classPath.size();
      final URL[] urls = new URL[top];
      int idx = 0;
      for (final String classPathEntry : classPath) {
        urls[idx++] = new File(classPathEntry).toURI().toURL();
      }
      loader = new URLClassLoader(urls, getClass().getClassLoader());
    } else {
      loader = getClass().getClassLoader();
    }
    Thread.currentThread().setContextClassLoader(loader);
    return (Module) loader.loadClass(className).newInstance();
  }
}
