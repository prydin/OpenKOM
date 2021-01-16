/*
 * Created on Oct 16, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text;

import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.structs.Envelope;

/**
 * Interfaces to classes capable of formatting and printing messages from a conference.
 *
 * @author Pontus Rydin
 */
public interface MessagePrinter {
  public void printMessage(Context context, Envelope envelope) throws KOMException;
}
