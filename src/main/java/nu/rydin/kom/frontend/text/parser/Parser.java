/*
 * Created on Aug 8, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import nu.rydin.kom.backend.NameUtils;
import nu.rydin.kom.exceptions.AuthorizationException;
import nu.rydin.kom.exceptions.CommandNotFoundException;
import nu.rydin.kom.exceptions.InvalidChoiceException;
import nu.rydin.kom.exceptions.InvalidParametersException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.LineEditingDoneException;
import nu.rydin.kom.exceptions.OperationInterruptedException;
import nu.rydin.kom.exceptions.TooManyParametersException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.frontend.text.Command;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.LineEditor;
import nu.rydin.kom.i18n.MessageFormatter;
import nu.rydin.kom.utils.PrintUtils;
import org.xml.sax.SAXException;

/**
 * @author Magnus Ihse Bursie
 * @author Henrik Schr�der
 */
public class Parser {
  private final TreeSet<CommandCategory> m_categories = new TreeSet<>();
  private Command[] m_commands;

  private Parser(final List<Command> commands, final Map<String, CommandCategory> categories) {
    m_commands = new Command[commands.size()];
    commands.toArray(m_commands);
    m_categories.addAll(categories.values());
  }

  public static int askForResolution(
      final Context context,
      final List<String> candidates,
      final String promptKey,
      final boolean printHeading,
      final String headingKey,
      final boolean allowPrefixes,
      final String legendKeyPrefix)
      throws IOException, InterruptedException, InvalidChoiceException,
          OperationInterruptedException {
    final LineEditor in = context.getIn();
    final PrintWriter out = context.getOut();
    final MessageFormatter fmt = context.getMessageFormatter();

    for (; ; ) {
      try {
        // Ask user to chose
        //
        if (printHeading) {
          out.println();
        }
        out.println(fmt.format(headingKey));
        final int top = candidates.size();
        for (int idx = 0; idx < top; ++idx) {
          final String candidate = candidates.get(idx);
          final int printIndex = idx + 1;
          PrintUtils.printRightJustified(out, Integer.toString(printIndex), 2);
          out.print(". ");
          out.print(candidate);
          if (legendKeyPrefix != null) {
            final String legend = fmt.getStringOrNull(legendKeyPrefix + '.' + candidate);
            if (legend != null) {
              out.print(" (");
              out.print(legend);
              out.print(')');
            }
          }
          out.println();
        }
        out.print(fmt.format(promptKey));
        out.flush();
        final String input = in.readLine().trim();

        // Empty string given? Abort!
        //
        if (input.length() == 0) {
          throw new OperationInterruptedException();
        }

        try {
          // Is it a number the user entered?
          final int selection = Integer.parseInt(input);
          if (selection < 1 || selection > top) {
            throw new InvalidChoiceException();
          }
          return selection - 1;
        } catch (final NumberFormatException e) {
          return resolveString(
              context, input, candidates, headingKey, promptKey, allowPrefixes, legendKeyPrefix);
        }
      } catch (final LineEditingDoneException e) {
        // Nothing we need to do
      }
    }
  }

  public static int resolveString(
      final Context context,
      final String input,
      final List<String> candidates,
      final String headingKey,
      final String promptKey,
      final boolean allowPrefixes,
      final String legendKeyPrefix)
      throws InvalidChoiceException, OperationInterruptedException, IOException,
          InterruptedException {
    // Nope. Assume it is a name to be matched against the list.
    final String cookedInput = NameUtils.normalizeName(input);
    final List<Integer> originalNumbers = new LinkedList<>();
    final List<String> matchingCandidates = new LinkedList<>();
    int i = 0;
    for (final String candidate : candidates) {
      final String cookedCandidate = NameUtils.normalizeName(candidate);
      if (NameUtils.match(cookedInput, cookedCandidate, allowPrefixes)) {
        originalNumbers.add(i);
        matchingCandidates.add(candidate);
      }
      i++;
    }
    if (matchingCandidates.size() == 0) {
      throw new InvalidChoiceException();
    } else if (matchingCandidates.size() == 1) {
      // Yeah! We got it!
      return originalNumbers.get(0);
    } else {
      // Still ambiguous. Let the user chose again, recursively.
      final int newIndex =
          askForResolution(
              context, matchingCandidates, promptKey, true, headingKey, false, legendKeyPrefix);
      // This is the index in our (shorter) list of remaining candidates,
      // but we need to
      // return the index of the original list. Good thing we saved that
      // number! :-)
      return originalNumbers.get(newIndex);
    }
  }

