/*
 * Created on Jun 25, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org/ for details
 */
package nu.rydin.kom.frontend.text.commands;

import java.io.IOException;

import nu.rydin.kom.KOMException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.KOMWriter;
import nu.rydin.kom.frontend.text.LineEditor;
import nu.rydin.kom.i18n.MessageFormatter;
import nu.rydin.kom.structs.LocalMessageHeader;
import nu.rydin.kom.utils.PrintUtils;

/**
 * @author Henrik Schr�der
 * @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a>
 */
public class ListOwnMessages extends AbstractCommand 
{
    private static final int CHUNK_SIZE = 50;
	public ListOwnMessages(String fullName) 
	{
		super(fullName);
	}

	public void execute(Context context, String[] parameters)
	throws KOMException, IOException, InterruptedException 
	{
		KOMWriter out = context.getOut();
		LineEditor in = context.getIn();
		MessageFormatter mf = context.getMessageFormatter();

		// TODO: Get out of here if there are no messages
		//
				
		// Print headers and a blank line.
		//
		PrintUtils.printRightJustified(out, mf.format("list.own.heading.global"), 7);
		out.print("  ");
		PrintUtils.printRightJustified(out, mf.format("list.own.heading.text"), 7);
		out.print("  ");
		PrintUtils.printLeftJustified(out, mf.format("list.own.heading.conference"), 30);
		out.print("  ");
		PrintUtils.printLeftJustified(out, mf.format("list.heading.subject"), 30);
		out.println();
		out.println();
		out.flush();
		
		for(int offset = 0;; offset += CHUNK_SIZE)
		{
		    LocalMessageHeader[] lmh = context.getSession().listGlobalMessagesByUser(context.getLoggedInUserId(), offset, CHUNK_SIZE);
		    int top = lmh.length;
		    if(top == 0)
		        break;
			for (int i = 0; i < top; ++i)
			{
				PrintUtils.printRightJustified(out, "(" + lmh[i].getId() + ")", 7);
				out.print("  ");
				PrintUtils.printRightJustified(out, String.valueOf(lmh[i].getLocalnum()), 7);
				out.print("  ");
				//Personal mailfolder prints as username instead of "Brevl�da". WTF?
				PrintUtils.printLeftJustified(out, 
				        context.formatObjectName(context.getSession().getName(lmh[i].getConference()), lmh[i].getConference()), 30);
				out.print("  ");
				PrintUtils.printLeftJustified(out, lmh[i].getSubject(), 30);
				out.println();
			}
		}
	}
}
