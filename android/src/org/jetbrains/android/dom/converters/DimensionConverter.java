package org.jetbrains.android.dom.converters;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class DimensionConverter extends ResolvingConverter<String> implements AttributeValueDocumentationProvider {
  public static final DimensionConverter INSTANCE = new DimensionConverter();

  private static final Map<String, String> ourUnits = new HashMap<>();

  static {
    ourUnits.put(SdkConstants.UNIT_DP, "<b>Density-independent Pixels</b> - an abstract unit " +
                                       "that is based on the physical density of the screen.");
    ourUnits.put(SdkConstants.UNIT_SP, "<b>Scale-independent Pixels</b> - this is like the dp unit, " +
                                       "but it is also scaled by the user's font size preference.");
    ourUnits.put(SdkConstants.UNIT_PT, "<b>Points</b> - 1/72 of an inch based on the physical size of the screen.");
    ourUnits.put(SdkConstants.UNIT_MM, "<b>Millimeters</b> - based on the physical size of the screen.");
    ourUnits.put(SdkConstants.UNIT_IN, "<b>Inches</b> - based on the physical size of the screen.");
    ourUnits.put(SdkConstants.UNIT_PX, "<b>Pixels</b> - corresponds to actual pixels on the screen. Not recommended.");
  }

  @NotNull
  @Override
  public Collection<String> getVariants(ConvertContext context) {
    final XmlElement element = context.getXmlElement();

    if (element == null) {
      return Collections.emptyList();
    }
    final String value = ResourceReferenceConverter.getValue(element);

    if (value == null) {
      return Collections.emptyList();
    }
    final String intPrefix = getIntegerPrefix(value);

    if (intPrefix.isEmpty()) {
      return Collections.emptyList();
    }
    final List<String> result = new ArrayList<>(ourUnits.size());

    for (String unit : ourUnits.keySet()) {
      result.add(intPrefix + unit);
    }
    return result;
  }

  @Nullable
  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    final String unit = getUnitFromValue(s);

    if (unit == null || unit.isEmpty()) {
      return null;
    }
    if (SdkConstants.UNIT_DIP.equals(unit)) {
      return s;
    }
    return ourUnits.get(unit) != null ? s : null;
  }

  @Nullable
  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    final String unit = getUnitFromValue(s);
    if (unit != null && !unit.isEmpty()) {
      if (unit.startsWith(",")) {
        return "Use a dot instead of a comma as the decimal mark";
      }
      return "Unknown unit '" + unit + "'";
    }
    return super.getErrorMessage(s, context);
  }

  @Nullable
  public static String getUnitFromValue(@Nullable String value) {
    if (value == null) {
      return null;
    }
    final String intPrefix = getIntegerPrefix(value);

    if (intPrefix.isEmpty()) {
      return null;
    }
    return value.substring(intPrefix.length());
  }

  @VisibleForTesting
  @NotNull
  static String getIntegerPrefix(@NotNull String s) {
    if (s.length() == 0) {
      return "";
    }
    final StringBuilder intPrefixBuilder = new StringBuilder();

    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);

      if (!Character.isDigit(c) && c != '.' && (i > 0 || c != '-')) {
        break;
      }
      intPrefixBuilder.append(c);
    }
    return intPrefixBuilder.toString();
  }

  @Override
  public String getDocumentation(@NotNull String value) {
    final String unit = getUnitFromValue(value);

    if (unit == null) {
      return null;
    }
    final String description = ourUnits.get(unit);

    if (description == null || description.length() == 0) {
      return null;
    }
    return "<html><body>" + description + "</body></html>";
  }
}
