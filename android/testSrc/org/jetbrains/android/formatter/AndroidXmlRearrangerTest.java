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
package org.jetbrains.android.formatter;

import com.android.SdkConstants;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import com.intellij.xml.arrangement.XmlRearranger;
import org.intellij.lang.annotations.Language;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class AndroidXmlRearrangerTest {
  @Rule
  public final TestRule myRule = AndroidProjectRule.inMemory();

  @Test
  public void deserializeAndroidAttributeOrder() {
    StdArrangementMatchRule rule = XmlRearranger.attrArrangementRule(".*", SdkConstants.NS_RESOURCES, AndroidAttributeOrder.INSTANCE);
    Object expected = StdArrangementSettings.createByMatchRules(Collections.emptyList(), Collections.singletonList(rule));

    Element arrangement = newArrangementElement();
    Object actual = Rearranger.EXTENSION.forLanguage(XMLLanguage.INSTANCE).getSerializer().deserialize(arrangement);

    assertEquals(expected, actual);
  }

  @NotNull
  private static Element newArrangementElement() {
    try {
      @Language("XML")
      String arrangement =
        "<arrangement>\n" +
        "  <rules>\n" +
        "    <section>\n" +
        "      <rule>\n" +
        "        <match>\n" +
        "          <AND>\n" +
        "            <NAME>.*</NAME>\n" +
        "            <XML_NAMESPACE>http://schemas.android.com/apk/res/android</XML_NAMESPACE>\n" +
        "          </AND>\n" +
        "        </match>\n" +
        "        <order>ANDROID_ATTRIBUTE_ORDER</order>\n" +
        "      </rule>\n" +
        "    </section>\n" +
        "  </rules>\n" +
        "</arrangement>";

      return new SAXBuilder().build(new StringReader(arrangement)).getRootElement();
    }
    catch (JDOMException | IOException exception) {
      throw new AssertionError(exception);
    }
  }
}
