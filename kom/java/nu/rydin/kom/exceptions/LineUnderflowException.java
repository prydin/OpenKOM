/*
 * Created on Jun 19, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/**
 * @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a>
 */
public class LineUnderflowException extends LineEditorException
{
	public LineUnderflowException(String line, int pos)
	{
		super(line, pos);
	}
}
