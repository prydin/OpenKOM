/*
 * Created on Oct 5, 2003
 * 
 * Distributed under the GPL licens.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.commands;

import nu.rydin.kom.KOMException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;


/**
 * @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a>
 */
public class Logout extends AbstractCommand
{
	public Logout(String fullName)
	{
		super(fullName, AbstractCommand.NO_PARAMETERS);	
	}
	
    public void execute2(Context context, Object[] parameterArray)
            throws KOMException 
    {
		// TODO: use argument string as a logout message 
		//       "log ska sova" broadcasts "Kalle Kula har loggat ut (ska sova)"

        context.getSession().updateLastlogin();
        context.getClientSession().logout();
    }
}
