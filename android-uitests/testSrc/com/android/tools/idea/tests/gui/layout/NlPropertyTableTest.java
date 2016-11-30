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
package com.android.tools.idea.tests.gui.layout;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlEditorFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI test for the layout preview window
 */
@RunWith(GuiTestRunner.class)
public class NlPropertyTableTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testScrollInViewDuringKeyboardNavigation() throws Exception {
    NlEditorFixture layout = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getLayoutEditor(true)
      .waitForRenderToFinish();

    layout.findView("TextView", 0).click();
    layout.getPropertyInspector()
      .adjustIdeFrameHeightFor(4, "ID")
      .focusAndWaitForFocusGainInProperty("ID", null)
      .assertPropertyShowing("text", null)
      .assertPropertyShowing("ID", null)
      .assertPropertyNotShowing("visibility", null)
      .tab() // ID
      .tab() // width
      .tab() // width resource editor
      .tab() // height
      .tab() // height resource editor
      .tab().assertFocusInProperty("text", null)
      .tab() // text resource editor
      .tab() // design text
      .tab() // design text resource editor
      .tab() // contentDescription
      .tab() // contentDescription resource editor
      .tab() // textAppearance
      .tab() // fontFamily
      .tab() // typeFace
      .tab() // textSize
      .tab() // textSize resource editor
      .tab() // lineSpacingExtra
      .tab() // lineSpacingExtra resource editor
      .tab().assertFocusInProperty("textColor", null)
      .tab() // textColor resource editor
      .tab() // textStyle: bold
      .tab() // textStyle: italics
      .tab() // textStyle: AllCaps
      .tab() // textAlignment: Start
      .tab() // textAlignment: Left
      .tab() // textAlignment: Center
      .tab() // textAlignment: Right
      .tab() // textAlignment: End
      .tab() // visibility
      .tab() // View all properties link
      .assertPropertyNotShowing("text", null)
      .assertPropertyNotShowing("ID", null)
      .assertPropertyShowing("visibility", null)
      .tab().assertFocusInProperty("ID", null)
      .assertPropertyShowing("ID", null)
      .assertPropertyShowing("text", null)
      .assertPropertyNotShowing("visibility", null)
      .tabBack()
      .assertPropertyNotShowing("ID", null)
      .assertPropertyNotShowing("text", null)
      .assertPropertyShowing("visibility", null);
  }
}