  /**
   * @param filename
   * @param context
   * @return @throws IOException
   * @throws UnexpectedException
   */
  public static Parser load(final String filename, final Context context)
      throws IOException, UnexpectedException {
    try (final InputStream is = ClassLoader.getSystemClassLoader().getResourceAsStream(filename)) {
      final SAXParser p = SAXParserFactory.newInstance().newSAXParser();
      final CommandListParser handler = new CommandListParser(context);
      p.parse(is, handler);
      return new Parser(handler.getCommands(), handler.getCategories());
    } catch (final SAXException | ParserConfigurationException e) {
      throw new UnexpectedException(-1, e);
    }
  }

  public boolean removeAlias(final String alias) {
    // Step 1, make sure we actually have an alias to remove
    //
    final int top = m_commands.length;
    int pos = -1;
    for (int i = 0; i < top; ++i) {
      if (!(m_commands[i] instanceof AliasWrapper)) {
        continue;
      }

      if (alias.equalsIgnoreCase(m_commands[i].getFullName())) {
        pos = i;
        break;
      }
    }
    if (-1 == pos) {
      // Throw AmbiguousWhateverException here? Call a resolver?
      return false;
    }

    // pos now contains the index of the alias we want to remove. Remove it.
    //
    final Command[] newCommands = new Command[top - 1];
    System.arraycopy(m_commands, 0, newCommands, 0, pos);
    System.arraycopy(m_commands, pos + 1, newCommands, pos - 1, top - (pos + 1));
    m_commands = newCommands;
    return true;
  }

  public void addAlias(final String alias, final String actualCommand) {
    // Add alias by reallocating command array. This isn't
    // done that often, so we gladly pay the price for some
    // shuffling of objects.
    //
    final int top = m_commands.length;
    final Command[] newCommands = new Command[top + 1];
    System.arraycopy(m_commands, 0, newCommands, 0, top);
    newCommands[top] = new AliasWrapper(alias, actualCommand);
    m_commands = newCommands;
  }

  public ExecutableCommand parseCommandLine(final Context context, final String commandLine)
      throws IOException, InterruptedException, KOMException {
    final CommandToMatches target = resolveMatchingCommand(context, commandLine);

    // Check access to command.
    target.getCommand().checkAccess(context);

    return resolveParametersForTarget(context, target);
  }

  private CommandToMatches resolveMatchingCommand(final Context context, String commandLine)
      throws IOException, InterruptedException, KOMException {
    // Trim the commandline
    commandLine = commandLine.trim();

    List<CommandToMatches> potentialTargets = getPotentialTargets(commandLine, context);

    potentialTargets = checkForExactMatch(potentialTargets);

    if (potentialTargets.size() > 1) {
      // Ambiguous matching command found. Try to resolve it.
      potentialTargets = resolveAmbiguousCommand(context, potentialTargets);
    }

    // Now we either have one target candidate, or none.
    if (potentialTargets.size() == 0) {
      throw new CommandNotFoundException(new Object[] {commandLine});
    }

    // We have one match, but it is not neccessarily correct: we might have
    // too few parameters, as well as too many. Let's find out, and
    // ask user about missing parameters.

    return potentialTargets.get(0);
  }

  public Command getMatchingCommand(final Context context, final String commandLine)
      throws IOException, InterruptedException, KOMException {
    return resolveMatchingCommand(context, commandLine).getCommand();
  }

  private List<CommandToMatches> resolveAmbiguousCommand(
      final Context context, List<CommandToMatches> potentialTargets)
      throws IOException, InterruptedException, KOMException {
    final List<String> commandNames = new ArrayList<>();
    for (final CommandToMatches potentialTarget : potentialTargets) {
      commandNames.add(potentialTarget.getCommand().getFullName());
    }

    final int selection =
        askForResolution(
            context, commandNames, "parser.choose", true, "parser.ambiguous", false, null);

    final CommandToMatches potentialTarget = potentialTargets.get(selection);

    // Just save the chosen one in our list for later processing
    potentialTargets = new LinkedList<>();
    potentialTargets.add(potentialTarget);
    return potentialTargets;
  }

