/*
 * Created on Aug 8, 2004
 * 
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import nu.rydin.kom.exceptions.*;
import nu.rydin.kom.frontend.text.Command;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.LineEditor;
import nu.rydin.kom.i18n.MessageFormatter;

/**
 * @author Magnus Ihse
 * @author Henrik Schr�der
 */
public class Parser
{
	private Command[] m_commands;
	
	/** Map[Command->CommandLinePart[]] */
//	private Map m_commandToPartsMap = new HashMap();

	public static class ExecutableCommand 
	{
	    private Object[] m_parameterArray;
	    private Command m_command;
	    
        public ExecutableCommand(Command command, Object[] parameterArray) 
        {
            m_command = command;
            m_parameterArray = parameterArray;
        }
        
        public Command getCommand() 
        {
            return m_command;
        }
        
        public Object[] getParameterArray() 
        {
            return m_parameterArray;
        }
        
        public void execute(Context context) 
        throws KOMException, IOException, InterruptedException 
        {
    		m_command.printPreamble(context.getOut());
    	    m_command.execute(context, m_parameterArray);
    		m_command.printPostamble(context.getOut());
        }
        
        public void executeBatch(Context context)
        throws KOMException, IOException, InterruptedException
        {
            m_command.execute(context, m_parameterArray);
        }
	}
	
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
		    CommandLinePart[] parts = getCommand().getFullSignature();
			//CommandLinePart[] parts = (CommandLinePart[]) (m_commandToPartsMap.get(m_command));
			if (level >= parts.length) {
				return null;
			} else {
				return parts[level];
			}
		}
		
        public String toString() {
            return "CommandToMatches:[command=" + m_command + ", matches=" + m_matches + "]";
        }
	}
	
	/**
	 * @param commands
	 * @param primaryCommands
	 */
	private Parser(List commands)
	{
		m_commands = new Command[commands.size()];
		commands.toArray(m_commands);
	}

	/**
	 * @param name
	 * @return
	 */
