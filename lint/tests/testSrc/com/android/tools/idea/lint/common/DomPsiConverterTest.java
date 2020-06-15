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
package com.android.tools.idea.lint.common;

import static com.android.SdkConstants.ANDROID_URI;

import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.utils.XmlUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import java.util.concurrent.atomic.AtomicReference;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class DomPsiConverterTest extends UsefulTestCase {
  protected JavaCodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected Project getProject() {
    return myFixture.getProject();
  }

  private static final String MANIFEST =
    "" +
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
    "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
    "          package=\"p1.p2\">\n" +
    "    <application android:icon=\"@drawable/icon\">\n" +
    "    </application>\n" +
    "</manifest>";

  public void testBasic() {
    XmlFile xmlFile = (XmlFile)myFixture.configureByText("AndroidManifest.xml", MANIFEST);
    VirtualFile file = xmlFile.getVirtualFile();
    assertNotNull(file);
    assertTrue(file.exists());
    Project project = getProject();
    assertNotNull(project);
    Document domDocument = DomPsiConverter.convert(xmlFile);
    assertNotNull(domDocument);

    // Pretty print; requires DOM iteration
    String formatted = XmlPrettyPrinter.prettyPrint(domDocument, true);

    // Compare to plain DOM implementation pretty printed
    @SuppressWarnings("ConstantConditions")
    String expected = XmlPrettyPrinter.prettyPrint(XmlUtils.parseDocumentSilently(xmlFile.getText(), true), true);

    assertEquals(expected, formatted);

    // Check some additional operations
    NodeList elementsByTagName = domDocument.getElementsByTagName("application");
    assertEquals(1, elementsByTagName.getLength());
    assertEquals("@drawable/icon", elementsByTagName.item(0).getAttributes().getNamedItemNS(ANDROID_URI, "icon").getNodeValue());
  }

  public void testAsyncAccess() throws InterruptedException {
    XmlFile xmlFile = (XmlFile)myFixture.configureByText("AndroidManifest.xml", MANIFEST);
    VirtualFile file = xmlFile.getVirtualFile();
    assertNotNull(file);
    assertTrue(file.exists());
    Project project = getProject();
    assertNotNull(project);
    final Document domDocument = DomPsiConverter.convert(xmlFile);
    assertNotNull(domDocument);

    // Perform iteration on a different thread without read access
    final AtomicReference<String> formattedHolder = new AtomicReference<>();
    Thread thread = new Thread("dom psi") {
      @Override
      public void run() {
        try {
          assertFalse(ApplicationManager.getApplication().isReadAccessAllowed());
          String formatted = ReadAction.compute(() -> XmlPrettyPrinter.prettyPrint(domDocument, true));
          formattedHolder.set(formatted);
        } catch (Exception e) {
          e.printStackTrace();
          fail(e.toString());
        }
      }
    };
    thread.start();
    thread.join();

    // Compare to plain DOM implementation pretty printed
    @SuppressWarnings("ConstantConditions")
    String expected = XmlPrettyPrinter.prettyPrint(XmlUtils.parseDocumentSilently(xmlFile.getText(), true), true);

    String formatted = formattedHolder.get();
    assertEquals(expected, formatted);

    // Check some additional operations
    NodeList elementsByTagName = domDocument.getElementsByTagName("application");
    assertEquals(1, elementsByTagName.getLength());
    assertEquals("@drawable/icon", elementsByTagName.item(0).getAttributes().getNamedItemNS(ANDROID_URI, "icon").getNodeValue());
  }
}
