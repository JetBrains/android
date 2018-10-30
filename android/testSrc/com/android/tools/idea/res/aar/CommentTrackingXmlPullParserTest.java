/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res.aar;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ATTR;
import static com.android.SdkConstants.TAG_DECLARE_STYLEABLE;
import static com.google.common.truth.Truth.assertThat;
import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import java.io.Reader;
import java.io.StringReader;
import org.intellij.lang.annotations.Language;
import org.junit.Test;

/**
 * Tests for {@link CommentTrackingXmlPullParser}.
 */
public class CommentTrackingXmlPullParserTest {
  @Language("XML")
  private static final String XML = "" +
      "<!--\n" +
      "  ~ Copyright comment\n" +
      "  -->\n" +
      "<resources>\n" +
      "  <!-- These are the standard attributes that make up a complete theme. -->\n" +
      "  <declare-styleable name=\"Theme\">\n" +
      "    <!-- ============== -->\n" +
      "    <!-- Generic styles -->\n" +
      "    <!-- ============== -->\n" +
      "    <eat-comment />\n" +
      "\n" +
      "    <attr name=\"colorForeground\" format=\"color\" />\n" +
      "    <attr name=\"colorForegroundInverse\" format=\"color\" />\n" +
      "\n" +
      "    <!-- WARNING:  If adding attributes to Preference, make sure it does not conflict\n" +
      "                 with a View's attributes.  Some subclasses (e.g., EditTextPreference)\n" +
      "                 proxy all attributes to its EditText widget. -->\n" +
      "    <eat-comment />\n" +
      "\n" +
      "    <!-- ============= -->\n" +
      "    <!-- Color palette -->\n" +
      "    <!-- ============= -->\n" +
      "    <eat-comment />\n" +
      "\n" +
      "    <!-- The primary branding color for the app. By default, this is the color applied to the\n" +
      "         action bar background. -->\n" +
      "    <attr name=\"colorPrimary\" format=\"color\" />\n" +
      "  </declare-styleable>\n" +
      "\n" +
      "  <!-- WARNING:  If adding attributes to Preference, make sure it does not conflict\n" +
      "                 with a View's attributes.  Some subclasses (e.g., EditTextPreference)\n" +
      "                 proxy all attributes to its EditText widget. -->\n" +
      "  <eat-comment />\n" +
      "\n" +
      "  <!-- **************************************************************** -->\n" +
      "  <!-- Other non-theme attributes. -->\n" +
      "  <!-- **************************************************************** -->\n" +
      "  <eat-comment />\n" +
      "\n" +
      "  <!-- WARNING:  If adding attributes to Preference, make sure it does not conflict\n" +
      "                 with a View's attributes.  Some subclasses (e.g., EditTextPreference)\n" +
      "                 proxy all attributes to its EditText widget. -->\n" +
      "  <eat-comment />\n" +
      "\n" +
      "  <!-- Default text typeface style. -->\n" +
      "  <attr name=\"textStyle\">\n" +
      "    <flag name=\"normal\" value=\"0\" />\n" +
      "    <flag name=\"bold\" value=\"1\" />\n" +
      "    <flag name=\"italic\" value=\"2\" />\n" +
      "  </attr>\n" +
      "\n" +
      "  <!-- Color of text (usually same as colorForeground). -->\n" +
      "  <attr name=\"textColor\" format=\"reference|color\" />\n" +
      "\n" +
      "  <declare-styleable name=\"DateTimeView\">\n" +
      "     <attr name=\"showRelative\" format=\"boolean\" />\n" +
      "  </declare-styleable>\n" +
      "</resources>\n";

  @Test
  public void testParsing() throws Exception {
    int styleableCount = 0;
    int attrCount = 0;

    try (Reader reader = new StringReader(XML)) {
      CommentTrackingXmlPullParser parser = new CommentTrackingXmlPullParser();
      parser.setInput(reader);

      int event;
      do {
        event = parser.nextToken();
        if (event == START_TAG) {
          String tagName = parser.getName();
          String name = parser.getAttributeValue(null, ATTR_NAME);
          if (tagName.equals(TAG_DECLARE_STYLEABLE)) {
            if (name.equals("Theme")) {
              assertThat(parser.getLastComment()).isEqualTo("These are the standard attributes that make up a complete theme.");
              assertThat(parser.getAttrGroupComment()).isNull();
              styleableCount++;
            } else if (name.equals("DateTimeView")) {
              assertThat(parser.getLastComment()).isNull();
              assertThat(parser.getAttrGroupComment()).isNull();
              styleableCount++;
            }
          } else if (tagName.equals(TAG_ATTR)) {
            if (name.equals("colorForeground") || name.equals("colorForegroundInverse")) {
              assertThat(parser.getLastComment()).isNull();
              assertThat(parser.getAttrGroupComment()).isEqualTo("Generic styles");
              attrCount++;
            } else if (name.equals("colorPrimary")) {
              assertThat(parser.getLastComment()).isEqualTo(
                  "The primary branding color for the app. By default, this is the color applied to the\n" +
                  "         action bar background.");
              assertThat(parser.getAttrGroupComment()).isEqualTo("Color palette");
              attrCount++;
            } else if (name.equals("textStyle")) {
              assertThat(parser.getLastComment()).isEqualTo("Default text typeface style.");
              assertThat(parser.getAttrGroupComment()).isEqualTo("Other non-theme attributes");
              attrCount++;
            } else if (name.equals("textColor")) {
              assertThat(parser.getLastComment()).isEqualTo("Color of text (usually same as colorForeground).");
              assertThat(parser.getAttrGroupComment()).isEqualTo("Other non-theme attributes");
              attrCount++;
            } else if (name.equals("showRelative")) {
              assertThat(parser.getLastComment()).isNull();
              assertThat(parser.getAttrGroupComment()).isNull();
              attrCount++;
            }
          }
        }
      } while (event != END_DOCUMENT);
    }

    // Make sure that we visited all declare-styleable and attr tags.
    assertThat(styleableCount).isEqualTo(2);
    assertThat(attrCount).isEqualTo(6);
  }
}
