/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.AndroidTestCase;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.StringReader;
import java.util.Collections;

import static com.android.SdkConstants.VALUE_FILL_PARENT;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;

public class XmlTagPullParserTest extends AndroidTestCase {
  public static final String BASE_PATH = "xmlpull/";

  public XmlTagPullParserTest() {
  }

  public void test1() throws Exception {
    checkFile("layout.xml", ResourceFolderType.LAYOUT);
  }

  public void test2() throws Exception {
    checkFile("simple.xml",  ResourceFolderType.LAYOUT);
  }

  enum NextEventType { NEXT, NEXT_TOKEN, NEXT_TAG };

  private void compareParsers(PsiFile file, NextEventType nextEventType) throws Exception {
    assertTrue(file instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)file;
    KXmlParser referenceParser = createReferenceParser(file);
    XmlTagPullParser parser = new XmlTagPullParser(xmlFile, Collections.<XmlTag>emptySet(), Density.MEDIUM,
                                                   new RenderLogger("test", myModule));

    assertEquals("Expected " + name(referenceParser.getEventType()) + " but was "
                 + name(parser.getEventType())
                 + " (at line:column " + describPosition(referenceParser) + ")",
                 referenceParser.getEventType(), parser.getEventType());

    while (true) {
      int expected, next;
      switch (nextEventType) {
        case NEXT:
          expected = referenceParser.next();
          next = parser.next();
          break;
        case NEXT_TOKEN:
          expected = referenceParser.nextToken();
          next = parser.nextToken();
          break;
        case NEXT_TAG: {
          try {
            expected = referenceParser.nextTag();
          } catch (Exception e) {
            expected = referenceParser.getEventType();
          }
          try {
            next = parser.nextTag();
          } catch (Exception e) {
            next = parser.getEventType();
          }
          break;
        }
        default:
          fail("Unexpected type");
          return;
      }

      PsiElement element = null;
      if (expected == XmlPullParser.START_TAG) {
        assertNotNull(parser.getViewKey());
        assertNotNull(parser.getViewCookie());
        assertTrue(parser.getViewCookie() instanceof PsiElement);
        element = (PsiElement)parser.getViewCookie();
      }

      if (expected == XmlPullParser.START_TAG) {
        assertEquals(referenceParser.getName(), parser.getName());
        if (false && element != xmlFile.getRootTag()) { // This doesn't work quite right; KXmlParser seems to not include xmlns: attributes on the root tag!
          if (referenceParser.getAttributeCount() != parser.getAttributeCount()) {
            StringBuilder difference = new StringBuilder();
            difference.append("Expected Atributes:\n");
            for (int i = 0; i < referenceParser.getAttributeCount(); i++) {
              difference.append(referenceParser.getAttributeName(i)).append('\n');
            }
            difference.append("\nbut was:\n");
            for (int i = 0; i < parser.getAttributeCount(); i++) {
              difference.append(parser.getAttributeName(i)).append('\n');
            }
            assertEquals(difference.toString(), referenceParser.getAttributeCount(), parser.getAttributeCount());
          }
          for (int i = 0; i < referenceParser.getAttributeCount(); i++) {
            assertEquals(referenceParser.getAttributeName(i), parser.getAttributeName(i));
            assertEquals(referenceParser.getAttributeNamespace(i), parser.getAttributeNamespace(i));
            assertEquals(referenceParser.getAttributeValue(i), parser.getAttributeValue(i));
            String value1 =
              referenceParser.getAttributeValue(referenceParser.getAttributeNamespace(i), referenceParser.getAttributeName(i));
            String value2 = parser.getAttributeValue(parser.getAttributeNamespace(i), parser.getAttributeName(i));
            assertEquals(normalizeValue(value1), normalizeValue(value2));
          }
        }
        assertEquals(referenceParser.isEmptyElementTag(), parser.isEmptyElementTag());

        if (element instanceof XmlTag) {
          XmlTag tag = (XmlTag)element;
          for (XmlAttribute attribute : tag.getAttributes()) {
            String namespace = attribute.getNamespace();
            String name = attribute.getLocalName();
            assertEquals(namespace + ':' + name + " in element " + parser.getName(),
                         normalizeValue(referenceParser.getAttributeValue(namespace, name)),
                         normalizeValue(parser.getAttributeValue(namespace, name)));
          }
        }
      } else if (expected == XmlPullParser.TEXT || expected == XmlPullParser.COMMENT) {
        assertEquals(StringUtil.notNullize(referenceParser.getText()).trim(), StringUtil.notNullize(parser.getText()).trim());
      }

      if (expected != next) {
        assertEquals("Expected " + name(expected) + " but was " + name(next)
                     + "(At " + describPosition(referenceParser) + ")",
                     expected, next);
      }
      if (expected == KXmlParser.END_DOCUMENT) {
        break;
      }
    }
  }

  private String normalizeValue(String value) {
    // Some parser translate values; ensure that these are identical
    if (value != null && value.equals(VALUE_MATCH_PARENT)) {
      return VALUE_FILL_PARENT;
    }
    return value;
  }

  private static String name(int event) {
    return XmlPullParser.TYPES[event];
  }

  private void checkFile(String filename, ResourceFolderType folder) throws Exception {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + filename, "res/" + folder.getName() + "/" + filename);
    assertNotNull(file);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    compareParsers(psiFile, NextEventType.NEXT_TAG);
    // The XmlTagPullParser only supports tags, not text (no text is used in layouts)
    //compareParsers(psiFile, NextEventType.NEXT);
    //compareParsers(psiFile, NextEventType.NEXT_TOKEN);
  }

  private KXmlParser createReferenceParser(PsiFile file) throws XmlPullParserException {
    KXmlParser referenceParser = new KXmlParser();
    referenceParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
    referenceParser.setInput(new StringReader(file.getText()));
    return referenceParser;
  }

  private String describPosition(KXmlParser referenceParser) {
    return referenceParser.getLineNumber() + ":" + referenceParser.getColumnNumber();
  }
}
