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
public class StopCharException extends SystemException
{
	private String m_line;
	private char m_stopChar;
	
	public StopCharException(String line, char stopChar)
	{
		m_line 		= line;
		m_stopChar 	= stopChar;
	}

	public String getLine()
	{
		return m_line;
	}
	
	public char getStopChar()
	{
		return m_stopChar;
	}
}
