package org.jetbrains.android;

import com.intellij.codeInsight.actions.RearrangeCodeProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import org.jetbrains.android.formatter.AndroidXmlCodeStyleSettings;
import org.jetbrains.android.formatter.AndroidXmlPredefinedCodeStyle;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlFormatterTest extends AndroidTestCase {
  private static final String BASE_PATH = "formatter/xml/";

  private CodeStyleSettings mySettings;

  public void testLayout1() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTestLayout("layout1.xml");
  }

  public void testLayout2() throws Exception {
    doTestLayout("layout1.xml");
  }

  public void testLayout3() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    xmlSettings.XML_KEEP_BLANK_LINES = 0;
    doTestLayout("layout1.xml");
  }

  public void testLayout4() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ALIGN_ATTRIBUTES = true;
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.LAYOUT_SETTINGS.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = false;
    androidSettings.LAYOUT_SETTINGS.INSERT_BLANK_LINE_BEFORE_TAG = false;
    doTestLayout("layout1.xml");
  }

  public void testLayout5() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.LAYOUT_SETTINGS.WRAP_ATTRIBUTES = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTestLayout("layout1.xml");
  }

  public void testLayout6() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ALIGN_ATTRIBUTES = false;
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.LAYOUT_SETTINGS.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = false;
    androidSettings.LAYOUT_SETTINGS.INSERT_BLANK_LINE_BEFORE_TAG = false;
    doTestLayout("layout1.xml");
  }

  public void testLayout7() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ALIGN_ATTRIBUTES = true;
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.LAYOUT_SETTINGS.INSERT_LINE_BREAK_AFTER_LAST_ATTRIBUTE = true;
    doTestLayout("layout1.xml");
  }

  public void testLayout8() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + "layout8.xml", "res/layout/layout.xml");
    myFixture.configureFromExistingVirtualFile(f);

    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        RearrangeCodeProcessor processor = new RearrangeCodeProcessor(new ReformatCodeProcessor(getProject(), false));
        processor.run();
      }
    });

    myFixture.checkResultByFile(BASE_PATH + "layout8_after.xml");
  }

  // Regression test for http://b.android.com/196833
  // "Android" style for XML formatting doesn't insert a newline character before the first attribute
  // (even when that setting is enabled) is a namespace declaration. However, it does it only when
  // namespace declaration is already first attribute in the list. Given that Android XML formatting
  // by default rearranges attributes, it would lead to weird results: if namespace declaration is
  // not on the first line, on first "Reformat Code" run it would but namespace first and keep the
  // line break if it was already there, and on the second run it would remove line break.
  public void testLayoutNonFirstNamespace() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + "layout_non_first_namespace.xml", "res/layout/layout.xml");
    myFixture.configureFromExistingVirtualFile(f);

    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        RearrangeCodeProcessor processor = new RearrangeCodeProcessor(new ReformatCodeProcessor(getProject(), false));
        processor.run();
      }
    });

    myFixture.checkResultByFile(BASE_PATH + "layout_non_first_namespace_after.xml");
  }

  public void testManifest1() throws Exception {
    deleteManifest();
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTestManifest("manifest1.xml");
  }

  public void testManifest2() throws Exception {
    deleteManifest();
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTestManifest("manifest1.xml");
  }

  public void testManifest3() throws Exception {
    deleteManifest();
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    xmlSettings.XML_KEEP_BLANK_LINES = 0;
    doTestManifest("manifest1.xml");
  }

  public void testManifest4() throws Exception {
    deleteManifest();
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ALIGN_ATTRIBUTES = true;
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.MANIFEST_SETTINGS.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = false;
    doTestManifest("manifest1.xml");
  }

  public void testManifest5() throws Exception {
    deleteManifest();
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.MANIFEST_SETTINGS.WRAP_ATTRIBUTES = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTestManifest("manifest1.xml");
  }

  public void testManifest6() throws Exception {
    deleteManifest();
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.MANIFEST_SETTINGS.GROUP_TAGS_WITH_SAME_NAME = false;
    doTestManifest("manifest1.xml");
  }

  public void testManifest7() throws Exception {
    deleteManifest();
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    doTestManifest("manifest1.xml");
  }

  public void testValues1() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTestValues("values1.xml");
  }

  public void testValues2() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.VALUE_RESOURCE_FILE_SETTINGS.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = true;
    doTestValues("values1.xml");
  }

  public void testValues3() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.VALUE_RESOURCE_FILE_SETTINGS.WRAP_ATTRIBUTES = CommonCodeStyleSettings.WRAP_ALWAYS;
    doTestValues("values1.xml");
  }

  public void testValues4() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTestValues("values4.xml");
  }

  public void testValues5() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.VALUE_RESOURCE_FILE_SETTINGS.INSERT_LINE_BREAKS_AROUND_STYLE = false;
    doTestValues("values4.xml");
  }

  public void testHtmlInsideString() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTestValues(getTestName(true) + ".xml");
  }

  public void testSelector1() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTest("selector1.xml", "res/drawable/selector.xml");
  }

  public void testSelector2() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTest("selector2.xml", "res/color/selector.xml");
  }

  public void testSelector3() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.VALUE_RESOURCE_FILE_SETTINGS.WRAP_ATTRIBUTES = CommonCodeStyleSettings.WRAP_ALWAYS;
    doTest("selector2.xml", "res/color/selector.xml");
  }

  public void testShapeDrawable1() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTest("shapeDrawable1.xml", "res/drawable/drawable.xml");
  }

  public void testShapeDrawable2() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.OTHER_SETTINGS.WRAP_ATTRIBUTES = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTest("shapeDrawable1.xml", "res/drawable/drawable.xml");
  }

  public void testPreferences1() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTest("preferences1.xml", "res/xml/preferences.xml");
  }

  public void testPreferences2() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.OTHER_SETTINGS.WRAP_ATTRIBUTES = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTest("preferences1.xml", "res/xml/preferences.xml");
  }

  public void testAttributesArrangement1() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTestArrangement("res/layout/layout1.xml");
  }

  public void testAttributesArrangement2() throws Exception {
    deleteManifest();
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTestArrangement("AndroidManifest.xml");
  }

  private void doTestArrangement(String dst) {
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", dst);
    myFixture.configureFromExistingVirtualFile(f);
    final ArrangementEngine engine = ServiceManager.getService(getProject(), ArrangementEngine.class);
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        engine.arrange(myFixture.getEditor(), myFixture.getFile(), Arrays.asList(new TextRange(0, myFixture.getFile().getTextLength())));
      }
    });
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  private void doTestLayout(String fileName) throws IOException {
    createManifest();
    doTest(fileName, "res/layout/layout.xml");
  }

  private void doTestManifest(String fileName) {
    doTest(fileName, "AndroidManifest.xml");
  }

  private void doTestValues(String fileName) {
    doTest(fileName, "res/values/values.xml");
  }

  private void doTest(String fileName, String dstFileName) {
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + fileName, dstFileName);
    myFixture.configureFromExistingVirtualFile(f);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile());
      }
    });
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySettings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(mySettings);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
    finally {
      super.tearDown();
    }
  }
}
