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
package com.android.tools.idea.resources.aar;

import static org.junit.Assert.assertEquals;

import com.android.aapt.Resources.StyledString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.TextFormat;
import org.junit.Test;

/**
 * Tests for {@link ProtoStyledStringDecoder}.
 */
public class ProtoStyledStringDecoderTest {
  private static final String STYLED_STRING_PROTO = "" +
      "value: \"aabbccddeeffgghhii\"\n" +
      "span: {\n" +
      "  tag: \"i\"\n" +
      "  first_char: 0x00000002\n" +
      "  last_char: 0x0000000b\n" +
      "}\n" +
      "span: {\n" +
      "  tag: \"b\"\n" +
      "  first_char: 0x00000002\n" +
      "  last_char: 0x00000005\n" +
      "}\n" +
      "span: {\n" +
      "  tag: \"u\"\n" +
      "  first_char: 0x00000004\n" +
      "  last_char: 0x00000005\n" +
      "}\n" +
      "span: {\n" +
      "  tag: \"font;color=blue;face=verdana\"\n" +
      "  first_char: 0x00000008\n" +
      "  last_char: 0x00000009\n" +
      "}\n" +
      "span: {\n" +
      "  tag: \"x;e= & < ' \\\"\"\n" +
      "  first_char: 0x0000000e\n" +
      "  last_char: 0x0000000f\n" +
      "}";
  private static final String STYLED_STRING_XML = "" +
      "aa<i><b>bb<u>cc</u></b>dd<font color=\"blue\" face=\"verdana\">ee</font>ff</i>gg<x e=\" &amp; &lt; &apos; &quot;\">hh</x>ii";

  @Test
  public void testRawXmlValue() throws Exception {
    StyledString.Builder builder = StyledString.newBuilder();
    TextFormat.merge(STYLED_STRING_PROTO, ExtensionRegistry.getEmptyRegistry(), builder);
    StyledString styledString = builder.build();
    assertEquals(STYLED_STRING_XML, ProtoStyledStringDecoder.getRawXmlValue(styledString));
  }
}
