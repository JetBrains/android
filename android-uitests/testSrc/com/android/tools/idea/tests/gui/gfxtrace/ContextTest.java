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
package com.android.tools.idea.tests.gui.gfxtrace;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.gfxtrace.GfxTraceFixture;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class ContextTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  // TODO remove this when mock server is merged (http://ag/1574242)
  @AfterClass
  public static void killGapis() {
    try {
      Runtime rt = Runtime.getRuntime();
      rt.exec("killall gapis");
      rt.exec("killall gapir");
    }
    catch (Exception ex) {
    }
  }

  @Test
  public void testGoToAtom() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("CapturesApplication")
      .getCapturesToolWindow()
      .openFile("domination-launch.gfxtrace");

    GfxTraceFixture gfxTraceFixture = guiTest.ideFrame().getEditor().getGfxTraceEditor();
    assertThat(gfxTraceFixture.getContexts())
      .containsExactly("All contexts", "OpenGL ES context 0", "OpenGL ES context 1").inOrder();

    gfxTraceFixture.selectContext("OpenGL ES context 1");
    gfxTraceFixture.goToAtom(0);
    assertThat(gfxTraceFixture.getSelectedAtom()).isEqualTo(0L);
    assertThat(gfxTraceFixture.getContextDropDownSelection()).isEqualTo("All contexts");

    gfxTraceFixture.selectContext("OpenGL ES context 0");
    gfxTraceFixture.goToAtom(100);
    assertThat(gfxTraceFixture.getSelectedAtom()).isEqualTo(100L);
    assertThat(gfxTraceFixture.getContextDropDownSelection()).isEqualTo("OpenGL ES context 1");
  }
}
