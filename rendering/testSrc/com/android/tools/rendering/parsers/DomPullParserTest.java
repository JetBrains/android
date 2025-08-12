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
package com.android.tools.rendering.parsers;

import static com.android.SdkConstants.VALUE_FILL_PARENT;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.utils.XmlUtils;
import com.intellij.openapi.util.text.StringUtil;
import java.io.StringReader;
import java.util.SortedSet;
import java.util.TreeSet;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.kxml2.io.KXmlParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Tests the parser by constructing a DOM from an XML file, and then it runs an XmlPullParser
 * in parallel with this parser and checks event for event that the two parsers are returning the same results
 */
public class DomPullParserTest {

  @Test
  public void test1() throws Exception {
    //noinspection SpellCheckingInspection
    String fileText = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                      "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "              android:layout_width=\"fill_parent\"\n" +
                      "              android:layout_height=\"fill_parent\"\n" +
                      "              android:orientation=\"vertical\">\n" +
                      "\n" +
                      "    <TextView\n" +
                      "            android:layout_width=\"fill_parent\"\n" +
                      "            android:layout_height=\"wrap_content\"\n" +
                      "            android:text=\"@string/app_name\"/>\n" +
                      "\n" +
                      "  <include layout=\"@layout/colorstrip\" android:layout_height=\"@dimen/colorstrip_height\" android:layout_width=\"match_parent\"/>\n" +
                      "\n" +
                      "</LinearLayout>";
    compareParsers(fileText, NextEventType.NEXT_TAG);
  }

  enum NextEventType { NEXT, NEXT_TOKEN, NEXT_TAG }

  private static void compareParsers(String fileText, NextEventType nextEventType) throws Exception {
    Document document = XmlUtils.parseDocumentSilently(fileText, true);
    assertNotNull(document);
    KXmlParser referenceParser = createReferenceParser(fileText);
    ILayoutPullParser parser = DomPullParser.createFromDocument(document);

    assertEquals("Expected " + name(referenceParser.getEventType()) + " but was "
                 + name(parser.getEventType())
                 + " (at line:column " + describePosition(referenceParser) + ")",
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
          throw new AssertionError("Unexpected type");
      }

      Element element = null;
      if (expected == XmlPullParser.START_TAG) {
        assertNotNull(parser.getViewCookie());
        assertTrue(parser.getViewCookie() == null || parser.getViewCookie() instanceof Element);
        element = (Element)parser.getViewCookie();
      }

      if (expected == XmlPullParser.START_TAG) {
        assertEquals(referenceParser.getName(), parser.getName());
        if (element != document.getDocumentElement()) { // KXmlParser seems to not include xmlns: attributes on the root tag!
          SortedSet<String> referenceAttributes = new TreeSet<>();
          SortedSet<String> attributes = new TreeSet<>();
          for (int i = 0; i < referenceParser.getAttributeCount(); i++) {
            String s = referenceParser.getAttributePrefix(i) + ':' + referenceParser.getAttributeName(i) + '='
                       + referenceParser.getAttributeValue(i);
            referenceAttributes.add(s);
          }
          for (int i = 0; i < parser.getAttributeCount(); i++) {
            String s = parser.getAttributePrefix(i) + ':' + parser.getAttributeName(i) + '=' + parser.getAttributeValue(i);
            attributes.add(s);
            if (parser.getAttributeNamespace(i) != null) {
              //noinspection ConstantConditions
              assertEquals(normalizeValue(parser.getAttributeValue(i)),
                           normalizeValue(parser.getAttributeValue(parser.getAttributeNamespace(i), parser.getAttributeName(i))));
            }
          }

          assertEquals(referenceAttributes, attributes);
        }
        assertEquals(referenceParser.isEmptyElementTag(), parser.isEmptyElementTag());
      } else if (expected == XmlPullParser.TEXT || expected == XmlPullParser.COMMENT) {
        assertEquals(StringUtil.notNullize(referenceParser.getText()).trim(), StringUtil.notNullize(parser.getText()).trim());
      }

      if (expected != next) {
        assertEquals("Expected " + name(expected) + " but was " + name(next)
                     + "(At " + describePosition(referenceParser) + ")",
                     expected, next);
      }
      if (expected == XmlPullParser.END_DOCUMENT) {
        break;
      }
    }
  }

  @Nullable
  private static String normalizeValue(@Nullable String value) {
    // Some parser translate values; ensure that these are identical
    if (value != null && value.equals(VALUE_MATCH_PARENT)) {
      return VALUE_FILL_PARENT;
    }
    return value;
  }

  private static String name(int event) {
    return XmlPullParser.TYPES[event];
  }

  private static KXmlParser createReferenceParser(String fileText) throws XmlPullParserException {
    KXmlParser referenceParser = new KXmlParser();
    referenceParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
    referenceParser.setInput(new StringReader(fileText));
    return referenceParser;
  }

  private static String describePosition(KXmlParser referenceParser) {
    return referenceParser.getLineNumber() + ":" + referenceParser.getColumnNumber();
  }
}
