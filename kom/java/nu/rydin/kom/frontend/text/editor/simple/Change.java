/*
 * Created on Jun 21, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.editor.simple;

import java.io.IOException;
import java.io.PrintWriter;

import nu.rydin.kom.exceptions.EventDeliveredException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.LineEditor;
import nu.rydin.kom.frontend.text.editor.Buffer;
import nu.rydin.kom.frontend.text.editor.EditorContext;
import nu.rydin.kom.frontend.text.parser.CommandLineParameter;
import nu.rydin.kom.frontend.text.parser.LineNumberParameter;
import nu.rydin.kom.utils.PrintUtils;

/**
 * @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a>
 */
public class Change extends AbstractCommand
{
	public Change(Context context, String fullName)
	{
		super(fullName, new CommandLineParameter[] { new LineNumberParameter(true) } );
	}

	public void execute(Context context, Object[] parameterArray)
		throws KOMException, IOException, InterruptedException
	{
	    Integer lineInteger = (Integer) parameterArray[0];
	    
		int line = lineInteger.intValue();
		Buffer buffer = ((EditorContext) context).getBuffer();
		try
		{
			String oldLine = buffer.get(line).toString();
			boolean hasNl = oldLine.endsWith("\n");
			PrintWriter out = context.getOut();
			PrintUtils.printRightJustified(out, Integer.toString(line + 1), 4);
			out.print(':');
			out.flush();
			String newLine = context.getIn().readLine(oldLine, null, context.getTerminalSettings().getWidth() - 6, 
				LineEditor.FLAG_ECHO);
			if(hasNl)
				newLine += "\n";
			buffer.set(line, newLine);
		}
		catch(EventDeliveredException e)
		{
			// TODO: Handle events
			//
			throw new RuntimeException("This should not happen!");	
		}
	}
	
	public void printPreamble(PrintWriter out)
	{
		// Nothing 
	}
	
	public void printPostamble(PrintWriter out)
	{
		// Nothing 
	}
}
