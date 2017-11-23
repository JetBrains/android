/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.editors;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.naveditor.property.fixtures.TextEditorFixture;
import com.android.tools.idea.uibuilder.property.EmptyProperty;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.google.common.collect.ImmutableList;
import com.intellij.util.xml.XmlName;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static com.android.SdkConstants.*;
import static java.awt.event.KeyEvent.VK_ENTER;
import static java.awt.event.KeyEvent.VK_ESCAPE;

public class TextEditorTest extends PropertyTestCase {
  private TextEditorFixture myFixture;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture = TextEditorFixture.create(getProject());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    finally {
      super.tearDown();
    }
  }

  public void testEscapeRestoresOriginalAfterTyping() {
    myFixture
      .setProperty(getProperty(myTextView, ATTR_TEXT))
      .expectText("SomeText")
      .expectSelectedText(null)
      .gainFocus()
      .expectSelectedText("SomeText")
      .type("5")
      .key(VK_ESCAPE)
      .verifyCancelEditingCalled()
      .expectValue("SomeText")
      .expectSelectedText("SomeText");
  }

  public void testFocusLoss() {
    myFixture
      .setProperty(getProperty(myTextView, ATTR_TEXT))
      .expectText("SomeText")
      .expectSelectedText(null)
      .gainFocus()
      .expectSelectedText("SomeText")
      .type("Hello")
      .expectText("Hello")
      .expectValue("SomeText")
      .expectSelectedText(null)
      .loseFocus()
      .verifyStopEditingCalled()
      .expectSelectedText(null)
      .expectText("Hello")
      .expectValue("Hello");
  }

  public void testReplaceCaseOfText() {
    myFixture
      .setProperty(getProperty(myTextView, ATTR_TEXT))
      .expectText("SomeText")
      .expectSelectedText(null)
      .gainFocus()
      .expectSelectedText("SomeText")
      .type("someText")
      .expectText("someText")
      .expectValue("SomeText")
      .expectSelectedText(null)
      .loseFocus()
      .verifyStopEditingCalled()
      .expectSelectedText(null)
      .expectText("someText")
      .expectValue("someText");
  }

  public void testReplaceAddedValue() {
    myFixture
      .setProperty(getProperty(myTextView, ATTR_ELEVATION))
      .expectText("2dp")
      .expectSelectedText(null)
      .gainFocus()
      .expectSelectedText("2dp")
      .type("foo")
      .expectSelectedText(null)
      .key(VK_ENTER)
      .expectSelectedText("foo")
      .type("bar")
      .expectSelectedText(null)
      .key(VK_ENTER)
      .expectSelectedText("bar")
      .loseFocus()
      .expectSelectedText(null)
      .expectText("bar")
      .expectValue("bar");
  }

  public void testEnterText() {
    myFixture
      .setProperty(getProperty(myTextView, ATTR_ELEVATION))
      .expectText("2dp")
      .expectSelectedText(null)
      .gainFocus()
      .expectSelectedText("2dp")
      .type("foo")
      .key(VK_ENTER)
      .verifyStopEditingCalled()
      .expectValue("foo")
      .expectSelectedText("foo")
      .loseFocus()
      .expectValue("foo");
  }

  public void testSetEmptyProperty() {
    myFixture
      .setProperty(getProperty(myTextView, ATTR_ELEVATION))
      .setProperty(EmptyProperty.INSTANCE);
  }

  private NlProperty createCollapseParallaxProperty(@NotNull NlComponent component) {
    XmlName name = new XmlName(ATTR_COLLAPSE_PARALLAX_MULTIPLIER, AUTO_URI);
    AttributeDefinition definition = new AttributeDefinition(
      name.getLocalName(), DESIGN_LIB_ARTIFACT, null, ImmutableList.of(AttributeFormat.Dimension, AttributeFormat.Fraction));
    return NlPropertyItem.create(name, definition, Collections.singletonList(component), myPropertiesManager);
  }
}
