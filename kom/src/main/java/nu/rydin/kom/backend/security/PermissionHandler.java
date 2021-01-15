/*
 * Created on Jan 24, 2005
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend.security;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** @author Pontus Rydin */
public class PermissionHandler extends DefaultHandler {
  private static final short INITIAL_STATE = 0;
  private static final short CLASS_STATE = 1;

  private short m_state = PermissionHandler.INITIAL_STATE;
  private Class<?> m_class;
  private final Map<Method, Long> m_methods = new HashMap<Method, Long>();

  @Override
  public void startElement(final String namespaceURI, final String localName, final String qName, final Attributes atts)
      throws SAXException {
    switch (m_state) {
      case PermissionHandler.INITIAL_STATE:
        if (!"class".equals(qName)) {
            throw new SAXException("'class' expected");
        }
        try {
          m_class = Class.forName(atts.getValue("name"));
          m_state = PermissionHandler.CLASS_STATE;
        } catch (final ClassNotFoundException e) {
          throw new SAXException(e);
        }
        break;
      case PermissionHandler.CLASS_STATE:
        if (!"method".equals(qName)) {
            throw new SAXException("'method' expected");
        }
        final String sStr = atts.getValue("signature");
        if (sStr == null) {
            throw new SAXException("'signature' missing");
        }
        final String pStr = atts.getValue("permissions");
        final long permissions = pStr != null ? Long.parseLong(pStr, 16) : 0;
        m_methods.put(parseSignature(sStr), permissions);
        break;
    }
  }

  @Override
  public void endElement(final String namespaceURI, final String localName, final String qName) throws SAXException {
    switch (m_state) {
      case PermissionHandler.CLASS_STATE:
        if ("class".equals(qName)) {
            m_state = PermissionHandler.INITIAL_STATE;
        }
        break;
    }
  }

  private Method parseSignature(final String s) throws SAXException {
    // Isolate name
    //
    final int p1 = s.indexOf('(');
    if (p1 == -1) {
        throw new SAXException("Malformed signature: " + s);
    }
    final String name = s.substring(0, p1);

    // Isolate parameters
    //
    final int p2 = s.indexOf(')', p1);
    final String pStr = s.substring(p1 + 1, p2);
    final ArrayList<Class<?>> list = new ArrayList<Class<?>>(10);
    for (final StringTokenizer st = new StringTokenizer(pStr); st.hasMoreTokens(); ) {
      final String each = st.nextToken().trim();

      // Try to load class
      //
      Class<?> pClass;
      try {
        pClass = Class.forName(each);
      } catch (final ClassNotFoundException e) {
        // Not found. Try with java.lang prefix
        //
        try {
          pClass = Class.forName("java.lang." + each);
        } catch (final ClassNotFoundException e2) {
          // Not found in java.lang, so throw original exception
          //
          throw new SAXException(e);
        }
      }
      list.add(pClass);
    }

    // Assemble parameter array
    //
    final Class<?>[] pArr = new Class[list.size()];
    list.toArray(pArr);

    // We have a name and a parameter array. Enough to find a method!
    //
    try {
      return m_class.getMethod(name, pArr);
    } catch (final NoSuchMethodException e) {
      throw new SAXException(e);
    }
  }

  public Map<Method, Long> getMethodProtections() {
    return m_methods;
  }
}