  private ExecutableCommand resolveParametersForTarget(
      final Context context, final CommandToMatches target)
      throws IOException, InterruptedException, KOMException {
    final CommandLinePart[] parts = target.getCommand().getFullSignature();
    // CommandLinePart[] parts = (CommandLinePart[])
    // m_commandToPartsMap.get(target.getCommand());
    int level = target.getLevel();
    final Match lastMatch = target.getMatch(level - 1);

    // First, do we have more left on the command line to parse?
    // If so, match and put it in the targets match list.
    String remainder = lastMatch.getRemainder();
    while (remainder.length() > 0) {
      if (level < parts.length) {
        // We still have parts to match to
        final Match match = parts[level].match(remainder);
        if (!match.isMatching()) {
          if (parts[level] instanceof CommandNamePart) {
            // User has entered enough to match one command
            // uniquely,
            // but the rest of the commandline does not match the
            // rest of
            // the command name parameters.
            throw new CommandNotFoundException(
                new Object[] {target.getOriginalCommandline() + remainder});
          } else {
            // User have entered an invalid parameter. This should
            // be unlikely.
            throw new InvalidParametersException(new Object[] {target.getCommand().getFullName()});
          }
        }
        target.addMatch(match);
        remainder = match.getRemainder();
        level++;
      } else {
        throw new TooManyParametersException(new Object[] {target.getCommand().getFullName()});
      }
    }

    // Now, resolve the entered parts.
    final List<Object> resolvedParameters = new LinkedList<>();
    for (int i = 0; i < target.getMatches().size(); i++) {
      // If this is a command name part, then it is part of the
      // signature. Add the resolved value of the match to our parameter
      // list.
      if (parts[i] instanceof CommandLineParameter) {
        final Match match = target.getMatch(i);

        final Object parameter = parts[i].resolveFoundObject(context, match);
        resolvedParameters.add(parameter);
      }
    }

    // If we still need more parameters, ask the user about them.
    while (level < parts.length) {
      final Object parameter;
      final CommandLinePart part = parts[level];

      // If we still have CommandNameParts unmatched, WE IGNORE THEM.
      // Since we've obviously matched one unique command, it's ok.
      if (!(part instanceof CommandNamePart)) {
        if (part.isRequired()) {
          // Not on command line and required, ask the user about it.
          final Match match = part.fillInMissingObject(context);
          if (!match.isMatching()) {
            // The user entered an invalid parameter, abort
            throw new InvalidParametersException(new Object[] {target.getCommand().getFullName()});
          }

          // Resolve directly
          parameter = part.resolveFoundObject(context, match);
        } else {
          // Parameter was not required, just skip it and add null to
          // the parameters
          parameter = null;
        }
        resolvedParameters.add(parameter);
      }
      level++;
    }

    // Now we can execute the command with the resolved parameters.
    final Object[] parameterArray = new Object[resolvedParameters.size()];
    resolvedParameters.toArray(parameterArray);

    final Command command = target.getCommand();
    return new ExecutableCommand(command, parameterArray);
  }

  private List<CommandToMatches> checkForExactMatch(List<CommandToMatches> potentialTargets) {
    // Check if there is one and only one potential command that the user
    // wrote all parts of. If so, choose it.
    if (potentialTargets.size() > 1) {
      final List<CommandToMatches> newPotentialTargets = new LinkedList<>();
      for (final CommandToMatches each : potentialTargets) {
        final List<Match> matches = each.getMatches();
        final CommandNamePart[] words = each.getCommand().getNameSignature();
        boolean failedAtLeastOnce = false;

        // More words than matches, we have definitely not matched all
        // words.
        if (words.length > matches.size()) {
          failedAtLeastOnce = true;
        } else {
          // Ok, let's check if all words in the command matches.
          // If so, put it in the new list.
          for (int i = 0; i < words.length; i++) {
            if (!matches.get(i).isMatching()) {
              failedAtLeastOnce = true;
              break;
            }
          }
        }

        if (!failedAtLeastOnce) {
          newPotentialTargets.add(each);
        }
      }

      // If newPotentialTargets holds one and only one item, this means
      // that in the list of ambigous commands, one and only one matched
      // all of its words. SELECT IT!
      if (newPotentialTargets.size() == 1) {
        potentialTargets = newPotentialTargets;
      }
    }
    return potentialTargets;
  }

