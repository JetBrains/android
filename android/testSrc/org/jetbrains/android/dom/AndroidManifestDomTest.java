package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.inspections.AndroidElementNotAllowedInspection;
import org.jetbrains.android.inspections.AndroidUnknownAttributeInspection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author coyote
 */
public class AndroidManifestDomTest extends AndroidDomTest {
  public AndroidManifestDomTest() {
    super(false, "dom/manifest");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return SdkConstants.FN_ANDROID_MANIFEST_XML;
  }

  public void testAttributeNameCompletion1() throws Throwable {
    doTestCompletionVariants("an1.xml", "android:icon", "android:label", "android:priority", "android:logo", "replace");
  }

  public void testAttributeNameCompletion2() throws Throwable {
    doTestCompletionVariants("an2.xml", "debuggable", "description", "hasCode", "vmSafeMode");
  }

  public void testAttributeNameCompletion3() throws Throwable {
    toTestCompletion("an3.xml", "an3_after.xml");
  }

  public void testAttributeNameCompletion4() throws Throwable {
    toTestCompletion("an4.xml", "an4_after.xml");
  }

  public void testAttributeByLocalNameCompletion() throws Throwable {
    toTestCompletion("attrByLocalName.xml", "attrByLocalName_after.xml");
  }

  public void testTagNameCompletion2() throws Throwable {
    doTestCompletionVariants("tn2.xml", "manifest");
  }

  public void testHighlighting() throws Throwable {
    doTestHighlighting("hl.xml");
  }

  public void testHighlighting2() throws Throwable {
    doTestHighlighting("hl2.xml");
  }

  public void testTagNameCompletion3() throws Throwable {
    toTestCompletion("tn3.xml", "tn3_after.xml");
  }

  public void testTagNameCompletion4() throws Throwable {
    toTestCompletion("tn4.xml", "tn4_after.xml");
  }

  // Test tag name completion for "supports" prefix
  public void testTagNameCompletion5() throws Throwable {
    doTestCompletionVariants("tag_name_supports.xml", "supports-gl-texture", "supports-screens");
  }

  public void testAttributeValueCompletion1() throws Throwable {
    doTestCompletionVariants("av1.xml", "behind", "landscape", "nosensor", "portrait", "sensor", "unspecified", "user", "fullSensor",
                             "reverseLandscape", "reversePortrait", "sensorLandscape", "sensorPortrait",
                             "fullUser", "locked", "userLandscape", "userPortrait");
  }

  public void testResourceCompletion1() throws Throwable {
    doTestCompletionVariants("av2.xml", "@android:", "@style/style1");
  }

  public void testResourceCompletion2() throws Throwable {
    doTestCompletionVariants("av3.xml", "@android:", "@string/hello", "@string/hello1", "@string/welcome", "@string/welcome1",
                             "@string/itStr");
  }

  public void testResourceCompletion3() throws Throwable {
    doTestCompletionVariants("av4.xml", "@android:", "@color/", "@dimen/", "@drawable/", "@id/", "@string/", "@style/");
  }

  public void testTagNameCompletion1() throws Throwable {
    doTestCompletionVariants("tn1.xml", "uses-permission",  "uses-permission-sdk-23", "uses-sdk", "uses-configuration", "uses-feature");
  }

  public void testSoftTagsAndAttrs() throws Throwable {
    myFixture.disableInspections(new AndroidUnknownAttributeInspection());
    myFixture.disableInspections(new AndroidElementNotAllowedInspection());
    doTestHighlighting("soft.xml");
  }

  public void testUnknownAttribute() throws Throwable {
    doTestHighlighting("unknownAttribute.xml");
  }

  /*public void testNamespaceCompletion() throws Throwable {
    toTestCompletion("ns.xml", "ns_after.xml");
  }*/

  public void testInnerActivityHighlighting() throws Throwable {
    copyFileToProject("A.java", "src/p1/p2/A.java");
    doTestHighlighting(getTestName(false) + ".xml");
  }

  public void testInnerActivityCompletion() throws Throwable {
    copyFileToProject("A.java", "src/p1/p2/A.java");
    doTestCompletionVariants(getTestName(false) + ".xml", "B");
  }

  public void testActivityCompletion1() throws Throwable {
    copyFileToProject("MyActivity1.java", "src/p1/p2/p3/MyActivity1.java");
    copyFileToProject("MyActivity2.java", "src/p1/MyActivity2.java");
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestCompletionVariants(getTestName(false) + ".xml", ".MyActivity", ".p3.MyActivity1", "p1.MyActivity2");
  }

