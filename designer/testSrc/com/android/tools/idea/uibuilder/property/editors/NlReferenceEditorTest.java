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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.property.EmptyProperty;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.uibuilder.property.fixtures.NlReferenceEditorFixture;
import com.google.common.collect.ImmutableList;
import com.intellij.util.xml.XmlName;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static com.android.SdkConstants.*;
import static java.awt.event.KeyEvent.VK_ENTER;
import static java.awt.event.KeyEvent.VK_ESCAPE;

public class NlReferenceEditorTest extends PropertyTestCase {
  private NlReferenceEditorFixture myFixture;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture = NlReferenceEditorFixture.createForInspector(getProject());
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
      .type("4")
      .expectSelectedText(null)
      .key(VK_ENTER)
      .expectSelectedText("4dp")
      .type("6")
      .expectSelectedText(null)
      .key(VK_ENTER)
      .expectSelectedText("6dp")
      .loseFocus()
      .expectSelectedText(null)
      .expectText("6dp")
      .expectValue("6dp");
  }

  public void testEnterSimpleDimension() {
    myFixture
      .setProperty(getProperty(myTextView, ATTR_ELEVATION))
      .expectText("2dp")
      .expectSelectedText(null)
      .gainFocus()
      .expectSelectedText("2dp")
      .type("4")
      .key(VK_ENTER)
      .verifyStopEditingCalled()
      .expectValue("4dp")
      .expectSelectedText("4dp")
      .loseFocus()
      .expectValue("4dp");
  }

  public void testTextHasNoSlider() {
    myFixture
      .setProperty(getProperty(myTextView, ATTR_TEXT))
      .gainFocus()
      .expectSliderVisible(false);
  }

  public void testSliderHidesDependingOnTotalWidth() {
    myFixture
      .setWidth(100)
      .setProperty(getProperty(myTextView, ATTR_ELEVATION))
      .gainFocus()
      .expectSliderVisible(false)
      .setWidth(150)
      .expectSliderVisible(true)
      .setWidth(100)
      .expectSliderVisible(false);
  }

  public void testSliderClicksAreNotImmediatelyRecognized() throws Exception {
    myFixture
      .setWidth(150)
      .setProperty(getProperty(myTextView, ATTR_ELEVATION))
      .gainFocus()
      .expectSliderVisible(true)
      .clickOnSlider(.5)
      .expectText("2dp");
  }

  public void testClickOnSliderChangesInspectorValue() throws Exception {
    myFixture
      .setWidth(150)
      .setProperty(getProperty(myTextView, ATTR_ELEVATION))
      .expectText("2dp")
      .gainFocus()
      .expectSliderVisible(true)
      .updateTime(1)
      .clickOnSlider(1.)
      .expectText("24dp")
      .expectValue("24dp");
  }

  public void testClickOnSliderDoesNotChangeTableValueBeforeFocusLoss() throws Exception {
    myFixture.tearDown();
    myFixture = NlReferenceEditorFixture.createForTable(getProject());
    myFixture
      .setWidth(190)
      .setProperty(getProperty(myTextView, ATTR_ELEVATION))
      .expectText("2dp")
      .gainFocus()
      .expectSliderVisible(true)
      .updateTime(1)
      .clickOnSlider(1.)
      .expectText("24dp")
      .expectValue("2dp")
      .loseFocus()
      .expectValue("24dp");
  }

  public void testClickOnSliderForMinHeight() throws Exception {
    myFixture
      .setWidth(150)
      .setProperty(getProperty(myTextView, ATTR_MIN_HEIGHT))
      .expectText("")
      .gainFocus()
      .expectSliderVisible(true)
      .updateTime(1)
      .clickOnSlider(1.)
      .expectText("250dp")
      .expectValue("250dp")
      .loseFocus()
      .expectValue("250dp");
  }

  public void testClickOnSliderForCollapseParallaxMultiplier() throws Exception {
    myFixture
      .setWidth(150)
      .setProperty(createCollapseParallaxProperty(myImageViewInCollapsingToolbarLayout))
      .expectText(".2")
      .gainFocus()
      .expectSliderVisible(true)
      .updateTime(1)
      .clickOnSlider(.5)
      .expectText("0.5")
      .expectValue("0.5")
      .clickOnSlider(1.)
      .expectText("1.0")
      .expectValue("1.0")
      .loseFocus()
      .expectValue("1.0");
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
