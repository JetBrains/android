/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.editors.manifest;

import com.android.SdkConstants;
import com.android.manifmerger.Actions;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.lint.detector.api.Lint;
import com.android.utils.DomExtensions;
import com.android.utils.PositionXmlParser;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.TAG_APPLICATION;

public class ManifestUtilsTest extends AndroidTestCase {

  private Element activity;
  private Attr name;
  private Attr label;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    XmlFile overlay = getDoc("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                     "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                     "<application>\n" +
                     "<activity android:name=\"my.Activity\" android:label=\"hello\"/>" +
                     "</application>\n" +
                     "</manifest>");
    XmlTag root = overlay.getRootTag();
    assert root != null;

    Document document = PositionXmlParser.parse(overlay.getText());
    activity = Lint.getChildren(Lint.getChildren(document.getDocumentElement()).get(0)).get(0);

    name = activity.getAttributeNodeNS(ANDROID_URI, "name");
    label = activity.getAttributeNodeNS(ANDROID_URI, "label");
  }

  private XmlFile getDoc(String text) {
    return (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText(SdkConstants.FN_ANDROID_MANIFEST_XML, XMLLanguage.INSTANCE, text);
  }

  private void toolsRemove(final @NotNull String input, final @NotNull Node item, @NotNull String result) {
    final XmlFile file = getDoc(input);
    WriteCommandAction.writeCommandAction(getProject(), file).run(() -> ManifestUtils.toolsRemove(file, item));
    assertEquals(result, file.getText());
  }

  public void testRemoveTag() throws Exception {
    // no app
    toolsRemove("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "</manifest>",
                activity,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:tools=\"http://schemas.android.com/tools\" xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "    <application>\n" +
                "        <activity android:name=\"my.Activity\" tools:node=\"remove\" />\n" +
                "    </application>\n" +
                "</manifest>");

    // no activity
    toolsRemove("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity android:name=\"other.Activity\"/>\n" +
                "</application>\n" +
                "</manifest>",
                activity,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:tools=\"http://schemas.android.com/tools\" xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity android:name=\"my.Activity\" tools:node=\"remove\" />\n" +
                "    <activity android:name=\"other.Activity\"/>\n" +
                "</application>\n" +
                "</manifest>");

    // no app, remove by attribute
    toolsRemove("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "</manifest>",
                name,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:tools=\"http://schemas.android.com/tools\" xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "    <application>\n" +
                "        <activity android:name=\"my.Activity\" tools:node=\"remove\" />\n" +
                "    </application>\n" +
                "</manifest>");

    // no activity, remove by attribute
    toolsRemove("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity android:name=\"other.Activity\"/>\n" +
                "</application>\n" +
                "</manifest>",
                name,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:tools=\"http://schemas.android.com/tools\" xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity android:name=\"my.Activity\" tools:node=\"remove\" />\n" +
                "    <activity android:name=\"other.Activity\"/>\n" +
                "</application>\n" +
                "</manifest>");
  }

  public void testRemoveAttribute() throws Exception {
    // no app
    toolsRemove("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "</manifest>",
                label,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:tools=\"http://schemas.android.com/tools\" xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "    <application>\n" +
                "        <activity android:name=\"my.Activity\" tools:remove=\"android:label\" />\n" +
                "    </application>\n" +
                "</manifest>");

    // no activity
    toolsRemove("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity android:name=\"other.Activity\"/>\n" +
                "</application>\n" +
                "</manifest>",
                label,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:tools=\"http://schemas.android.com/tools\" xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity android:name=\"my.Activity\" tools:remove=\"android:label\" />\n" +
                "    <activity android:name=\"other.Activity\"/>\n" +
                "</application>\n" +
                "</manifest>");

    // with activity
    toolsRemove("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity android:name=\"my.Activity\" android:windowSoftInputMode=\"stateAlwaysHidden\"/>\n" +
                "    <activity android:name=\"other.Activity\"/>\n" +
                "</application>\n" +
                "</manifest>",
                label,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:tools=\"http://schemas.android.com/tools\" xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity android:name=\"my.Activity\" android:windowSoftInputMode=\"stateAlwaysHidden\"\n" +
                "        tools:remove=\"android:label\" />\n" +
                "    <activity android:name=\"other.Activity\"/>\n" +
                "</application>\n" +
                "</manifest>");

    // with activity AND existing tools:remove tag
    toolsRemove("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity android:name=\"my.Activity\" android:windowSoftInputMode=\"stateAlwaysHidden\" tools:remove=\"android:configChanges\"/>\n" +
                "    <activity android:name=\"other.Activity\"/>\n" +
                "</application>\n" +
                "</manifest>",
                label,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:tools=\"http://schemas.android.com/tools\" xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity android:name=\"my.Activity\" android:windowSoftInputMode=\"stateAlwaysHidden\" tools:remove=\"android:configChanges,android:label\"/>\n" +
                "    <activity android:name=\"other.Activity\"/>\n" +
                "</application>\n" +
                "</manifest>");
  }

  public void testRemoveNamespace() throws Exception {

    // setting on new tag
    toolsRemove("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:t=\"http://schemas.android.com/tools\" xmlns:a=\"http://schemas.android.com/apk/res/android\">\n" +
                "</manifest>",
                activity,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:t=\"http://schemas.android.com/tools\" xmlns:a=\"http://schemas.android.com/apk/res/android\">\n" +
                "    <application>\n" +
                "        <activity a:name=\"my.Activity\" t:node=\"remove\" />\n" +
                "    </application>\n" +
                "</manifest>");

    // setting on new tag for attribute
    toolsRemove("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:t=\"http://schemas.android.com/tools\" xmlns:a=\"http://schemas.android.com/apk/res/android\">\n" +
                "</manifest>",
                label,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:t=\"http://schemas.android.com/tools\" xmlns:a=\"http://schemas.android.com/apk/res/android\">\n" +
                "    <application>\n" +
                "        <activity a:name=\"my.Activity\" t:remove=\"android:label\" />\n" +
                "    </application>\n" +
                "</manifest>");

    // setting on existing tag
    toolsRemove("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:t=\"http://schemas.android.com/tools\" xmlns:a=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity a:name=\"my.Activity\" a:windowSoftInputMode=\"stateAlwaysHidden\"/>\n" +
                "</application>\n" +
                "</manifest>",
                label,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:t=\"http://schemas.android.com/tools\" xmlns:a=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity a:name=\"my.Activity\" a:windowSoftInputMode=\"stateAlwaysHidden\"\n" +
                "        t:remove=\"android:label\" />\n" +
                "</application>\n" +
                "</manifest>");

    // updating a existing tag
    toolsRemove("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:t=\"http://schemas.android.com/tools\" xmlns:a=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity a:name=\"my.Activity\" a:windowSoftInputMode=\"stateAlwaysHidden\" t:remove=\"android:configChanges\"/>\n" +
                "</application>\n" +
                "</manifest>",
                label,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:t=\"http://schemas.android.com/tools\" xmlns:a=\"http://schemas.android.com/apk/res/android\">\n" +
                "<application>\n" +
                "    <activity a:name=\"my.Activity\" a:windowSoftInputMode=\"stateAlwaysHidden\" t:remove=\"android:configChanges,android:label\"/>\n" +
                "</application>\n" +
                "</manifest>");
  }

  public void testGetRecordsForIntentFilters() throws Exception {
    // Regression test for https://issuetracker.google.com/335824315
    //
    // Previously, child elements of intent filters would have the location of the parent intent
    // filter element (due to a workaround). Without the workaround, the location of an
    // action/category/data element would map to the first instance of that element, regardless of
    // parent intent filter or activity. Also, duplicate intent filters across different activities
    // would be incorrectly mapped to the first instance.
    //
    // We check that the line number of each element under application is increasing to ensure that
    // elements are not incorrectly mapped back to previous elements.
    MergedManifestSnapshot mergedManifest =
      getMergedManifest(
        "<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
        "    package='com.example.app1'>\n" +
        "    <application android:name=\"com.example.app1.TheApp\">\n" +
        "        <activity android:name=\"com.example.app1.Activity1\">\n" +
        "            <intent-filter>\n" + // A
        "                <action android:name=\"android.intent.action.VIEW\"/>\n" +
        "                <category android:name=\"android.intent.category.DEFAULT\"/>\n" +
        "                <category android:name=\"android.intent.category.BROWSABLE\"/>\n" +
        "                <data android:scheme=\"https\"/>\n" +
        "                <data android:host=\"www.example.com\"/>\n" +
        "                <data android:path=\"/\"/>\n" +
        "            </intent-filter>\n" +
        "            <intent-filter>\n" + // B
        "                <action android:name=\"android.intent.action.VIEW\"/>\n" +
        "                <category android:name=\"android.intent.category.DEFAULT\"/>\n" +
        "                <category android:name=\"android.intent.category.BROWSABLE\"/>\n" +
        "                <data android:scheme=\"https\"/>\n" +
        "                <data android:host=\"www.example2.com\"/>\n" + // different host to A
        "                <data android:path=\"/\"/>\n" +
        "            </intent-filter>\n" +
        "        </activity>\n" +
        "        <activity android:name=\"com.example.app1.Activity2\">\n" +
        "            <intent-filter>\n" + // Same as A, but under a different activity.
        "                <action android:name=\"android.intent.action.VIEW\"/>\n" +
        "                <category android:name=\"android.intent.category.DEFAULT\"/>\n" +
        "                <category android:name=\"android.intent.category.BROWSABLE\"/>\n" +
        "                <data android:scheme=\"https\"/>\n" +
        "                <data android:host=\"www.example.com\"/>\n" +
        "                <data android:path=\"/\"/>\n" +
        "            </intent-filter>\n" +
        "        </activity>\n" +
        "    </application>\n" +
        "</manifest>\n");

    Document document = mergedManifest.getDocument();
    assertNotNull(document);
    Element manifest = document.getDocumentElement();
    assertNotNull(manifest);
    Element application = (Element) manifest.getElementsByTagName(TAG_APPLICATION).item(0);
    // Use arrays so we can modify within lambda.
    final int[] lineNumber = { ManifestUtils.getRecords(mergedManifest, application).get(0).getActionLocation().getPosition().getStartLine() };
    assertTrue(lineNumber[0] > 0);
    final int[] count = { 0 };
    DomExtensions.visitElements(application, (Element element) -> {
      if (TAG_APPLICATION.equals(element.getLocalName())) return false; // continue
      List<? extends Actions.Record> records = ManifestUtils.getRecords(mergedManifest, element);
      assertEquals(1, records.size());
      int nextLineNumber = records.get(0).getActionLocation().getPosition().getStartLine();
      assertTrue(nextLineNumber > lineNumber[0]);
      lineNumber[0] = nextLineNumber;
      ++count[0];
      // Return false to continue visiting elements.
      return false;
    });
    assertEquals(23, count[0]);
  }

  /**
   * Test that getNodeKey() returns null for an unexpected element.
   * Regression test for b/73329785; previously getNodeKey() would throw an AssertionError for an unexpected element.
   */
  public void testGetNodeKeyForUnexpectedElement() throws Exception {

    MergedManifestSnapshot mergedManifest =
      getMergedManifest(
        "<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
        "    package='com.example.app1'>\n" +
        "    <application android:name=\"com.example.app1.TheApp\">\n" +
        "        <activity android:name=\"com.example.app1.Activity1\">\n" +
        "            <foo/>\n" +
        "        </activity>\n" +
        "    </application>\n" +
        "</manifest>\n");

    Element unexpectedElement = (Element) mergedManifest.getActivities().get(0).getElementsByTagName("foo").item(0);

    mergedManifest =
      getMergedManifest(
        "<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
        "    package='com.example.app1'>\n" +
        "</manifest>\n");

    assertEquals(null, ManifestUtils.getNodeKey(mergedManifest, unexpectedElement));
  }


  private MergedManifestSnapshot getMergedManifest(String manifestContents) throws Exception {
    String path = "AndroidManifest.xml";

    final VirtualFile manifest = myFixture.findFileInTempDir(path);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {

        if (manifest != null) {
          try {
            manifest.delete(this);
          }
          catch (IOException e) {
            fail("Could not delete manifest");
          }
        }
      }
    });

    myFixture.addFileToProject(path, manifestContents);

    return MergedManifestManager.getMergedManifest(myModule).get();
  }

}
