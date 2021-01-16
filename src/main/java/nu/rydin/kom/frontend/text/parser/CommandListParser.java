/*
 * Created on Nov 8, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nu.rydin.kom.frontend.text.Command;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.i18n.MessageFormatter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** @author Pontus Rydin */
public class CommandListParser extends DefaultHandler {
  public static final short STATE_INITIAL = 0;
  public static final short STATE_COMMAND_LIST = 1;
  public static final short STATE_CATEGORY = 2;
  public static final short STATE_COMMAND = 3;

  private static final Class<?>[] s_commandCtorSignature =
      new Class<?>[] {Context.class, String.class, long.class};

  private final Map<String, CommandCategory> m_categories = new HashMap<>();

  private final List<Command> m_commands = new ArrayList<>();

  private final Context m_context;

  private short m_state = STATE_INITIAL;

  private CommandCategory m_currentCat;

  public CommandListParser(final Context context) {
    super();
    m_context = context;
  }

  @Override
  public void startElement(
      final String namespaceURI, final String localName, final String qName, final Attributes atts)
      throws SAXException {
    switch (m_state) {
      case STATE_INITIAL:
        // Looking for "parser" node
        //
        if (!"commandlist".equals(qName)) {
          throw new SAXException("Node 'commandlist' expected");
        }
        m_state = STATE_COMMAND_LIST;
        break;
      case STATE_COMMAND_LIST:
        if (!"category".equals(qName)) {
          throw new SAXException("Node 'category' expected");
        }
        m_currentCat =
            new CommandCategory(
                atts.getValue("id"),
                atts.getValue("i18n"),
                Integer.parseInt(atts.getValue("order")));
        m_categories.put(m_currentCat.getId(), m_currentCat);
        m_state = STATE_CATEGORY;
        break;
      case STATE_CATEGORY:
        {
          try {
            if (!"command".equals(qName)) {
              throw new SAXException("Node 'command' expected");
            }
            final String className = atts.getValue("class");
            final Class<?> clazz = Class.forName(className);
            final Class<? extends Command> commandClazz = clazz.asSubclass(Command.class);
            final Constructor<? extends Command> commandCtor =
                commandClazz.getConstructor(s_commandCtorSignature);
            final String pString = atts.getValue("permissions");
            final long permissions = pString != null ? Long.parseLong(pString, 16) : 0L;

            // Install primary command
            //
            final MessageFormatter formatter = m_context.getMessageFormatter();
            final String name = formatter.format(className + ".name");

            final Command primaryCommand = commandCtor.newInstance(m_context, name, permissions);
            m_commands.add(primaryCommand);
            m_currentCat.addCommand(primaryCommand);

            // Install aliases
            //
            int aliasIdx = 1;
            for (; ; ++aliasIdx) {
              // Try alias key
              //
              final String alias = formatter.getStringOrNull(clazz.getName() + ".name." + aliasIdx);
              if (alias == null) {
                break; // No more aliases
              }

              // We found an alias! Create command.
              //
              final Command aliasCommand = commandCtor.newInstance(m_context, alias, permissions);
              m_commands.add(aliasCommand);
              m_currentCat.addCommand(aliasCommand);
            }
            m_state = STATE_COMMAND;
            break;
          } catch (final ClassNotFoundException
              | InvocationTargetException
              | IllegalAccessException
              | InstantiationException
              | NoSuchMethodException e) {
            throw new SAXException(e);
          }
        }
    }
  }

  @Override
  public void endElement(final String namespaceURI, final String localName, final String qName) {
    switch (m_state) {
      case STATE_INITIAL:
        break;
      case STATE_COMMAND_LIST:
        m_state = STATE_INITIAL;
        break;
      case STATE_CATEGORY:
        m_state = STATE_COMMAND_LIST;
        break;
      case STATE_COMMAND:
        m_state = STATE_CATEGORY;
        break;
    }
  }

  public List<Command> getCommands() {
    return m_commands;
  }

  public Map<String, CommandCategory> getCategories() {
    return m_categories;
  }
}
