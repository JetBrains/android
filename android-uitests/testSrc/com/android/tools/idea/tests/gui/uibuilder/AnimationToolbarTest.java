/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.uibuilder;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.idea.common.editor.DesignerEditorPanel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.uibuilder.editor.AnimationToolbar;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.exception.ComponentLookupException;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AnimationToolbarTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  @Test
  public void toolbarDisplayedForAnimatedVectors() throws Exception {
    StudioFlags.NELE_ANIMATIONS_PREVIEW.override(true);
    guiTest.importSimpleApplication();
    IdeFrameFixture frame = guiTest.ideFrame();

    EditorFixture editor = frame.getEditor().open("app/src/main/res/drawable/animated_vector.xml", EditorFixture.Tab.DESIGN);
    DesignerEditorPanel panel = editor.getLayoutEditor(false).waitForRenderToFinish().target();
    AnimationToolbar toolbar = frame.robot().finder().findByType(panel, AnimationToolbar.class);
    assertThat(toolbar).isNotNull();

    // Other drawables should not display the toolbar.
    frame.getEditor().open("app/src/main/res/drawable/vector.xml", EditorFixture.Tab.DESIGN);

    panel = editor.getLayoutEditor(false).waitForRenderToFinish().target();
    try {
      frame.robot().finder().findByType(panel, AnimationToolbar.class);
      fail("We shouldn't find an AnimationToolbar when opening vector files.");
    }
    catch (ComponentLookupException expected) {
      // We expect to throw ComponentLookupException as we shouldn't find an AnimationToolbar
    }
  }

  @After
  public void tearDown() {
    StudioFlags.NELE_ANIMATIONS_PREVIEW.clearOverride();
    StudioFlags.NELE_MOTION_LAYOUT_ANIMATIONS.clearOverride();
  }
}
