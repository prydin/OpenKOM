/*
 * Created on Oct 11, 2003
 *  
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.commands;

import java.io.IOException;
import java.io.PrintWriter;

import nu.rydin.kom.KOMException;
import nu.rydin.kom.backend.data.ConferenceManager;
import nu.rydin.kom.backend.data.NameManager;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.structs.NameAssociation;

/**
 * @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a>
 */
public class ListConferences extends AbstractCommand
{
	public ListConferences(String fullName)
	{
		super(fullName);	
	}

	public void execute(Context context, String[] args) 
	throws KOMException, IOException
	{
		PrintWriter out = context.getOut();
		NameAssociation[] names = context.getSession().getAssociationsForPatternAndKind("%", NameManager.CONFERENCE_KIND);
		int top = names.length;
		for(int idx = 0; idx < top; ++idx)
		    out.println(context.formatObjectName(names[idx].getName(), names[idx].getId()));
	}
}
