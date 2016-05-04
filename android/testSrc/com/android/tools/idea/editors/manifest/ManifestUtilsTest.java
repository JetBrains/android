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
import com.android.tools.lint.detector.api.LintUtils;
import com.android.utils.PositionXmlParser;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static com.android.SdkConstants.ANDROID_URI;

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
    activity = LintUtils.getChildren(LintUtils.getChildren(document.getDocumentElement()).get(0)).get(0);

    name = activity.getAttributeNodeNS(ANDROID_URI, "name");
    label = activity.getAttributeNodeNS(ANDROID_URI, "label");
  }

  private XmlFile getDoc(String text) {
    return (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText(SdkConstants.FN_ANDROID_MANIFEST_XML, XMLLanguage.INSTANCE, text);
  }

  private void toolsRemove(final @NotNull String input, final @NotNull Node item, @NotNull String result) {
    final XmlFile file = getDoc(input);
    new WriteCommandAction.Simple(getProject(), file) {
      @Override
      protected void run() throws Throwable {
        ManifestUtils.toolsRemove(file, item);
      }
    }.execute();
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
                "        <activity android:name=\"my.Activity\" tools:node=\"remove\"/>\n" +
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
                "    <activity android:name=\"my.Activity\" tools:node=\"remove\"/>\n" +
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
                "        <activity android:name=\"my.Activity\" tools:node=\"remove\"/>\n" +
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
                "    <activity android:name=\"my.Activity\" tools:node=\"remove\"/>\n" +
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
                "        <activity android:name=\"my.Activity\" tools:remove=\"android:label\"/>\n" +
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
                "    <activity android:name=\"my.Activity\" tools:remove=\"android:label\"/>\n" +
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
                "    <activity android:name=\"my.Activity\" android:windowSoftInputMode=\"stateAlwaysHidden\" tools:remove=\"android:label\"/>\n" +
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
                "        <activity a:name=\"my.Activity\" t:node=\"remove\"/>\n" +
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
                "        <activity a:name=\"my.Activity\" t:remove=\"android:label\"/>\n" +
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
                "    <activity a:name=\"my.Activity\" a:windowSoftInputMode=\"stateAlwaysHidden\" t:remove=\"android:label\"/>\n" +
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
}
