/*
 * Created on Aug 8, 2004
 * 
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import nu.rydin.kom.OperationInterruptedException;
import nu.rydin.kom.UnexpectedException;
import nu.rydin.kom.backend.NameUtils;
import nu.rydin.kom.frontend.text.Command;
import nu.rydin.kom.frontend.text.CommandParser;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.LineEditor;
import nu.rydin.kom.i18n.MessageFormatter;

/**
 * @author Henrik
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class Parser
{
	private Command[] m_commands;
	
	/** Map[Command->CommandLinePart[]] */
	private Map m_commandToPartsMap = new HashMap();

	private class CommandToMatches {
		private Command m_command;
		/** List[CommandLinePart.Match] */
		private List m_matches = new LinkedList();
		/**
		 * @param command
		 * @param matches
		 */
		public CommandToMatches(Command command)
		{
			m_command = command;
		}
		public Command getCommand()
		{
			return m_command;
		}
		public List getMatches()
		{
			return m_matches;
		}
		public void addMatch(Match match) {
			m_matches.add(match);
		}
		/**
		 * @param level
		 * @return
		 */
		public Match getMatch(int level)
		{
			return (Match) m_matches.get(level);
		}
		
		public Match getLastMatch()
		{
			return (Match)m_matches.get(m_matches.size() - 1);
		}
		
		/**
		 * @param level
		 * @return
		 */
		public CommandLinePart getCommandLinePart(int level)
		{
			CommandLinePart[] parts = (CommandLinePart[]) (m_commandToPartsMap.get(m_command));
			if (level > parts.length) {
				return null;
			} else {
				return parts[level];
			}
		}
	}
	
	/**
	 * @param commands
	 * @param primaryCommands
	 */
	public Parser(List commands, List commandNames)
	{
		m_commands = new Command[commands.size()];
		commands.toArray(m_commands);
		
		// Initialize the command->parts map with a pair for each command
		// and its corresponding array of command line parts (command name followed
		// by its signature).
		for (int i = 0; i < m_commands.length; i++)
		{
			Command command = m_commands[i];
			String name = (String)commandNames.get(i);
			CommandNamePart[] nameParts = splitName(name);
			CommandLineParameter[] parameterParts = command.getSignature();
			
			CommandLinePart[] commandLineParts = new CommandLinePart[nameParts.length + 
																	 parameterParts.length];
			System.arraycopy(nameParts, 0, commandLineParts, 0, nameParts.length);
			System.arraycopy(parameterParts, 0, commandLineParts, nameParts.length, parameterParts.length);
			m_commandToPartsMap.put(command, commandLineParts);
		}
	}

	/**
	 * @param name
	 * @return
	 */
	private CommandNamePart[] splitName(String name)
	{
		String cooked = CommandLinePart.cookString(name);
		String[] cookedParts = cooked.split(" ");
		CommandNamePart[] result = new CommandNamePart[cookedParts.length];
		for (int i = 0; i < cookedParts.length; i++)
		{
			String cookedPart = cookedParts[i];
			result[i] = new CommandNamePart(cookedPart);
		}
		return result;
	}
	
	private CommandToMatches resolveAmbiguousCommand(Context context, List potentialTargets) throws IOException, InterruptedException {
		LineEditor in = context.getIn();
		PrintWriter out = context.getOut();
		MessageFormatter fmt = context.getMessageFormatter();

		// Ask user to chose
		//
		out.println(fmt.format("parser.ambiguous"));
		int top = potentialTargets.size();
		for(int idx = 0; idx < top; ++idx)
		{
		    CommandToMatches potentialTarget = (CommandToMatches) potentialTargets.get(idx);
			out.print(idx + 1);
			out.print(". ");
			out.println(potentialTarget.getCommand().getFullName()); 
		}
		out.print(fmt.format("parser.chose"));
		out.flush();
		String input = in.readLine();
		
		// Empty string given? Abort!
		//
		if(input.length() == 0)
			return null;
			
		int idx = 0;
		try
		{
			idx = Integer.parseInt(input);
		}
		catch(NumberFormatException e)
		{
			out.println(fmt.format("parser.invalid.choice"));
			return null;
		}
		if(idx < 1 || idx > top)
		{
			out.println(fmt.format("parser.invalid.choice"));
			return null;
		}
		return (CommandToMatches) potentialTargets.get(idx - 1);
	}

	public void parseAndExecute(Context context, String commandLine) throws IOException, InterruptedException
    {
    	int level = 0;
    	
    	// List[CommandToMatches] 
    	List potentialTargets = new LinkedList();
    	
    	// Build a copy of all commands first, to filter down later.
    	for (int i = 0; i < m_commands.length; i++)
    	{
    		potentialTargets.add(new CommandToMatches(m_commands[i]));
    	}
    	
    	boolean remaindersExist = true;
    	while (remaindersExist && potentialTargets.size() > 1)
    	{
    		for (Iterator iter = potentialTargets.iterator(); iter.hasNext();)
    		{
    			remaindersExist = false;
    			CommandToMatches potentialTarget = (CommandToMatches)iter.next();
    			CommandLinePart part = potentialTarget.getCommandLinePart(level);
    			if (part == null) {
    				if (potentialTarget.getLastMatch().getRemainder().length() > 0) {
    					iter.remove();
    				}
    			} else {
    				String commandLineToMatch;
    				if (level == 0) {
    					commandLineToMatch = commandLine;
    				} else {
    					commandLineToMatch = potentialTarget.getMatch(level).getRemainder();
    				}
    				Match match = part.match(commandLineToMatch);
    				if (!match.isMatching()) {
    					iter.remove();
    				} else {
    					potentialTarget.addMatch(match);
    					if (match.getRemainder().length() > 0)
    					{
    						remaindersExist = true;
    					}
    				}
    			}
    		}
    		level++;
    	}
    	
    	if (potentialTargets.size() > 1) {
    		// Ambiguous matching command found. Try to resolve it.
    	    CommandToMatches potentialTarget = resolveAmbiguousCommand(context, potentialTargets);
    	    if (potentialTarget == null) {
    	        return;
    	    }
    	    // Just save the chosen one in our list for later processing
    	    potentialTargets = new LinkedList();
    	    potentialTargets.add(potentialTarget);
    	}
    	
    	// Now we either have one target candidate, or none.
    	if (potentialTargets.size() == 0) {
    		// No matching command found. Print error and abort.
    		PrintWriter out = context.getOut();
    		MessageFormatter fmt = context.getMessageFormatter();
    		
    		out.println(fmt.format("parser.unknown", commandLine));
    		out.flush();
    		return;
    	} else {
    		// We have one match, but it is not neccessarily correct: we might have
    		// too few parameters, as well as too many. Let's find out, and
    		// ask user about missing parameters.
    		
    		CommandToMatches target = (CommandToMatches) potentialTargets.get(0); 
    		CommandLinePart[] parts = (CommandLinePart[]) m_commandToPartsMap.get(target.getCommand());
    		Match lastMatch = target.getMatch(level - 1);
    		
    		// First, do we have more left on the command line to parse?
    		// If so, match and put it in the targets match list.
    		String remainder = lastMatch.getRemainder();
    		while (remainder.length() > 0) {
    			if (level < parts.length) {
    				// We still have parts to match to
    				Match match = parts[level].match(remainder);
    				if (!match.isMatching()) {
    					// User have entered an invalid parameter. Report error. This should be unlikely.
    		    		PrintWriter out = context.getOut();
    		    		MessageFormatter fmt = context.getMessageFormatter();
    		    		
    		    		out.println(fmt.format("parser.invalid.match", target.getCommand().getFullName()));
    		    		out.flush();
    		    		return;
    				}
    				target.addMatch(match);
    				remainder = match.getRemainder();
    				level++;
    			} else {
    				// User have entered too many parameters. Report error.
		    		PrintWriter out = context.getOut();
		    		MessageFormatter fmt = context.getMessageFormatter();
		    		
		    		out.println(fmt.format("parser.superfluous.parameters", target.getCommand().getFullName()));
		    		out.flush();
		    		return;
    			}
    		}
    		
    		// Now, resolve the entered parts.
    		List resolvedParameters = new LinkedList();
    		for (int i = 0; i < target.getMatches().size(); i++) {
    		    // If this is a command name part, then it is part of the
    			// signature. Add the resolved value of the match to our parameter
    			// list.
    			if (parts[i] instanceof CommandLineParameter) {
        		    Match match = target.getMatch(i);

    			    Object parameter = parts[i].resolveFoundObject(context, match);
    				if (parameter == null) {
    					// Error message have already been written. User aborted.
    					return;
    				}
    				resolvedParameters.add(parameter);
    			}
    		}
    		
    		// If we still need more parameters, ask the user about them.
    		while (level < parts.length) {
    			// Not on command line, ask the user about it.
    			Match match = parts[level].fillInMissingObject(context);
    			if (!match.isMatching()) {
    				// The user entered an invalid parameter, abort
    				PrintWriter out = context.getOut();
    				MessageFormatter fmt = context.getMessageFormatter();
    				
    				out.println(fmt.format("parser.invalid.parameter"));
    				out.flush();
    				return;
    			}
    			
    			// Resolve directly
    			Object parameter = parts[level].resolveFoundObject(context, match);
    			if (parameter == null) {
    				// Error message have already been written. User aborted.
    				return;
    			}
    			resolvedParameters.add(parameter);
    			level++;
    		}
    		
    		System.out.println("command: " + target.getCommand().getFullName());
    		for (Iterator iter = resolvedParameters.iterator(); iter.hasNext();) {
                Object param = (Object) iter.next();
                System.out.println("param: " + param.toString());
            }
    		// Now we can execute the command with the resolved parameters.
    		Object[] parameterArray = new Object[resolvedParameters.size()];
    		resolvedParameters.toArray(parameterArray);
    		target.getCommand().execute2(context, parameterArray);
    	}
    }
	
	private static final Class[] s_commandCtorSignature = new Class[] { String.class };
	
	public static Parser load(String filename, MessageFormatter formatter)
	throws IOException, UnexpectedException
	{
		try
		{
			List commandNames = new ArrayList();
			List list = new ArrayList();
			BufferedReader rdr = new BufferedReader(
				new InputStreamReader(CommandParser.class.getResourceAsStream(filename)));
				
			// Read command list
			//
			String line;
			while((line = rdr.readLine()) != null)
			{
				line = line.trim();
				if(!line.startsWith("#"))
					list.add(line);
			}
			rdr.close();
				
			// Instantiate commands
			//
			int top = list.size();
			List commandList = new ArrayList();
			for(int idx = 0; idx < top; ++idx)
			{
				Class clazz = Class.forName((String) list.get(idx));
				Constructor ctor = clazz.getConstructor(s_commandCtorSignature); 
					
				// Install primary command
				//
				String name = formatter.format(clazz.getName() + ".name");
				Command primaryCommand = (Command) ctor.newInstance(new Object[] { name });
				commandList.add(primaryCommand);
				commandNames.add(name);
					
				// Install aliases
				//
				int aliasIdx = 1;
				for(;; ++aliasIdx)
				{
					// Try alias key
					//
					String alias = formatter.getStringOrNull(clazz.getName() + ".name." + aliasIdx);
					if(alias == null)
						break; // No more aliases
						
					// We found an alias! Create command.
					//
					Command aliasCommand = (Command)ctor.newInstance(new Object[] { alias });
					commandList.add(aliasCommand);
					commandNames.add(alias);
				}
			}

			// Copy to command array
			// 
			return new Parser(commandList, commandNames);
		}
		catch(ClassNotFoundException e)
		{
			throw new UnexpectedException(-1, e);
		}
		catch(NoSuchMethodException e)
		{
			throw new UnexpectedException(-1, e);
		}
		catch(InstantiationException e)
		{
			throw new UnexpectedException(-1, e);
		}
		catch(IllegalAccessException e)
		{
			throw new UnexpectedException(-1, e);
		}
		catch(InvocationTargetException e)
		{
			throw new UnexpectedException(-1, e.getCause());
		}		
	}

	public Command[] getCommandList()
	{
		return m_commands;
	}

	/**
	 * @param class1
	 * @return
	 */
	public Command getCommand(Class class1)
	{
		//TODO: HOLY INEFFICIENT LOOKUP, BATMAN!
		for (int i = 0; i < m_commands.length; i++)
		{
			if (class1.isInstance(m_commands[i]))
			{
				return m_commands[i];
			}
		}
		return null;
	}
}
