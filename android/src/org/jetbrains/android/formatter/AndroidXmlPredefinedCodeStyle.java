package org.jetbrains.android.formatter;

import com.android.SdkConstants;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.PredefinedCodeStyle;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.BY_NAME;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.KEEP;
import static com.intellij.xml.arrangement.XmlRearranger.attrArrangementRule;

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

    // arrangement
    final List<StdArrangementMatchRule> rules = new ArrayList<StdArrangementMatchRule>();
    rules.add(attrArrangementRule("xmlns:android", "^$", KEEP));
    rules.add(attrArrangementRule("xmlns:.*", "^$", BY_NAME));
    rules.add(attrArrangementRule(".*:id", SdkConstants.NS_RESOURCES, KEEP));
    rules.add(attrArrangementRule(".*:name", SdkConstants.NS_RESOURCES, KEEP));
    rules.add(attrArrangementRule("name", "^$", KEEP));
    rules.add(attrArrangementRule("style", "^$", KEEP));
    rules.add(attrArrangementRule(".*", "^$", BY_NAME));
    rules.add(attrArrangementRule(".*:layout_width", SdkConstants.NS_RESOURCES, KEEP));
    rules.add(attrArrangementRule(".*:layout_height", SdkConstants.NS_RESOURCES, KEEP));
    rules.add(attrArrangementRule(".*:layout_.*", SdkConstants.NS_RESOURCES, BY_NAME));
    rules.add(attrArrangementRule(".*:width", SdkConstants.NS_RESOURCES, BY_NAME));
    rules.add(attrArrangementRule(".*:height", SdkConstants.NS_RESOURCES, BY_NAME));
    rules.add(attrArrangementRule(".*", SdkConstants.NS_RESOURCES, BY_NAME));
    rules.add(attrArrangementRule(".*", ".*", BY_NAME));
    // TODO: Should sort name:"color",namespace:"" to the end (primarily for color state lists)
    final CommonCodeStyleSettings xmlCommonSettings = settings.getCommonSettings(XMLLanguage.INSTANCE);
    xmlCommonSettings.setArrangementSettings(
      StdArrangementSettings.createByMatchRules(ContainerUtil.<ArrangementGroupingRule>emptyList(), rules));
    xmlCommonSettings.FORCE_REARRANGE_MODE = CommonCodeStyleSettings.REARRANGE_ALWAYS;
  }
}
