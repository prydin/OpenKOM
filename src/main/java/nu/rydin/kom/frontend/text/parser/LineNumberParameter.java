/*
 * Created on 2004-aug-19
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

import nu.rydin.kom.exceptions.InvalidLineNumberException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.editor.Buffer;
import nu.rydin.kom.frontend.text.editor.EditorContext;

/** @author Magnus Ihse Bursie */
public class LineNumberParameter extends IntegerParameter {

  public LineNumberParameter(final String missingObjectQuestionKey, final boolean isRequired) {
    super(missingObjectQuestionKey, isRequired);
  }

  public LineNumberParameter(final boolean isRequired) {
    super("parser.parameter.linenumber.ask", isRequired);
  }

  @Override
  public Object resolveFoundObject(final Context context, final Match match) throws KOMException {
    final int line = (Integer) (match.getParsedObject());
    final Buffer buffer = ((EditorContext) context).getBuffer();
    if (line < 1 || line > buffer.size()) {
      throw new InvalidLineNumberException();
    }
    return match.getParsedObject();
  }

  @Override
  protected String getUserDescriptionKey() {
    return "parser.parameter.linenumber.description";
  }
}
