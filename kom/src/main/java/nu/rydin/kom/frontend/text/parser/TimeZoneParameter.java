/*
 * Created on 2004-aug-19
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

import java.util.TimeZone;

/** @author Magnus Ihse Bursie */
public class TimeZoneParameter extends EnumParameter {
  private static final String[] m_javaTimeZones;

  private static final String[] m_presentableTimeZones;

  static {
    m_javaTimeZones = TimeZone.getAvailableIDs();
    m_presentableTimeZones = convertToPresentable(m_javaTimeZones);
  }

  public TimeZoneParameter(final String missingObjectQuestionKey, final boolean isRequired) {
    super(
        missingObjectQuestionKey,
        "parser.parameter.timezone.header",
        missingObjectQuestionKey,
        m_presentableTimeZones,
        true,
        null,
        isRequired);
  }

  public TimeZoneParameter(final boolean isRequired) {
    this("parser.parameter.timezone.ask", isRequired);
  }

  /**
   * @param timeZones
   * @return
   */
  private static String[] convertToPresentable(final String[] timeZones) {
    final String[] converted = new String[timeZones.length];

    for (int i = 0; i < timeZones.length; i++) {
      final String original = timeZones[i];
      converted[i] = original.replace('/', ' ').replace('_', ' ');
    }

    return converted;
  }

  public static String getJavaNameForTimeZoneSelection(final Integer selection) {
    return m_javaTimeZones[selection];
  }

  @Override
  protected String getUserDescriptionKey() {
    return "parser.parameter.timezone.description";
  }
}
