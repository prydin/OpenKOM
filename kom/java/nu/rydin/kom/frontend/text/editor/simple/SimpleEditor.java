/*
 * Created on Jun 19, 2004
 *
 * Distributed under the GPL licens.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.editor.simple;

import java.io.IOException;
import java.io.PrintWriter;

import nu.rydin.kom.EventDeliveredException;
import nu.rydin.kom.KOMException;
import nu.rydin.kom.LineOverflowException;
import nu.rydin.kom.LineUnderflowException;
import nu.rydin.kom.OperationInterruptedException;
import nu.rydin.kom.StopCharException;
import nu.rydin.kom.UnexpectedException;
import nu.rydin.kom.backend.NameUtils;
import nu.rydin.kom.frontend.text.Command;
import nu.rydin.kom.frontend.text.CommandParser;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.LineEditor;
import nu.rydin.kom.frontend.text.MessageEditor;
import nu.rydin.kom.frontend.text.editor.Buffer;
import nu.rydin.kom.frontend.text.editor.EditorContext;
import nu.rydin.kom.frontend.text.editor.WordWrapper;
import nu.rydin.kom.i18n.MessageFormatter;
import nu.rydin.kom.structs.UnstoredMessage;
import nu.rydin.kom.utils.PrintUtils;

/**
 * @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a>
 */
public class SimpleEditor implements MessageEditor
{	
	private final CommandParser m_parser;
	
	public SimpleEditor(MessageFormatter formatter)
	throws IOException, UnexpectedException
	{
		m_parser = CommandParser.load("/editorcommands.list", formatter);
	}
	
	public UnstoredMessage edit(Context underlying, long replyTo)
		throws KOMException, InterruptedException, IOException
	{
		EditorContext context = new EditorContext(underlying);
		PrintWriter out = context.getOut();
		LineEditor in = context.getIn();
		MessageFormatter formatter = context.getMessageFormatter();	
		String oldSubject = null;
		try
		{
			// if this is a reply, retrieve subject from original message
			//
			if(replyTo > 0)  
				context.getSession().innerReadMessage(replyTo).getMessage().getSubject();
				
			// Print author
			//
			out.println(formatter.format("simple.editor.author", context.getCachedUserInfo().getName()));
				
			// Read subject
			//
			out.print(formatter.format("simple.editor.subject"));
			out.flush();
			context.setSubject(in.readLine(oldSubject));
						
			// Enter the main editor loop
			//
			if(!this.mainloop(context))
				throw new OperationInterruptedException();			
			return new UnstoredMessage(context.getSubject(), context.getBuffer().toString());
		}
		catch(IOException e)
		{
			throw new KOMException(formatter.format("error.reading.user.input"), e);		
		}
	}
	
	protected boolean mainloop(EditorContext context)
	throws InterruptedException, IOException
	{
		// Set up some stuff
		//
		PrintWriter out = context.getOut();
		LineEditor in = context.getIn();
		MessageFormatter formatter = context.getMessageFormatter();
		Buffer buffer = context.getBuffer();
		int width = context.getTerminalSettings().getWidth() - 5;
		
		// Mainloop
		//
		String defaultLine = ""; 
		for(;;)
		{
			PrintUtils.printRightJustified(out, Integer.toString(buffer.size() + 1), 4);
			out.print(':');
			out.flush();
			String line = null;
			try
			{
				// TODO: Handle chat messages n'stuff.
				//
				 line = in.readLine(defaultLine, "\u0003\u001a", width,
				 	LineEditor.FLAG_ECHO | LineEditor.FLAG_STOP_ON_BOL | LineEditor.FLAG_STOP_ON_EOL);
				 	
				 // Check if we got a command
				 //
				 if(line.length() > 0 && line.charAt(0) == '!')
				 {
				 	// Could be a command, but stuff starting with "!!" are 
				 	// escaped "!".
				 	//
				 	if(line.startsWith("!!"))
				 		line = line.substring(1);
				 	else
				 	{
				 		// It's a command! How great! Go parse it!
				 		//
				 		line = line.substring(1);
				 		String[] parts = NameUtils.splitNameKeepParenteses(line);
						Command command = null;
				 		try
				 		{
				 			command = m_parser.parse(context, line, parts);
				 		}
				 		catch(OperationInterruptedException e)
				 		{
				 			// Command interrupted, continue with editor loop
				 			//
				 			continue;
				 		}
				 		
				 		if(command == null)
				 			continue;
				 			
				 		// We have a command. Go run it! ...with two exceptions: The quit
				 		// and the save command. Check them first.
				 		//
				 		if(command.getClass() == Save.class)
				 			return true;
				 		if(command.getClass() == Quit.class)
				 			return false;
				 		try
				 		{
				 			command.printPreamble(out);
				 			command.execute(context, command.getParameters(parts));
				 			command.printPostamble(out);
				 		}
				 		catch(KOMException e)
				 		{
				 			// TODO: Is this the way we should handle this?
				 			//
				 			out.println(e.formatMessage(context));
				 		}
				 		
				 		// Don't inlude this in the buffer!
				 		// 
				 		continue;
				 	}	
				 }
				 	
				 line += '\n';
				 buffer.add(line);
				 defaultLine = null;
			}
			catch(EventDeliveredException e)
			{
				// TODO: Handle chat messages here!
			}
			catch(LineOverflowException e)
			{
				// Overflow! We have to wrap the line
				//
				String original = e.getLine();
				WordWrapper wrapper = context.getWordWrapper(original, width - 1);
				line = wrapper.nextLine();
				buffer.add(line);
				defaultLine = wrapper.nextLine();
				if(defaultLine == null)
					defaultLine = "";
				
				// Erase wrapped portion
				//
				int top = defaultLine.length();
				for(int idx = 0; idx < top; ++idx)
					out.print("\b \b");
				out.println();
			}
			catch(LineUnderflowException e)
			{
				if(buffer.size() > 0)
				{
					defaultLine = buffer.get(buffer.size() - 1).toString();
					buffer.remove(buffer.size() - 1);
				}
				out.println();
			}
			catch(StopCharException e)
			{
				String s = null;
				int i = (int) e.getStopChar();
				switch(e.getStopChar())
				{
					case '\u001a': // Ctrl-Z
						s = e.getLine();
						if(s.length() > 0)
							buffer.add(s);
						return true;
					case '\u0003': // Ctrl-C
						return false;
				}
			}				
		}
	}
}
