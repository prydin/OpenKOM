/*
 * Created on Nov 7, 2003
 *
 * Distributed under the GPL licens.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.commands;

import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;

/**
 * @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a>
 */
public class DisplayCurrentConference extends AbstractCommand
{
	public DisplayCurrentConference(Context context, String fullName)
	{
		super(fullName, AbstractCommand.NO_PARAMETERS);	
	}

	public void execute(Context context, Object[] parameterArray)
		throws KOMException
	{
		context.printCurrentConference();	
	}
}
