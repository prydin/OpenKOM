/*
 * Created on Jun 22, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.editor.simple;

import java.io.PrintWriter;

import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.DisplayController;
import nu.rydin.kom.frontend.text.editor.Buffer;
import nu.rydin.kom.frontend.text.editor.EditorContext;
import nu.rydin.kom.i18n.MessageFormatter;
import nu.rydin.kom.utils.PrintUtils;

/**
 * @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a>
 */
public class Show extends AbstractCommand
{
	public Show(Context context, String fullName)
	{
		super(fullName, AbstractCommand.NO_PARAMETERS);
	}

	public void execute(Context context, Object[] parameterArray)
		throws KOMException
	{
		PrintWriter out = context.getOut();
		MessageFormatter formatter = context.getMessageFormatter();
		EditorContext edContext = (EditorContext) context;
		DisplayController dc = context.getDisplayController();
		
		// Print header
		//
		dc.messageHeader();
		out.println(formatter.format("simple.editor.author", context.getCachedUserInfo().getName()));
		
		// Print recipient
		//
		out.println(formatter.format("simple.editor.receiver", edContext.formatObjectName(edContext.getRecipient())));
				
		// Print subject
		//
		out.print(formatter.format("simple.editor.subject"));
		dc.messageSubject();
		out.println(edContext.getSubject());
		dc.messageBody();
		
		// Print body
		//
		Buffer buffer = edContext.getBuffer();
		int top = buffer.size();
		for(int idx = 0; idx < top; ++idx)
		{
			PrintUtils.printRightJustified(out, Integer.toString(idx + 1), 4);
			out.print(':');
			String line = buffer.get(idx).toString();
			int l = line.length();
			if(l > 0 && line.endsWith("\n"))
				line = line.substring(0, l - 1);
			out.println(line);
		}
	}
		
	public void printPostamble(PrintWriter out)
	{
		// Nothing 
	}	
}