  public void testActivityCompletion2() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestCompletionVariants(getTestName(false) + ".xml", "p2.MyActivity");
  }

  public void testActivityCompletion3() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    copyFileToProject("MyActivity1.java", "src/p1/p2/p3/MyActivity1.java");
    doTestCompletionVariants(getTestName(false) + ".xml", ".MyActivity", ".p3.MyActivity1");
  }

  public void testActivityCompletion4() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    copyFileToProject("MyActivity1.java", "src/p1/p2/p3/MyActivity1.java");
    doTestCompletionVariants(getTestName(false) + ".xml", "MyActivity", "p3.MyActivity1");
  }

  public void testActivityCompletion5() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    copyFileToProject("MyActivity1.java", "src/p1/p2/p3/MyActivity1.java");
    copyFileToProject("MyActivity2.java", "src/p1/MyActivity2.java");
    doTestCompletionVariants(getTestName(false) + ".xml", "MyActivity", "p3.MyActivity1");
  }

  public void testActivityCompletion6() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestCompletion(false);
  }

  public void testParentActivityCompletion1() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    copyFileToProject("MyActivity1.java", "src/p1/p2/p3/MyActivity1.java");
    copyFileToProject("MyActivity2.java", "src/p1/MyActivity2.java");
    doTestCompletionVariants(getTestName(false) + ".xml", ".MyActivity", ".p3.MyActivity1", "p1.MyActivity2");
  }

  public void testBackupAgentCompletion() throws Throwable {
    copyFileToProject("MyBackupAgent.java", "src/p1/p2/MyBackupAgent.java");
    doTestCompletionVariants(getTestName(false) + ".xml", ".MyBackupAgent");
  }

  public void testUsesPermissionCompletion() throws Throwable {
    doTestCompletion(false);
  }

  public void testUsesPermissionCompletion1() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(testFolder + '/' + getTestName(false) + "_after.xml");
  }

  public void testUsesPermissionCompletion2() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(testFolder + '/' + getTestName(false) + "_after.xml");
  }

  public void testUsesPermissionCompletion3() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(testFolder + '/' + getTestName(false) + "_after.xml");
  }

  public void testUsesPermissionCompletion4() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(testFolder + '/' + getTestName(false) + "_after.xml");
  }

  public void testUsesPermissionCompletion5() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(testFolder + '/' + getTestName(false) + "_after.xml");
  }

  public void testUsesPermissionDoc() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("WI");
    doTestExternalDoc("Allows applications to access information about Wi-Fi networks");
  }

  public void testUsesPermissionDoc1() throws Throwable {
    myFixture.configureFromExistingVirtualFile(copyFileToProject(getTestName(false) + ".xml"));
    doTestExternalDoc("Allows applications to access information about Wi-Fi networks");
  }

  public void testIntentActionDoc() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("_BUT");
    doTestExternalDoc("The user pressed the \"call\" button to go to the dialer");
  }

  public void testIntentActionDoc1() throws Throwable {
    myFixture.configureFromExistingVirtualFile(copyFileToProject(getTestName(false) + ".xml"));
    doTestExternalDoc("The user pressed the \"call\" button to go to the dialer");
  }

  public void testIntentActionDoc2() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    doTestExternalDoc("The user pressed the \"call\" button to go to the dialer");
  }

  public void testIntentActionCompletion1() throws Throwable {
    doTestCompletionVariants(getTestName(false) + ".xml", "android.intent.action.CALL", "android.intent.action.CALL_BUTTON");
  }

  public void testIntentActionCompletion2() throws Throwable {
    doTestCompletionVariants(getTestName(false) + ".xml", "android.intent.action.CAMERA_BUTTON",
                             "android.intent.action.NEW_OUTGOING_CALL");
  }

  public void testIntentActionCompletion3() throws Throwable {
    doTestCompletionVariants(getTestName(false) + ".xml");
  }

  // Regression test for http://b.android.com/154004
  public void testIntentActionCompletion4() throws Throwable {
    toTestFirstCompletion("IntentActionCompletion4.xml", "IntentActionCompletion4_after.xml");
  }

  public void testIntentCategoryCompletion1() throws Throwable {
    doTestCompletion(false);
  }

  public void testIntentCategoryCompletion2() throws Throwable {
    doTestCompletion(false);
  }

  // Tests for completion of actions outside of set of constants defined in android.intent.Intent
  // Regression test for http://b.android.com/187026
  public void testTelephonyActionCompletion() throws Throwable {
    toTestCompletion("TelephonyActionCompletion.xml", "TelephonyActionCompletion_after.xml");
  }

  // Test support for tools: namespace attribute completion in manifest files,
  // tools:node in this particular case
  public void testToolsNodeCompletion() throws Throwable {
    toTestCompletion("ToolsManifestMergerCompletion.xml", "ToolsManifestMergerCompletion_after.xml");
  }

  // Test support for value completion of tools:node attribute
  public void testToolsNodeValueCompletion() throws Throwable {
    toTestCompletion("ToolsNodeValueCompletion.xml", "ToolsNodeValueCompletion_after.xml");
  }

  public void testIntentActionsHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testIntentCategoryDoc() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("BRO");
    doTestExternalDoc("The activity should be able to browse the Internet.");
  }

  public void testIntentCategoryDoc1() throws Throwable {
    myFixture.configureFromExistingVirtualFile(copyFileToProject(getTestName(false) + ".xml"));
    doTestExternalDoc("The activity should be able to browse the Internet.");
  }

  public void testIntentCategoryDoc2() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    doTestExternalDoc("To be used as a test");
  }

  public void testApplicationNameCompletion() throws Throwable {
    copyFileToProject("MyApplication.java", "src/p1/p2/MyApplication.java");
    doTestCompletion(false);
  }

  public void testManageSpaceActivity() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestCompletion(false);
  }

  public void testInstrumentationRunner() throws Throwable {
    doTestHighlighting(getTestName(false) + ".xml");
  }

  public void testInstrumentationRunner1() throws Throwable {
    doTestHighlighting(getTestName(false) + ".xml");
  }

  public void testInstrumentationRunner2() throws Throwable {
    doTestCompletion(false);
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    if ("testInstrumentationRunner1".equals(getName()) ||
        "testInstrumentationRunner2".equals(getName())) {
      addModuleWithAndroidFacet(projectBuilder, modules, "module1", false);
    }
  }

  public void testIntentsCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testIntentsCompletion2() throws Throwable {
    doTestCompletion();
  }

  public void testCompletionInManifestTag() throws Throwable {
    doTestCompletion();
  }

  public void testActivityAlias() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestHighlighting();
  }

  public void testActivityAlias1() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestHighlighting();
  }

  public void testActivityAlias2() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestHighlighting();
  }

  public void testActivityAlias3() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestHighlighting();
  }

  public void testIntegerRefAsEnumValue() throws Throwable {
    copyFileToProject("myIntResource.xml", "res/values/myIntResource.xml");
    doTestCompletion();
  }

  public void testJavaHighlighting() throws Throwable {
    copyFileToProject("PermissionsManifest.xml", "AndroidManifest.xml");
    copyFileToProject("Manifest.java", "src/p1/p2/Manifest.java");
    doTestJavaHighlighting("p1.p2");
  }

  public void testAndroidPrefixCompletion() throws Throwable {
    // do not complete prefix in manifest because there is not many attributes
    doTestAndroidPrefixCompletion(null);
  }

  public void testNamespaceCompletion() throws Throwable {
    doTestNamespaceCompletion(true, true, true, false);
  }

  public void testNamespaceCompletion1() throws Throwable {
    doTestNamespaceCompletion(true, false, true, false);
  }

  public void testCompatibleScreensCompletion() throws Throwable {
    doTestCompletion();
  }

  public void testCompatibleScreensHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testHexInteger() throws Throwable {
    doTestHighlighting();
  }

  public void testMinSdkVersionAttributeValueCompletion() throws Throwable {
    doTestSdkVersionAttributeValueCompletion();
  }

  public void testTargetSdkVersionAttributeValueCompletion() throws Throwable {
    doTestSdkVersionAttributeValueCompletion();
  }

  public void testMaxSdkVersionAttributeValueCompletion() throws Throwable {
    doTestSdkVersionAttributeValueCompletion();
  }

  public void testSpellchecker1() throws Throwable {
    myFixture.enableInspections(SpellCheckingInspection.class);
    doTestHighlighting();
  }

  public void testSpellchecker2() throws Throwable {
    doTestSpellcheckerQuickFixes();
  }

  public void testMetadataCompletion1() throws Throwable {
    copyFileToProject("MyActivity1.java", "src/p1/p2/p3/MyActivity1.java");
    copyFileToProject("MyActivity2.java", "src/p1/MyActivity2.java");
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestCompletionVariants(getTestName(true) + ".xml", ".MyActivity2", ".p2.MyActivity", ".p2.p3.MyActivity1");
  }

  public void testMetadataCompletion2() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml",
                             "@android:", "@color/", "@dimen/", "@drawable/", "@id/", "@string/", "@style/");
  }

  public void testMetadataCompletion3() throws Throwable {
    copyFileToProject("MyActivity1.java", "src/p1/p2/p3/MyActivity1.java");
    copyFileToProject("MyActivity2.java", "src/p1/MyActivity2.java");
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestCompletionVariants(getTestName(true) + ".xml", "p1.MyActivity2", "p1.p2.MyActivity", "p1.p2.p3.MyActivity1");
  }


  private void doTestSdkVersionAttributeValueCompletion() throws Throwable {
    final ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
    final Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        projectJdkTable.addJdk(sdk);
      }
    });
    try {
      doTestCompletionVariants(getTestName(true) + ".xml", "1", "2", "3", "4", "5", "6", "7",
                               "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23");
    }
    finally {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          projectJdkTable.removeJdk(sdk);
        }
      });
    }
  }
}
