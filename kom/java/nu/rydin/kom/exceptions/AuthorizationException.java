/*
 * Created on Nov 5, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/**
 * @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a>
 */
public class AuthorizationException extends UserException
{
	public AuthorizationException()
	{
		super();
	}
	public AuthorizationException(String msg)
	{
		super(msg);
	}	

}
