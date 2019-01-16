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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.AUTO_URI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import static org.xmlpull.v1.XmlPullParser.TEXT;

import com.android.aapt.Resources.XmlNode;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.TextFormat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/**
 * Tests for {@link ProtoXmlPullParser}.
 */
public class ProtoXmlPullParserTest {
  private static final String LAYOUT_PROTO = "" +
      "element: {\n" +
      "  namespace_declaration: {\n" +
      "    prefix: \"android\"\n" +
      "    uri: \"http://schemas.android.com/apk/res/android\"\n" +
      "    source: {\n" +
      "      line_number: 0x00000002\n" +
      "    }\n" +
      "  }\n" +
      "  namespace_declaration: {\n" +
      "    prefix: \"app\"\n" +
      "    uri: \"http://schemas.android.com/apk/res-auto\"\n" +
      "    source: {\n" +
      "      line_number: 0x00000002\n" +
      "    }\n" +
      "  }\n" +
      "  name: \"android.support.v4.widget.DrawerLayout\"\n" +
      "  attribute: {\n" +
      "    namespace_uri: \"http://schemas.android.com/apk/res/android\"\n" +
      "    name: \"fitsSystemWindows\"\n" +
      "    value: \"true\"\n" +
      "    source: {\n" +
      "      line_number: 0x00000002\n" +
      "    }\n" +
      "    resource_id: 0x010100dd\n" +
      "    compiled_item: {\n" +
      "      prim: {\n" +
      "        boolean_value: true\n" +
      "      }\n" +
      "    }\n" +
      "  }\n" +
      "  attribute: {\n" +
      "    namespace_uri: \"http://schemas.android.com/apk/res/android\"\n" +
      "    name: \"id\"\n" +
      "    value: \"@+id/drawer_layout\"\n" +
      "    source: {\n" +
      "      line_number: 0x00000002\n" +
      "    }\n" +
      "    resource_id: 0x010100d0\n" +
      "    compiled_item: {\n" +
      "      ref: {\n" +
      "        name: \"id/drawer_layout\"\n" +
      "      }\n" +
      "    }\n" +
      "  }\n" +
      "  attribute: {\n" +
      "    namespace_uri: \"http://schemas.android.com/apk/res/android\"\n" +
      "    name: \"layout_height\"\n" +
      "    value: \"match_parent\"\n" +
      "    source: {\n" +
      "      line_number: 0x00000002\n" +
      "    }\n" +
      "    resource_id: 0x010100f5\n" +
      "    compiled_item: {\n" +
      "      prim: {\n" +
      "        int_decimal_value: -1\n" +
      "      }\n" +
      "    }\n" +
      "  }\n" +
      "  attribute: {\n" +
      "    namespace_uri: \"http://schemas.android.com/apk/res/android\"\n" +
      "    name: \"layout_width\"\n" +
      "    value: \"match_parent\"\n" +
      "    source: {\n" +
      "      line_number: 0x00000002\n" +
      "    }\n" +
      "    resource_id: 0x010100f4\n" +
      "    compiled_item: {\n" +
      "      prim: {\n" +
      "        int_decimal_value: -1\n" +
      "      }\n" +
      "    }\n" +
      "  }\n" +
      "  child: {\n" +
      "    text: \"\\n\\n    \"\n" +
      "    source: {\n" +
      "      line_number: 0x00000009\n" +
      "      column_number: 0x0000001d\n" +
      "    }\n" +
      "  }\n" +
      "  child: {\n" +
      "    element: {\n" +
      "      name: \"include\"\n" +
      "      attribute: {\n" +
      "        name: \"layout\"\n" +
      "        value: \"@layout/app_bar_main\"\n" +
      "        source: {\n" +
      "          line_number: 0x0000000b\n" +
      "        }\n" +
      "        compiled_item: {\n" +
      "          ref: {\n" +
      "            name: \"layout/app_bar_main\"\n" +
      "          }\n" +
      "        }\n" +
      "      }\n" +
      "      attribute: {\n" +
      "        namespace_uri: \"http://schemas.android.com/apk/res/android\"\n" +
      "        name: \"layout_height\"\n" +
      "        value: \"match_parent\"\n" +
      "        source: {\n" +
      "          line_number: 0x0000000b\n" +
      "        }\n" +
      "        resource_id: 0x010100f5\n" +
      "        compiled_item: {\n" +
      "          prim: {\n" +
      "            int_decimal_value: -1\n" +
      "          }\n" +
      "        }\n" +
      "      }\n" +
      "      attribute: {\n" +
      "        namespace_uri: \"http://schemas.android.com/apk/res/android\"\n" +
      "        name: \"layout_width\"\n" +
      "        value: \"match_parent\"\n" +
      "        source: {\n" +
      "          line_number: 0x0000000b\n" +
      "        }\n" +
      "        resource_id: 0x010100f4\n" +
      "        compiled_item: {\n" +
      "          prim: {\n" +
      "            int_decimal_value: -1\n" +
      "          }\n" +
      "        }\n" +
      "      }\n" +
      "    }\n" +
      "    source: {\n" +
      "      line_number: 0x0000000b\n" +
      "      column_number: 0x00000004\n" +
      "    }\n" +
      "  }\n" +
      "  child: {\n" +
      "    text: \"\\n\\n    \"\n" +
      "    source: {\n" +
      "      line_number: 0x0000000e\n" +
      "      column_number: 0x0000002f\n" +
      "    }\n" +
      "  }\n" +
      "  child: {\n" +
      "    element: {\n" +
      "      name: \"android.support.design.widget.NavigationView\"\n" +
      "      attribute: {\n" +
      "        namespace_uri: \"http://schemas.android.com/apk/res/android\"\n" +
      "        name: \"fitsSystemWindows\"\n" +
      "        value: \"true\"\n" +
      "        source: {\n" +
      "          line_number: 0x00000010\n" +
      "        }\n" +
      "        resource_id: 0x010100dd\n" +
      "        compiled_item: {\n" +
      "          prim: {\n" +
      "            boolean_value: true\n" +
      "          }\n" +
      "        }\n" +
      "      }\n" +
      "      attribute: {\n" +
      "        namespace_uri: \"http://schemas.android.com/apk/res/android\"\n" +
      "        name: \"id\"\n" +
      "        value: \"@+id/nav_view\"\n" +
      "        source: {\n" +
      "          line_number: 0x00000010\n" +
      "        }\n" +
      "        resource_id: 0x010100d0\n" +
      "        compiled_item: {\n" +
      "          ref: {\n" +
      "            name: \"id/nav_view\"\n" +
      "          }\n" +
      "        }\n" +
      "      }\n" +
      "      attribute: {\n" +
      "        namespace_uri: \"http://schemas.android.com/apk/res/android\"\n" +
      "        name: \"layout_gravity\"\n" +
      "        value: \"start\"\n" +
      "        source: {\n" +
      "          line_number: 0x00000010\n" +
      "        }\n" +
      "        resource_id: 0x010100b3\n" +
      "        compiled_item: {\n" +
      "          prim: {\n" +
      "            int_hexadecimal_value: 0x00800003\n" +
      "          }\n" +
      "        }\n" +
      "      }\n" +
      "      attribute: {\n" +
      "        namespace_uri: \"http://schemas.android.com/apk/res/android\"\n" +
      "        name: \"layout_height\"\n" +
      "        value: \"match_parent\"\n" +
      "        source: {\n" +
      "          line_number: 0x00000010\n" +
      "        }\n" +
      "        resource_id: 0x010100f5\n" +
      "        compiled_item: {\n" +
      "          prim: {\n" +
      "            int_decimal_value: -1\n" +
      "          }\n" +
      "        }\n" +
      "      }\n" +
      "      attribute: {\n" +
      "        namespace_uri: \"http://schemas.android.com/apk/res/android\"\n" +
      "        name: \"layout_width\"\n" +
      "        value: \"wrap_content\"\n" +
      "        source: {\n" +
      "          line_number: 0x00000010\n" +
      "        }\n" +
      "        resource_id: 0x010100f4\n" +
      "        compiled_item: {\n" +
      "          prim: {\n" +
      "            int_decimal_value: -2\n" +
      "          }\n" +
      "        }\n" +
      "      }\n" +
      "    }\n" +
      "    source: {\n" +
      "      line_number: 0x00000010\n" +
      "      column_number: 0x00000004\n" +
      "    }\n" +
      "  }\n" +
      "  child: {\n" +
      "    text: \"\\n\\n\"\n" +
      "    source: {\n" +
      "      line_number: 0x00000016\n" +
      "      column_number: 0x0000000a\n" +
      "    }\n" +
      "  }\n" +
      "}\n" +
      "source: {\n" +
      "  line_number: 0x00000002\n" +
      "}\n";

