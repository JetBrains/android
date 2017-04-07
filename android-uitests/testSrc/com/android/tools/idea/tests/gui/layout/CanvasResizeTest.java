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
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI tests for the canvas resizing interaction
 */
@RunWith(GuiTestRunner.class)
public class CanvasResizeTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void resizeAndSnap() throws Exception {
    guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(false)
      .waitForRenderToFinish()
      .showOnlyDesignView()
      .startResizeInteraction()
      .resizeToAndroidSize(638, 365) // Size of Nexus 5 in landscape is (640 x 360)
      .endResizeInteraction()
      .waitForRenderToFinish()
      .getConfigToolbar()
      .requireOrientation("Landscape")
      .requireDevice("Nexus 5");
  }
}
