/*
 * Created on Apr 20, 2005
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org/ for details.
 */
package nu.rydin.kom.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/** @author Pontus Rydin */
public class FileUtils {
  public static String loadTextFromResource(final String resource) throws IOException {
    final InputStream is = ClassLoader.getSystemResourceAsStream(resource);
    if (is != null) {
      try {
        final BufferedReader rdr = new BufferedReader(new InputStreamReader(is));
        final StringBuffer sb = new StringBuffer();
        for (String line = null; (line = rdr.readLine()) != null; ) {
          sb.append(line);
          sb.append("\n\r");
        }
        return sb.toString();
      } finally {
        if (is != null) {
            is.close();
        }
      }

    } else {
        throw new FileNotFoundException();
    }
  }
}
