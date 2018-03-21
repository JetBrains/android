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
package com.android.tools.idea.common.property.editors;

import com.android.tools.idea.uibuilder.property.EmptyProperty;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.uibuilder.property.fixtures.EnumEditorFixture;

import static com.android.SdkConstants.*;
import static java.awt.event.KeyEvent.*;

public class EnumEditorTest extends PropertyTestCase {
  private EnumEditorFixture myEditorFixture;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    myEditorFixture = EnumEditorFixture.create(EnumEditor::createForTest);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myEditorFixture.tearDown();
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myEditorFixture = null;
    }
    finally {
      super.tearDown();
    }
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static String getTestDataPath() {
    return getAndroidPluginHome() + "/testData";
  }

  public void testEscapeRestoresOriginalAfterTyping() {
    myEditorFixture
      .setProperty(getProperty(myTextView, ATTR_LAYOUT_WIDTH))
      .expectText("wrap_content")
      .expectSelectedText(null)
      .gainFocus()
      .expectSelectedText("wrap_content")
      .type("5")
      .key(VK_ESCAPE)
      .verifyCancelEditingCalled()
      .expectValue("wrap_content")
      .expectSelectedText("wrap_content");
  }

  public void testFocusLoss() {
    myEditorFixture
      .setProperty(getProperty(myTextView, ATTR_LAYOUT_WIDTH))
      .expectText("wrap_content")
      .expectSelectedText(null)
      .gainFocus()
      .expectSelectedText("wrap_content")
      .type("match_parent")
      .expectValue("wrap_content")
      .expectSelectedText(null)
      .loseFocus()
      .expectSelectedText(null)
      .expectText("match_parent")
      .expectValue("match_parent");
  }

  public void testEnterDimensionFromResourceValue() {
    myEditorFixture
      .setProperty(getProperty(myTextView, ATTR_LAYOUT_WIDTH))
      .gainFocus()
      .type("@android:dimen/notification_large_icon_width")
      .key(VK_ENTER)
      .expectValue("@android:dimen/notification_large_icon_width")
      .expectSelectedText("@android:dimen/notification_large_icon_width")
      .loseFocus()
      .expectText("64dp")
      .expectSelectedText(null)
      .gainFocus()
      .expectText("@android:dimen/notification_large_icon_width")
      .loseFocus()
      .expectText("64dp")
      .expectValue("@android:dimen/notification_large_icon_width");
  }

  public void testEnterOnClick() {
    myEditorFixture
      .setProperty(getProperty(myButton, ATTR_ON_CLICK))
      .expectText("none")
      .gainFocus()
      .expectText("")
      .type("sendEmail")
      .key(VK_ENTER)
      .loseFocus()
      .expectText("sendEmail")
      .expectValue("sendEmail");
  }

  public void testSetEmptyProperty() {
    myEditorFixture
      .setProperty(getProperty(myButton, ATTR_VISIBILITY))
      .setProperty(EmptyProperty.INSTANCE);
  }
}
