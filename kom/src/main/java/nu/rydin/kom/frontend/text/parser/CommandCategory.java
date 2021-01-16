/*
 * Created on Nov 8, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package nu.rydin.kom.frontend.text.parser;

import java.util.LinkedList;
import java.util.List;
import nu.rydin.kom.frontend.text.Command;

/** @author Pontus Rydin */
public class CommandCategory implements Comparable<CommandCategory> {
  private final String id;

  private final String i18nKey;

  private final int order;

  private final LinkedList<Command> commands = new LinkedList<>();

  public CommandCategory(final String id, final String key, final int order) {
    super();
    this.id = id;
    i18nKey = key;
    this.order = order;
  }

  public String getI18nKey() {
    return i18nKey;
  }

  public String getId() {
    return id;
  }

  public int getOrder() {
    return order;
  }

  public List<Command> getCommands() {
    return commands;
  }

  public void addCommand(final Command command) {
    commands.add(command);
  }

  @Override
  public int compareTo(final CommandCategory cat) {
    return Integer.compare(order, cat.order);
  }
}
