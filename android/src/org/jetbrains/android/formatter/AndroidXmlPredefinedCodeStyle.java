package org.jetbrains.android.formatter;

import com.android.SdkConstants;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.PredefinedCodeStyle;
import com.intellij.psi.codeStyle.arrangement.ArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.xml.arrangement.XmlRearranger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class AndroidXmlPredefinedCodeStyle extends PredefinedCodeStyle {
  public AndroidXmlPredefinedCodeStyle() {
    super("Android", XMLLanguage.INSTANCE);
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    final CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(XmlFileType.INSTANCE);
    indentOptions.CONTINUATION_INDENT_SIZE = indentOptions.INDENT_SIZE;

    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ALIGN_ATTRIBUTES = false;
    xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG = true;
    xmlSettings.XML_KEEP_LINE_BREAKS = false;

    final AndroidXmlCodeStyleSettings androidSettings = AndroidXmlCodeStyleSettings.getInstance(settings);
    androidSettings.USE_CUSTOM_SETTINGS = true;

    androidSettings.LAYOUT_SETTINGS = new AndroidXmlCodeStyleSettings.LayoutSettings();
    androidSettings.MANIFEST_SETTINGS = new AndroidXmlCodeStyleSettings.ManifestSettings();
    androidSettings.VALUE_RESOURCE_FILE_SETTINGS = new AndroidXmlCodeStyleSettings.ValueResourceFileSettings();
    androidSettings.OTHER_SETTINGS = new AndroidXmlCodeStyleSettings.OtherSettings();

    final CommonCodeStyleSettings xmlCommonSettings = settings.getCommonSettings(XMLLanguage.INSTANCE);
    xmlCommonSettings.setArrangementSettings(createVersion3Settings());
    xmlCommonSettings.FORCE_REARRANGE_MODE = CommonCodeStyleSettings.REARRANGE_ALWAYS;
  }

  @NotNull
  public static ArrangementSettings createVersion3Settings() {
    List<StdArrangementMatchRule> rules = new ArrayList<>();

    rules.add(AndroidXmlRearranger.newAttributeRule("xmlns:android", "^$", Order.KEEP));
    rules.add(AndroidXmlRearranger.newAttributeRule("xmlns:.*", "^$", Order.BY_NAME));
    rules.add(AndroidXmlRearranger.newAttributeRule(".*:id", SdkConstants.ANDROID_URI, Order.KEEP));
    rules.add(AndroidXmlRearranger.newAttributeRule(".*:name", SdkConstants.ANDROID_URI, Order.KEEP));
    rules.add(AndroidXmlRearranger.newAttributeRule("name", "^$", Order.KEEP));
    rules.add(AndroidXmlRearranger.newAttributeRule("style", "^$", Order.KEEP));
    rules.add(AndroidXmlRearranger.newAttributeRule(".*", "^$", Order.BY_NAME));
    rules.add(AndroidXmlRearranger.newAttributeRule(".*", SdkConstants.ANDROID_URI, AndroidAttributeOrder.INSTANCE));
    rules.add(AndroidXmlRearranger.newAttributeRule(".*", ".*", Order.BY_NAME));

    return StdArrangementSettings.createByMatchRules(Collections.emptyList(), rules);
  }

  @NotNull
  public static ArrangementSettings createVersion2Settings() {
    List<StdArrangementMatchRule> rules = new ArrayList<>();

    rules.add(XmlRearranger.attrArrangementRule("xmlns:android", "^$", Order.KEEP));
    rules.add(XmlRearranger.attrArrangementRule("xmlns:.*", "^$", Order.BY_NAME));
    rules.add(XmlRearranger.attrArrangementRule(".*:id", SdkConstants.ANDROID_URI, Order.KEEP));
    rules.add(XmlRearranger.attrArrangementRule(".*:name", SdkConstants.ANDROID_URI, Order.KEEP));
    rules.add(XmlRearranger.attrArrangementRule("name", "^$", Order.KEEP));
    rules.add(XmlRearranger.attrArrangementRule("style", "^$", Order.KEEP));
    rules.add(XmlRearranger.attrArrangementRule(".*", "^$", Order.BY_NAME));
    rules.add(XmlRearranger.attrArrangementRule(".*", SdkConstants.ANDROID_URI, AndroidAttributeOrder.INSTANCE));
    rules.add(XmlRearranger.attrArrangementRule(".*", ".*", Order.BY_NAME));

    return StdArrangementSettings.createByMatchRules(Collections.emptyList(), rules);
  }

  @NotNull
  public static ArrangementSettings createVersion1Settings() {
    List<StdArrangementMatchRule> rules = new ArrayList<>();

    rules.add(XmlRearranger.attrArrangementRule("xmlns:android", "^$", Order.KEEP));
    rules.add(XmlRearranger.attrArrangementRule("xmlns:.*", "^$", Order.BY_NAME));
    rules.add(XmlRearranger.attrArrangementRule(".*:id", SdkConstants.ANDROID_URI, Order.KEEP));
    rules.add(XmlRearranger.attrArrangementRule(".*:name", SdkConstants.ANDROID_URI, Order.KEEP));
    rules.add(XmlRearranger.attrArrangementRule("name", "^$", Order.KEEP));
    rules.add(XmlRearranger.attrArrangementRule("style", "^$", Order.KEEP));
    rules.add(XmlRearranger.attrArrangementRule(".*", "^$", Order.BY_NAME));
    rules.add(XmlRearranger.attrArrangementRule(".*:layout_width", SdkConstants.ANDROID_URI, Order.KEEP));
    rules.add(XmlRearranger.attrArrangementRule(".*:layout_height", SdkConstants.ANDROID_URI, Order.KEEP));
    rules.add(XmlRearranger.attrArrangementRule(".*:layout_.*", SdkConstants.ANDROID_URI, Order.BY_NAME));
    rules.add(XmlRearranger.attrArrangementRule(".*:width", SdkConstants.ANDROID_URI, Order.BY_NAME));
    rules.add(XmlRearranger.attrArrangementRule(".*:height", SdkConstants.ANDROID_URI, Order.BY_NAME));
    rules.add(XmlRearranger.attrArrangementRule(".*", SdkConstants.ANDROID_URI, Order.BY_NAME));
    rules.add(XmlRearranger.attrArrangementRule(".*", ".*", Order.BY_NAME));

    return StdArrangementSettings.createByMatchRules(Collections.emptyList(), rules);
  }
}
