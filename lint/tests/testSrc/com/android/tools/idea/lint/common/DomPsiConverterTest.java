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

import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static org.junit.Assert.assertSame;

import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.utils.PositionXmlParser;
import com.android.utils.XmlUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
    Node appTag = elementsByTagName.item(0);
    Node iconAttr = appTag.getAttributes().getNamedItemNS(ANDROID_URI, "icon");
    assertEquals("@drawable/icon", iconAttr.getNodeValue());

    for (Node node : List.of(domDocument, appTag, iconAttr)) {
      assertEquals(node.lookupNamespaceURI(ANDROID_NS_NAME), ANDROID_URI);
      assertEquals(node.lookupPrefix(ANDROID_URI), ANDROID_NS_NAME);
    }
  }

  public void testRootNodes() {
    // Ensure that we also represent nodes outside of the document element
    String s = "<!-- some comment -->\n" +
               "<resources>\n" +
               "    <string name=\"app_name\">Name</string>\n" +
               "</resources>\n" +
               "<!-- some final comment -->\n";
    XmlFile xmlFile = (XmlFile)myFixture.configureByText("res/values/strings.xml", s);
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
    assertEquals(" some comment ", ((Comment)domDocument.getFirstChild()).getData());
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

  public void testNonDomNodeRange() throws Exception {
    // Checks that the position computations are correct when the
    // document is NOT backed by PSI. This is relevant when a lint
    // reports problems in the merged manifest and maps it back
    // to the corresponding source.
    String xml = "" +
                      "<test>\n" +
                      "    <element name=\"MyName\" type=\"string\">\n" +
                      "       <child name=\"child\" type=\"dimen\"/>\n" +
                      "    </element>\n" +
                      "</test>\n";

    XmlFile xmlFile = (XmlFile)myFixture.configureByText("test.xml", xml);
    VirtualFile file = xmlFile.getVirtualFile();
    assertNotNull(file);
    assertTrue(file.exists());
    Project project = getProject();
    assertNotNull(project);
    final Document domDocument = DomPsiConverter.convert(xmlFile);
    assertNotNull(domDocument);

    Element psiElement = (Element)domDocument.getElementsByTagName("element").item(0);
    Node psiAttribute = psiElement.getAttributeNode("type");

    // Merged manifest will create a separate parse, not PSI based:
    Document document = PositionXmlParser.parse(xml, false);
    assertNotNull(document);
    Element domElement = (Element)document.getElementsByTagName("element").item(0);
    Node domAttribute = domElement.getAttributeNode("type");

    assertEquals(DomPsiConverter.getTextRange(psiElement), DomPsiConverter.getTextRange(domElement));
    assertEquals(DomPsiConverter.getTextRange(psiAttribute), DomPsiConverter.getTextRange(domAttribute));

    assertEquals(DomPsiConverter.getTextNameRange(psiElement), DomPsiConverter.getTextNameRange(domElement));
    assertEquals(DomPsiConverter.getTextNameRange(psiAttribute), DomPsiConverter.getTextNameRange(domAttribute));

    assertEquals(DomPsiConverter.getTextValueRange(psiElement), DomPsiConverter.getTextValueRange(domElement));
    assertEquals(DomPsiConverter.getTextValueRange(psiAttribute), DomPsiConverter.getTextValueRange(domAttribute));
  }

  public void testGetTextContent() {
    String xml = "<resources>\n" +
                 "    <style name=\"MainActivityTheme.Launch\" parent=\"android:Theme.DeviceDefault\">\n" +
                 "        <item name=\"android:windowSwipeToDismiss\">\n" +
                 "            <!-- abc --> f<!-- abc -->a<![CDATA[lse]]>\n" +
                 "        </item>\n" +
                 "        <foo> just text </foo>\n" +
                 "        <bar> abc\n" +
                 "         c   <other> def</other> ghi\n" +
                 "        </bar>\n" +
                 "    </style>\n" +
                 "</resources>";

    XmlFile xmlFile = (XmlFile)myFixture.configureByText("test.xml", xml);
    VirtualFile file = xmlFile.getVirtualFile();
    assertNotNull(file);
    assertTrue(file.exists());
    Project project = getProject();
    assertNotNull(project);
    final Document psiDocument = DomPsiConverter.convert(xmlFile);
    assertNotNull(psiDocument);

    Document document = XmlUtils.parseDocumentSilently(xmlFile.getText(), true);
    assertNotNull(document);

    // Check that getTextContent works the same vs. the plain DOM implementation.

    // <item>
    Element psiElement = (Element)psiDocument.getElementsByTagName("item").item(0);
    Element element = (Element)document.getElementsByTagName("item").item(0);
    assertFalse(element.getTextContent().isEmpty());
    assertEquals(element.getTextContent(), psiElement.getTextContent());

    // <foo>
    psiElement = (Element)psiDocument.getElementsByTagName("foo").item(0);
    element = (Element)document.getElementsByTagName("foo").item(0);
    assertFalse(element.getTextContent().isEmpty());
    assertEquals(element.getTextContent(), psiElement.getTextContent());

    // <bar>
    psiElement = (Element)psiDocument.getElementsByTagName("bar").item(0);
    element = (Element)document.getElementsByTagName("bar").item(0);
    assertFalse(element.getTextContent().isEmpty());
    assertEquals(element.getTextContent(), psiElement.getTextContent());
  }

  public void testGetElementsByTagName() {
    String xml = "<resource>\n" +
                 "    <style name=\"a\">\n" +
                 "        <style name=\"b\">\n" +
                 "        </style>\n" +
                 "        <style name=\"c\">\n" +
                 "        </style>\n" +
                 "    </style>\n" +
                 "    <style name=\"d\">\n" +
                 "        <other name=\"z\">\n" +
                 "            <style name=\"e\">\n" +
                 "                <style name=\"f\">\n" +
                 "                </style>\n" +
                 "            </style>\n" +
                 "            <style name=\"g\"/>\n" +
                 "        </other>\n" +
                 "    </style>\n" +
                 "    <style name=\"h\"/>\n" +
                 "</resource>";

    XmlFile xmlFile = (XmlFile)myFixture.configureByText("test.xml", xml);
    VirtualFile file = xmlFile.getVirtualFile();
    assertNotNull(file);
    assertTrue(file.exists());
    Project project = getProject();
    assertNotNull(project);
    final Document psiDocument = DomPsiConverter.convert(xmlFile);
    assertNotNull(psiDocument);

    Document document = XmlUtils.parseDocumentSilently(xmlFile.getText(), true);
    assertNotNull(document);

    assertEquals(
      XmlPrettyPrinter.prettyPrint(document, true),
      XmlPrettyPrinter.prettyPrint(psiDocument, true)
    );

    NodeList docList = document.getElementsByTagName("style");
    StringBuilder docString = new StringBuilder();
    for (int i = 0; i < docList.getLength(); ++i) {
      docString.append(((Element)docList.item(i)).getAttribute("name"));
    }

    NodeList psiDocList = psiDocument.getElementsByTagName("style");
    StringBuilder psiDocString = new StringBuilder();
    for (int i = 0; i < psiDocList.getLength(); ++i) {
      psiDocString.append(((Element)psiDocList.item(i)).getAttribute("name"));
    }

    assertEquals(docString.toString(), psiDocString.toString());

    Element firstStyle = (Element)document.getDocumentElement().getFirstChild().getNextSibling();
    Element lastStyle = (Element)document.getDocumentElement().getLastChild().getPreviousSibling();
    assertEquals("a", firstStyle.getAttribute("name"));
    assertEquals("h", lastStyle.getAttribute("name"));

    // Check findNodeAt. We're doing that here because getElementsByTagName used to have a side effect
    // of modifying the sibling pointers; we want to make sure it doesn't do that.
    assertEquals(
      "a",
      ((Node)DomPsiConverter.findNodeAt(
        psiDocument,
        xml.indexOf("<style"))).getAttributes().getNamedItem("name").getNodeValue()
    );
    assertEquals(
      "h",
      ((Node)DomPsiConverter.findNodeAt(
        psiDocument,
        xml.indexOf("name=\"h\""))).getNodeValue()
    );
  }

  public void testFindNodeAt() {
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"wrap_content\"\n" +
                 "    android:orientation=\"vertical\" >\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:id=\"@+id/button1\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:text=\"Button\" />\n" +
                 "<!-- my comment -->\n" +
                 "some text\n" +
                 "\n" +
                 "</LinearLayout>\n";

    XmlFile xmlFile = (XmlFile)myFixture.configureByText("test.xml", xml);
    VirtualFile file = xmlFile.getVirtualFile();
    assertNotNull(file);
    assertTrue(file.exists());
    Project project = getProject();
    assertNotNull(project);
    final Document psiDocument = DomPsiConverter.convert(xmlFile);
    assertNotNull(psiDocument);

    Document document = XmlUtils.parseDocumentSilently(xmlFile.getText(), true);
    assertNotNull(document);

    Element button = (Element)psiDocument.getElementsByTagName("Button").item(0);

    LintIdeClient client = LintIdeSupport.get().createClient(project, new LintIgnoredResult());
    DomPsiParser parser = new DomPsiParser(client);
    File ioFile = VfsUtilCore.virtualToIoFile(file);
    int button1Start = parser.getNodeStartOffset(client, ioFile, button);
    int button1End = parser.getNodeEndOffset(client, ioFile, button);
    assertSame(button, parser.findNodeAt(psiDocument, button1Start));
    assertSame(button, parser.findNodeAt(psiDocument, button1End-1));
    Attr buttonId = button.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
    int buttonIdStart = parser.getNodeStartOffset(client, ioFile, buttonId);
    int buttonIdEnd = parser.getNodeEndOffset(client, ioFile, buttonId);
    assertSame(buttonId, parser.findNodeAt(psiDocument, buttonIdStart));
    assertSame(buttonId, parser.findNodeAt(psiDocument, buttonIdEnd - 1));

    Node text = parser.findNodeAt(psiDocument, xml.indexOf("some text"));
    assertNotNull(text);
    assertEquals(Node.TEXT_NODE, text.getNodeType());
    assertEquals("\nsome text\n\n", text.getNodeValue());

    Node comment = parser.findNodeAt(psiDocument, xml.indexOf("comment"));
    assertNotNull(comment);
    assertEquals(Node.COMMENT_NODE, comment.getNodeType());
    assertEquals(" my comment ", comment.getNodeValue());
  }
}