  @NotNull
  private static byte[] protoToByteArray(@NotNull String proto) throws Exception {
    XmlNode.Builder builder = XmlNode.newBuilder();
    TextFormat.merge(proto, ExtensionRegistry.getEmptyRegistry(), builder);
    XmlNode node = builder.build();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    node.writeTo(outputStream);
    return outputStream.toByteArray();
  }

  private static void checkStandardNamespaces(@NotNull ProtoXmlPullParser parser) {
    assertEquals(0, parser.getNamespaceCount(0));
    for (int depth = 1; depth <= parser.getDepth(); depth++) {
      assertEquals(2, parser.getNamespaceCount(depth));
    }
    assertEquals(ANDROID_URI, parser.getNamespaceUri(0));
    assertEquals(AUTO_URI, parser.getNamespaceUri(1));
    assertEquals("android", parser.getNamespacePrefix(0));
    assertEquals("app", parser.getNamespacePrefix(1));
    assertEquals(ANDROID_URI, parser.getNamespace("android"));
    assertEquals(AUTO_URI, parser.getNamespace("app"));
  }

  @Test
  public void testParsing() throws Exception {
    try (InputStream stream = new ByteArrayInputStream(protoToByteArray(LAYOUT_PROTO))) {
      ProtoXmlPullParser parser = new ProtoXmlPullParser();
      parser.setInput(stream, null);
      assertEquals(START_DOCUMENT, parser.getEventType());
      assertEquals(0, parser.getDepth());

      int event = parser.next();
      assertEquals(START_TAG, event);
      assertEquals(START_TAG, parser.getEventType());
      assertEquals(1, parser.getDepth());
      assertEquals("android.support.v4.widget.DrawerLayout", parser.getName());
      assertNull(parser.getPrefix());
      assertEquals("", parser.getNamespace());
      checkStandardNamespaces(parser);
      assertEquals(2, parser.getLineNumber());
      assertEquals(0, parser.getColumnNumber());
      assertEquals(4, parser.getAttributeCount());
      assertEquals("fitsSystemWindows", parser.getAttributeName(0));
      assertEquals("android", parser.getAttributePrefix(0));
      assertEquals(ANDROID_URI, parser.getAttributeNamespace(0));
      assertEquals("true", parser.getAttributeValue(0));
      assertEquals("true", parser.getAttributeValue(ANDROID_URI, "fitsSystemWindows"));
      assertNull(parser.getAttributeValue(null, "fitsSystemWindows")); // Wrong namespace
      assertEquals("id", parser.getAttributeName(1));
      assertEquals("android", parser.getAttributePrefix(1));
      assertEquals(ANDROID_URI, parser.getAttributeNamespace(1));
      assertEquals("@+id/drawer_layout", parser.getAttributeValue(1));
      assertEquals("@+id/drawer_layout", parser.getAttributeValue(ANDROID_URI, "id"));
      assertEquals("layout_height", parser.getAttributeName(2));
      assertEquals("android", parser.getAttributePrefix(2));
      assertEquals(ANDROID_URI, parser.getAttributeNamespace(2));
      assertEquals("match_parent", parser.getAttributeValue(2));
      assertEquals("match_parent", parser.getAttributeValue(ANDROID_URI, "layout_height"));
      assertEquals("layout_width", parser.getAttributeName(3));
      assertEquals("android", parser.getAttributePrefix(3));
      assertEquals(ANDROID_URI, parser.getAttributeNamespace(3));
      assertEquals("match_parent", parser.getAttributeValue(3));
      assertEquals("match_parent", parser.getAttributeValue(ANDROID_URI, "layout_width"));

      event = parser.next();
      assertEquals(TEXT, event);
      assertEquals(TEXT, parser.getEventType());
      assertEquals(2, parser.getDepth());
      assertEquals("\n\n    ", parser.getText());
      assertTrue(parser.isWhitespace());
      assertEquals(9, parser.getLineNumber());
      assertEquals(29, parser.getColumnNumber());

      event = parser.next();
      assertEquals(START_TAG, event);
      assertEquals(START_TAG, parser.getEventType());
      assertEquals(2, parser.getDepth());
      assertEquals("include", parser.getName());
      assertNull(parser.getPrefix());
      assertEquals("", parser.getNamespace());
      checkStandardNamespaces(parser);
      assertEquals(11, parser.getLineNumber());
      assertEquals(4, parser.getColumnNumber());
      assertEquals(3, parser.getAttributeCount());
      assertEquals("layout", parser.getAttributeName(0));
      assertNull(parser.getAttributePrefix(0));
      assertEquals("", parser.getAttributeNamespace(0));
      assertEquals("@layout/app_bar_main", parser.getAttributeValue(0));
      assertEquals("@layout/app_bar_main", parser.getAttributeValue(null, "layout"));
      assertNull(parser.getAttributeValue(ANDROID_URI, "layout")); // Wrong namespace
      assertEquals("layout_height", parser.getAttributeName(1));
      assertEquals("android", parser.getAttributePrefix(1));
      assertEquals(ANDROID_URI, parser.getAttributeNamespace(1));
      assertEquals("match_parent", parser.getAttributeValue(1));
      assertEquals("match_parent", parser.getAttributeValue(ANDROID_URI, "layout_height"));
      assertEquals("layout_width", parser.getAttributeName(2));
      assertEquals("android", parser.getAttributePrefix(2));
      assertEquals(ANDROID_URI, parser.getAttributeNamespace(2));
      assertEquals("match_parent", parser.getAttributeValue(2));
      assertEquals("match_parent", parser.getAttributeValue(ANDROID_URI, "layout_width"));

      event = parser.next();
      assertEquals(END_TAG, event);
      assertEquals(END_TAG, parser.getEventType());
      assertEquals(2, parser.getDepth());
      assertEquals("include", parser.getName());

      event = parser.next();
      assertEquals(TEXT, event);
      assertEquals(TEXT, parser.getEventType());
      assertEquals(2, parser.getDepth());
      assertEquals("\n\n    ", parser.getText());
      assertTrue(parser.isWhitespace());
      assertEquals(14, parser.getLineNumber());
      assertEquals(47, parser.getColumnNumber());

      event = parser.next();
      assertEquals(START_TAG, event);
      assertEquals(START_TAG, parser.getEventType());
      assertEquals(2, parser.getDepth());
      assertEquals("android.support.design.widget.NavigationView", parser.getName());
      assertNull(parser.getPrefix());
      assertEquals("", parser.getNamespace());
      checkStandardNamespaces(parser);
      assertEquals(16, parser.getLineNumber());
      assertEquals(4, parser.getColumnNumber());
      assertEquals(5, parser.getAttributeCount());
      assertEquals("fitsSystemWindows", parser.getAttributeName(0));
      assertEquals("android", parser.getAttributePrefix(0));
      assertEquals(ANDROID_URI, parser.getAttributeNamespace(0));
      assertEquals("true", parser.getAttributeValue(0));
      assertEquals("true", parser.getAttributeValue(ANDROID_URI, "fitsSystemWindows"));
      assertEquals("id", parser.getAttributeName(1));
      assertEquals("android", parser.getAttributePrefix(1));
      assertEquals(ANDROID_URI, parser.getAttributeNamespace(1));
      assertEquals("@+id/nav_view", parser.getAttributeValue(1));
      assertEquals("@+id/nav_view", parser.getAttributeValue(ANDROID_URI, "id"));
      assertEquals("layout_gravity", parser.getAttributeName(2));
      assertEquals("android", parser.getAttributePrefix(2));
      assertEquals(ANDROID_URI, parser.getAttributeNamespace(2));
      assertEquals("start", parser.getAttributeValue(2));
      assertEquals("start", parser.getAttributeValue(ANDROID_URI, "layout_gravity"));
      assertEquals("layout_height", parser.getAttributeName(3));
      assertEquals("android", parser.getAttributePrefix(3));
      assertEquals(ANDROID_URI, parser.getAttributeNamespace(3));
      assertEquals("match_parent", parser.getAttributeValue(3));
      assertEquals("match_parent", parser.getAttributeValue(ANDROID_URI, "layout_height"));
      assertEquals("layout_width", parser.getAttributeName(4));
      assertEquals("android", parser.getAttributePrefix(4));
      assertEquals(ANDROID_URI, parser.getAttributeNamespace(4));
      assertEquals("wrap_content", parser.getAttributeValue(4));
      assertEquals("wrap_content", parser.getAttributeValue(ANDROID_URI, "layout_width"));

      event = parser.next();
      assertEquals(END_TAG, event);
      assertEquals(END_TAG, parser.getEventType());
      assertEquals(2, parser.getDepth());
      assertEquals("android.support.design.widget.NavigationView", parser.getName());

      event = parser.next();
      assertEquals(TEXT, event);
      assertEquals(TEXT, parser.getEventType());
      assertEquals(2, parser.getDepth());
      assertEquals("\n\n", parser.getText());
      assertTrue(parser.isWhitespace());
      assertEquals(22, parser.getLineNumber());
      assertEquals(10, parser.getColumnNumber());

      event = parser.next();
      assertEquals(END_TAG, event);
      assertEquals(END_TAG, parser.getEventType());
      assertEquals(1, parser.getDepth());
      assertEquals("android.support.v4.widget.DrawerLayout", parser.getName());

      event = parser.next();
      assertEquals(END_DOCUMENT, event);
      assertEquals(END_DOCUMENT, parser.getEventType());
    }
  }
}