  private List<CommandToMatches> getPotentialTargets(
      final String commandLine, final Context context) throws UnexpectedException {
    int level = 0;

    // List[CommandToMatches]
    final List<CommandToMatches> potentialTargets = new LinkedList<>();

    // Build a copy of all commands first, to filter down later.
    for (final Command each : m_commands) {
      if (each.hasAccess(context)) {
        potentialTargets.add(new CommandToMatches(each));
      }
    }

    boolean remaindersExist = true;
    while (remaindersExist && potentialTargets.size() > 1) {
      remaindersExist = false;
      for (final Iterator<CommandToMatches> iter = potentialTargets.iterator(); iter.hasNext(); ) {
        final CommandToMatches potentialTarget = iter.next();
        final CommandLinePart part = potentialTarget.getCommandLinePart(level);
        if (part == null) {
          if (potentialTarget.getLastMatch().getRemainder().length() > 0) {
            iter.remove();
          }
        } else {
          final String commandLineToMatch;
          if (level == 0) {
            commandLineToMatch = commandLine;
          } else {
            commandLineToMatch = potentialTarget.getLastMatch().getRemainder();
          }
          final Match match = part.match(commandLineToMatch);
          if (!match.isMatching()) {
            iter.remove();
          } else {
            potentialTarget.addMatch(match);
            if (match.getRemainder().length() > 0) {
              remaindersExist = true;
            }
          }
        }
      }
      level++;
    }
    return potentialTargets;
  }

  public TreeSet<CommandCategory> getCategories() {
    return m_categories;
  }

  /**
   * Returns an array of all Commands that are available to the user.
   *
   * @return An Command[] of available commands.
   */
  public Command[] getCommandList() {
    return m_commands;
  }

  /**
   * @param class1
   * @return
   */
  public Command getCommand(final Class<? extends Command> class1) {
    for (final Command m_command : m_commands) {
      if (class1.isInstance(m_command)) {
        return m_command;
      }
    }
    return null;
  }

  /** Map[Command->CommandLinePart[]] */
  //	private Map m_commandToPartsMap = new HashMap();
  public static class ExecutableCommand {
    private final Object[] m_parameterArray;

    private final Command m_command;

    public ExecutableCommand(final Command command, final Object[] parameterArray) {
      m_command = command;
      m_parameterArray = parameterArray;
    }

    public Command getCommand() {
      return m_command;
    }

    public Object[] getParameterArray() {
      return m_parameterArray;
    }

    public void execute(final Context context)
        throws KOMException, IOException, InterruptedException {
      final long rp = m_command.getRequiredPermissions();
      if ((rp & context.getCachedUserInfo().getRights()) != rp) {
        throw new AuthorizationException();
      }

      final boolean pageBreak = context.getIn().getPageBreak();
      // Page break might have been disabled on a previous more prompt.
      // Turn it on for this command.
      context.getIn().setPageBreak(true);

      m_command.printPreamble(context.getOut());
      m_command.execute(context, m_parameterArray);
      m_command.printPostamble(context.getOut());

      // Reset page break again.
      context.getIn().setPageBreak(pageBreak);
    }

    public void executeBatch(final Context context)
        throws KOMException, IOException, InterruptedException {
      m_command.execute(context, m_parameterArray);
    }
  }

  private static class CommandToMatches {
    private final Command m_command;

    /** List[CommandLinePart.Match] */
    private final List<Match> m_matches = new LinkedList<>();

    public CommandToMatches(final Command command) {
      m_command = command;
    }

    public Command getCommand() {
      return m_command;
    }

    public List<Match> getMatches() {

      return m_matches;
    }

    public void addMatch(final Match match) {
      m_matches.add(match);
    }

    /**
     * @param level
     * @return
     */
    public Match getMatch(final int level) {
      return m_matches.get(level);
    }

    public Match getLastMatch() {
      return m_matches.get(m_matches.size() - 1);
    }

    public int getLevel() {
      return m_matches.size();
    }

    /**
     * @param level
     * @return
     */
    public CommandLinePart getCommandLinePart(final int level) {
      final CommandLinePart[] parts = getCommand().getFullSignature();
      // CommandLinePart[] parts = (CommandLinePart[])
      // (m_commandToPartsMap.get(m_command));
      if (level >= parts.length) {
        return null;
      } else {
        return parts[level];
      }
    }

    public String getOriginalCommandline() {
      final StringBuilder result = new StringBuilder();
      for (final Match m_match : m_matches) {
        result.append(m_match.getMatchedString());
        result.append(" ");
      }
      return result.toString();
    }

    @Override
    public String toString() {
      return "CommandToMatches:[command=" + m_command + ", matches=" + m_matches + "]";
    }
  }
}
