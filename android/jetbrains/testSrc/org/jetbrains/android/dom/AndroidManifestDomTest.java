package org.jetbrains.android.dom;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_APP;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.tools.idea.testing.AndroidTestUtils;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import java.util.List;
import javaslang.collection.Array;
import org.jetbrains.android.dom.inspections.AndroidElementNotAllowedInspection;
import org.jetbrains.android.dom.inspections.AndroidUnknownAttributeInspection;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.UsesFeature;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

public class AndroidManifestDomTest extends AndroidDomTestCase {
  private static final String API_LEVELS_URL = "https://developer.android.com/guide/topics/manifest/uses-sdk-element.html#ApiLevels";

  public AndroidManifestDomTest() {
    super("dom/manifest");
  }

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return SdkConstants.FN_ANDROID_MANIFEST_XML;
  }

  public void testAttributeTagHighlighting() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "  <attribute android:tag=\"true\" android:label=\"true\"/>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting();
  }

  public void testAttributeTagCompletion() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "  <<caret>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).contains("attribute");
  }

  public void testAttributeAttributesCompletion() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "  <attribute <caret>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsAllOf("android:label", "android:tag");
  }

  public void testPropertyHighlighting() {
    // UNRESOLVED errors do not relate to the <property> tag which is the purpose of the test.
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      //language=XML
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "        package=\"p1.p2\" >\n" +
      "  <application>\n" +
      "    <property android:name=\"android.cts.PROPERTY_STRING_VIA_RESOURCE\" android:value=\"foo\" />\n" +
      "    <activity android:name=\"<error>UNRESOLVED</error>\">\n" +
      "        <property android:name=\"android.cts.PROPERTY_ACTIVITY\" android:value=\"foo\" />\n" +
      "    </activity>\n" +
      "    <activity-alias android:name=\"UNRESOLVED\" android:targetActivity=\"<error>UNRESOLVED</error>\">\n" +
      "        <property android:name=\"android.cts.PROPERTY_ACTIVITY_ALIAS\" android:value=\"foo\" />\n" +
      "    </activity-alias>\n" +
      "    <provider android:name=\"<error>UNRESOLVED</error>\" android:authorities=\"UNRESOLVED\">\n" +
      "      <property android:name=\"android.cts.PROPERTY_PROVIDER\" android:value=\"foo\" />\n" +
      "    </provider>\n" +
      "    <receiver android:name=\"<error>UNRESOLVED</error>\">\n" +
      "        <property android:name=\"android.cts.PROPERTY_RECEIVER\" android:value=\"foo\" />\n" +
      "    </receiver>\n" +
      "    <service android:name=\"<error>UNRESOLVED</error>\">\n" +
      "        <property android:name=\"android.cts.PROPERTY_SERVICE\" android:value=\"foo\" />\n" +
      "    </service>\n" +
      "  </application>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting();
  }

  public void testPropertyTagCompletion() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "    <application>\n" +
      "        <<caret>\n" +
      "    </application>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).contains("property");
  }

  public void testPropertyAttributeCompletion() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "    <application>\n" +
      "        <property <caret>/>\n" +
      "    </application>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsExactly("android:name", "android:value", "android:resource");
  }


  public void testPropertyResourceAttributeValueCompletion() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "    <application>\n" +
      "        <property android:name=\"android.cts.PROPERTY_RESOURCE_XML\" android:resource=\"@<caret>\"/>\n" +
      "    </application>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();

    //Contains every accessible resource.
    assertThat(myFixture.getLookupElementStrings()).containsAllOf("@android:", "@color/color0", "@color/color1");
  }


  public void testProfileableHighlighting() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "    <application>\n" +
      "        <profileable android:shell=\"true\"/>\n" +
      "    </application>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting();
  }

  public void testProfileableTagCompletion() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "    <application>\n" +
      "        <<caret>\n" +
      "    </application>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).contains("profileable");
  }

  public void testProfileableAttributeCompletion() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "    <application>\n" +
      "        <profileable <caret>/>\n" +
      "    </application>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsExactly("android:shell");
  }

  public void testOverlayTagCompletion() throws Throwable {
    toTestCompletion("overlay.xml", "overlay_after.xml");
  }

  public void testOverlayAttributeTagCompletion() throws Throwable {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.a\">\n" +
      "    <overlay android:tar<caret>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();

    assertThat(myFixture.getLookupElementStrings()).containsExactly("targetName", "targetPackage");
  }

  public void testOverlayHighlighting() {
    // Asserting that using overlay tag, and attributes does not produce a warning from AndroidDomInspection.
    // Attribute values do not need to resolve to anything.
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "          package=\"p1.a\">\n" +
      "  <overlay android:targetPackage=\"doesn't matter\" android:targetName=\"doesn't matter\">\n" +
      "  </overlay>\n" +
      "</manifest>").getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting();
  }

  public void testQueriesHighlighting() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "    <queries>\n" +
      "        <package android:name=\"com.android.internal.util\"/>\n" +
      "    </queries>\n" +
      "    <queries>\n" +
      "        <intent>\n" +
      "            <action android:name=\"android.intent.action.BATTERY_LOW\"/>\n" +
      "            <category android:name=\"android.intent.category.BROWSABLE\"/>\n" +
      "            <data android:mimeType=\"basic string\"/>\n" +
      "        </intent>\n" +
      "    </queries>\n" +
      "    <queries>\n" +
      "        <provider android:authorities=\"p1.p2\"/>\n" +
      "    </queries>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting();
  }

  public void testQueriesCompletion() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "    <queries>\n" +
      "        <<caret>\n" +
      "    </queries>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsExactly("package", "intent", "provider");
  }

  public void testQueriesSubtagCompletion() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "    <queries>\n" +
      "        <package />\n" +
      "    </queries>\n" +
      "    <queries>\n" +
      "        <intent>\n" +
      "           <<caret>\n" +
      "        </intent>\n" +
      "    </queries>\n" +
      "    <queries>\n" +
      "        <provider />\n" +
      "    </queries>\n" +
      "</manifest>").getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    // Testing Intent subtags
    assertThat(myFixture.getLookupElementStrings()).containsExactly("data", "action", "category");

    // Testing Package tag attributes
    AndroidTestUtils.moveCaret(myFixture, "<package |/>");
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsExactly("android:name");

    // Testing Provider tag attributes
    AndroidTestUtils.moveCaret(myFixture, " <provider |/>");
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsExactly("android:authorities");
  }

  public void testQueriesCategoryNameCompletion() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "    <queries>\n" +
      "        <intent>\n" +
      "            <category android:name=\"<caret>\"/>\n" +
      "        </intent>\n" +
      "    </queries>\n" +
      "</manifest>").getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).contains("android.intent.category.BROWSABLE");
  }

  public void testUsesFeatureCompletion() {
    VirtualFile file = myFixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"p1.p2\">\n" +
      "    <uses-feature android:name=\"<caret>\" android:required=\"\"/>\n" +
      "</manifest>").getVirtualFile();

    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings())
      .containsAllIn(Array.of("android.hardware.audio.low_latency", "android.hardware.camera", "android.hardware.telephony"));

    AndroidTestUtils.moveCaret(myFixture, "android:required=\"|\"");
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsAllIn(Array.of("true", "false"));
  }

  public void testAttributeNameCompletion1() throws Throwable {
    doTestCompletionVariantsContains("an1.xml", "android:icon", "android:label", "android:priority", "android:logo", "replace");
  }

  public void testAttributeNameCompletion2() throws Throwable {
    doTestCompletionVariantsContains("an2.xml", "debuggable", "description", "hasCode", "vmSafeMode");
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

  public void testHighlighting3() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    copyFileToProject("bools.xml", "res/values-v23/bools.xml");
    doTestHighlighting("hl3.xml");
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
    doTestCompletionVariants("tn1.xml", "uses-permission",  "uses-permission-sdk-23", "uses-sdk", "uses-configuration",
                             "uses-feature", "uses-split");
  }

  public void testNavGraphCompletion() throws Throwable {
    doTestCompletionVariantsContains("navgraph1.xml", "nav-graph");
  }

  public void testNavGraphAttributeCompletion() throws Throwable {
    doTestCompletionVariantsContains("navgraph2.xml", "android:value");
  }

  public void testNavGraphValueCompletion() throws Throwable {
    copyFileToProject("nav_main.xml", "res/navigation/nav_main.xml");
    doTestCompletionVariantsContains("navgraph3.xml", "@navigation/nav_main");
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

  public void testUsesSplits() throws Throwable {
    doTestHighlighting();
  }

  public void testUsesPermissionCompletion() throws Throwable {
    doTestCompletion(false);
  }

  public void testUsesPermissionCompletion1() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(myTestFolder + '/' + getTestName(false) + "_after.xml");
  }

  public void testUsesPermissionCompletion2() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(myTestFolder + '/' + getTestName(false) + "_after.xml");
  }

  public void testUsesPermissionCompletion3() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(myTestFolder + '/' + getTestName(false) + "_after.xml");
  }

  public void testUsesPermissionCompletion4() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(myTestFolder + '/' + getTestName(false) + "_after.xml");
  }

  public void testUsesPermissionCompletion5() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(myTestFolder + '/' + getTestName(false) + "_after.xml");
  }

  public void testUsesPermissionCompletion6() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(myTestFolder + '/' + getTestName(false) + "_after.xml");
  }

  /* b/115735357: We don't currently have a valid docs package, nor do we plan to have one again soon.
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
  */

  public void testUsesPermissionDoc2() throws Throwable {
    myFixture.configureFromExistingVirtualFile(copyFileToProject(getTestName(false) + ".xml"));
    PsiElement originalElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    doTestDoc("Removed in <a href=\"" + API_LEVELS_URL + "\">API level 24</a>");
  }

  /* b/115735357: We don't currently have a valid docs package, nor do we plan to have one again soon.
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
  */

  public void testIntentActionCompletion1() throws Throwable {
    doTestCompletionVariants(getTestName(false) + ".xml",
                             "android.intent.action.CALL",
                             "android.intent.action.CALL_BUTTON",
                             "android.intent.action.CARRIER_SETUP");
  }

  public void testIntentActionCompletion2() throws Throwable {
    doTestCompletionVariants(getTestName(false) + ".xml", "android.intent.action.CAMERA_BUTTON",
                             "android.intent.action.NEW_OUTGOING_CALL");
  }

  public void testIntentActionCompletion3() throws Throwable {
    toTestFirstCompletion("IntentActionCompletion3.xml", "IntentActionCompletion3_after.xml");
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

  /* b/144507473
  public void testIntentCategoryDoc() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("BRO");
    doTestExternalDoc("The activity should be able to browse the Internet.");
  }
  */

  /* b/115735357: We don't currently have a valid docs package, nor do we plan to have one again soon.
  public void testIntentCategoryDoc1() throws Throwable {
    myFixture.configureFromExistingVirtualFile(copyFileToProject(getTestName(false) + ".xml"));
    doTestExternalDoc("The activity should be able to browse the Internet.");
  }

  public void testIntentCategoryDoc2() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      copyFileToProject(getTestName(false) + ".xml"));
    doTestExternalDoc("To be used as a test");
  }
  */

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
      addModuleWithAndroidFacet(projectBuilder, modules, "module1", PROJECT_TYPE_APP);
    }
  }

  public void testIntentsCompletion1() throws Throwable {
    toTestFirstCompletion("intentsCompletion1.xml", "intentsCompletion1_after.xml");
  }

  public void testIntentsCompletion2() throws Throwable {
    doTestCompletion();
  }

  /**
   * Test that "data" tag is completed as a subtag of "intent-filter"
   */
  public void testDataTagCompletion() throws Throwable {
    doTestCompletion();
  }

  /**
   * Test that "path" attribute prefix inside "data" tag leads to correct completion results
   */
  public void testDataAttributeCompletion() throws Throwable {
    doTestCompletionVariants("dataAttributeCompletion.xml", "android:path", "android:pathPrefix", "android:pathPattern",
                             "android:pathSuffix", "android:pathAdvancedPattern");
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
    doTestJavaHighlighting("p1.p2");
  }

  public void testNamespaceCompletion() throws Throwable {
    doTestNamespaceCompletion(SdkConstants.DIST_URI);
  }

  public void testNamespaceCompletion1() throws Throwable {
    doTestNamespaceCompletion(SdkConstants.DIST_URI);
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

  public void testContentProviderIntentFilter() throws Throwable {
    copyFileToProject("MyDocumentsProvider.java", "src/p1/p2/MyDocumentsProvider.java");
    doTestHighlighting();
  }

  public void testAddUsesFeatureTag() throws Throwable {
    VirtualFile manifestFile = copyFileToProject("AddUsesFeature.xml");
    myFixture.configureFromExistingVirtualFile(manifestFile);

    Manifest manifest = AndroidUtils.loadDomElement(myModule, manifestFile, Manifest.class);
    assertNotNull(manifest);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      UsesFeature feature = manifest.addUsesFeature();
      feature.getName().setStringValue("android.hardware.type.watch");
    });

    myFixture.checkResultByFile(myTestFolder + '/' + "AddUsesFeature_after.xml");
  }

  private void doTestSdkVersionAttributeValueCompletion() throws Throwable {
      doTestCompletionVariants(getTestName(true) + ".xml", "1", "2", "3", "4", "5", "6", "7",
                               "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25",
                               "26", "27", "28", "29", "30", "31", "32", "33");
  }
}
