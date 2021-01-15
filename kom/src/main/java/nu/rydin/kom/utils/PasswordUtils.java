/*
 * Created on Jul 30, 2006
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.utils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class PasswordUtils {
  public static boolean compareDigest(final String password, final String candidate)
      throws NoSuchAlgorithmException {
    // Figure out what encoding we're dealing with
    //
    final int l = candidate.length();
    switch (l) {
      case 13:
        {
          // Old-fashioned UNIX crypt
          //
          final String salt = candidate.substring(0, 2);
          return UnixCrypt.crypt(salt, password).equals(candidate);
        }
      case 34:
        // MD5 with 6 byte salt
        //
        final String salt = candidate.substring(3, 11);
        return MD5Crypt.crypt(password, salt).equals(candidate);
      default:
        return false;
    }
  }

  public static String gerenatePasswordDigest(final String password) throws NoSuchAlgorithmException {
    final SecureRandom rnd = SecureRandom.getInstance("SHA1PRNG");
    final byte[] salt = rnd.generateSeed(6);
    final String answer = MD5Crypt.crypt(password, "$1$" + Base64.encodeBytes(salt) + "$");
    return answer;
  }
}