/*	private CommandNamePart[] splitName(String name)
	{
		String cooked = CommandLinePart.cookString(name);
		String[] cookedParts = cooked.split(" ");
		CommandNamePart[] result = new CommandNamePart[cookedParts.length];
		for (int i = 0; i < cookedParts.length; i++)
		{
			String cookedPart = cookedParts[i];
			result[i] = new CommandNamePart(cookedPart, true);
		}
		return result;
	}
*/	
	private CommandToMatches resolveAmbiguousCommand(Context context, List potentialTargets) throws IOException, InterruptedException, KOMException {
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
		if(input.length() == 0) {
			throw new OperationInterruptedException();
		}
			
		int idx = 0;
		try
		{
			idx = Integer.parseInt(input);
		}
		catch(NumberFormatException e)
		{
		    throw new InvalidChoiceException();
		}
		if(idx < 1 || idx > top)
		{
		    throw new InvalidChoiceException();
		}
		return (CommandToMatches) potentialTargets.get(idx - 1);
	}

	public ExecutableCommand parse(Context context, String commandLine) throws IOException, InterruptedException, KOMException
    {
    	int level = 0;
    	
    	// Trim the commandline
    	commandLine = commandLine.trim();
    	
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
			remaindersExist = false;
    		for (Iterator iter = potentialTargets.iterator(); iter.hasNext();)
    		{
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
    					commandLineToMatch = potentialTarget.getLastMatch().getRemainder();
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
    	
    	// Check if there is one and only one potential command that the user 
    	// wrote all parts of. If so, choose it.
    	if (potentialTargets.size() > 1) {
    	    List newPotentialTargets = new LinkedList();
    	    for (Iterator iter = potentialTargets.iterator(); iter.hasNext();)
            {
                CommandToMatches each = (CommandToMatches) iter.next();
                List matches = each.getMatches();
                CommandNamePart[] words = each.getCommand().getNameSignature();
                boolean failedAtLeastOnce = false;
                
                //More words than matches, we have definitely not matched all words.
                if (words.length > matches.size())
                {
                    failedAtLeastOnce = true;
                }
                else
                {
	                //Ok, let's check if all words in the command matches.
	                //If so, put it in the new list.
	                for (int i = 0; i < words.length; i++)
	                {
	                    if (!((Match)matches.get(i)).isMatching())
	                    {
	                        failedAtLeastOnce = true;
	                    }
	                }
                }
                
                if (!failedAtLeastOnce)
                {
                    newPotentialTargets.add(each);
                }
            }
    	
    	    //If newPotentialTargets holds one and only one item, this means
    	    //that in the list of ambigous commands, one and only one matched
    	    //all of its words. SELECT IT!
    	    if (newPotentialTargets.size() == 1)
    	    {
    	        potentialTargets = newPotentialTargets;
    	    }
    	}
  	
    	if (potentialTargets.size() > 1) {
    		// Ambiguous matching command found. Try to resolve it.
    	    CommandToMatches potentialTarget = resolveAmbiguousCommand(context, potentialTargets);

    	    // Just save the chosen one in our list for later processing
    	    potentialTargets = new LinkedList();
    	    potentialTargets.add(potentialTarget);
    	}
    	
    	// Now we either have one target candidate, or none.
    	if (potentialTargets.size() == 0) {
    	    throw new CommandNotFoundException(context.getMessageFormatter().format("parser.unknown", commandLine));
    	} else {
    		// We have one match, but it is not neccessarily correct: we might have
    		// too few parameters, as well as too many. Let's find out, and
    		// ask user about missing parameters.
    		
    		CommandToMatches target = (CommandToMatches) potentialTargets.get(0); 
    		CommandLinePart[] parts = target.getCommand().getFullSignature();
    		//CommandLinePart[] parts = (CommandLinePart[]) m_commandToPartsMap.get(target.getCommand());
    		Match lastMatch = target.getMatch(level - 1);
    		
    		// First, do we have more left on the command line to parse?
    		// If so, match and put it in the targets match list.
    		String remainder = lastMatch.getRemainder();
    		while (remainder.length() > 0) {
    			if (level < parts.length) {
    				// We still have parts to match to
    				Match match = parts[level].match(remainder);
    				if (!match.isMatching()) {
    					// User have entered an invalid parameter. This should be unlikely.
    		    		throw new InvalidParametersException(context.getMessageFormatter().format("parser.invalid.match", target.getCommand().getFullName()));
    				}
    				target.addMatch(match);
    				remainder = match.getRemainder();
    				level++;
    			} else {
		    		throw new TooManyParametersException(context.getMessageFormatter().format("parser.superfluous.parameters", target.getCommand().getFullName()));
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
    				resolvedParameters.add(parameter);
    			}
    		}
    		
    		// If we still need more parameters, ask the user about them.
    		while (level < parts.length) {
    		    Object parameter;
    		    if (parts[level].isRequired())
    		    {
	    			// Not on command line and required, ask the user about it.
	    			Match match = parts[level].fillInMissingObject(context);
	    			if (!match.isMatching()) {
	    				// The user entered an invalid parameter, abort
    		    		throw new InvalidParametersException(context.getMessageFormatter().format("parser.invalid.parameter"));
	    			}
	    			
	    			// Resolve directly
	    			parameter = parts[level].resolveFoundObject(context, match);
    		    }
	    		else
	    		{
	    		    //Parameter was not required, just skip it and add null to the parameters
	    		    parameter = null;
	    		}
	    		resolvedParameters.add(parameter);
    			level++;
    		}
    		
    		for (Iterator iter = resolvedParameters.iterator(); iter.hasNext();) {
                Object param = iter.next();
            }
    		// Now we can execute the command with the resolved parameters.
    		Object[] parameterArray = new Object[resolvedParameters.size()];
    		resolvedParameters.toArray(parameterArray);
    		
    		Command command = target.getCommand();
    		return new ExecutableCommand(command, parameterArray);
    	}
    }

	private static final Class[] s_commandCtorSignature = new Class[] { Context.class, String.class };
	
	/**
	 * 
	 * @param filename
	 * @param context
	 * @return
	 * @throws IOException
	 * @throws UnexpectedException
	 */
	public static Parser load(String filename, Context context)
	throws IOException, UnexpectedException
	{
	    MessageFormatter formatter = context.getMessageFormatter();
		try
		{
			//List commandNames = new ArrayList();
			List list = new ArrayList();
			BufferedReader rdr = new BufferedReader(
				new InputStreamReader(Parser.class.getResourceAsStream(filename)));
				
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
				Command primaryCommand = (Command) ctor.newInstance(new Object[] { context, name });
				commandList.add(primaryCommand);
				//commandNames.add(name);
					
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
					Command aliasCommand = (Command)ctor.newInstance(new Object[] { context, alias });
					commandList.add(aliasCommand);
					//commandNames.add(alias);
				}
			}

			// Copy to command array
			// 
			return new Parser(commandList);
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

	/**
	 * Returns an array of all Commands that are available to the user.
	 * 
	 * @return An Command[] of available commands.
	 */
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
