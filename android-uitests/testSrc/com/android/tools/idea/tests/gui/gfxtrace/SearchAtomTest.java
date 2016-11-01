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
public class SearchAtomTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  // TODO remove this when stub/fake server is merged (http://ag/1574242)
  @AfterClass
  public static void killGapis() {
    try {
      Runtime rt = Runtime.getRuntime();
      rt.exec("killall gapis");
      rt.exec("killall gapir");
    }
    catch (Exception ignored) {
    }
  }

  @Test
  public void testEditAtom() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("CapturesApplication")
      .getCapturesToolWindow()
      .openFile("domination-launch.gfxtrace");
    GfxTraceFixture gfxTrace = guiTest.ideFrame()
      .getEditor()
      .getGfxTraceEditor()
      .selectContext("OpenGL ES context 0");

    // search for item that should not be found in this context
    gfxTrace.searchForText("glClear");
    assertThat(gfxTrace.hasSelection()).isFalse();

    gfxTrace.selectContext("OpenGL ES context 1");

    gfxTrace.searchForText("glClea.");
    assertThat(gfxTrace.hasSelection()).isFalse();

    gfxTrace.searchForRegex("glClea.");
    long firstFoundId = gfxTrace.getSelectedAtom();
    assertThat(gfxTrace.getAtomAsString(firstFoundId)).contains("glClear");

    gfxTrace.searchAgain();
    long secondFoundId = gfxTrace.getSelectedAtom();
    assertThat(gfxTrace.getAtomAsString(secondFoundId)).contains("glClear");

    assertThat(firstFoundId).isLessThan(secondFoundId);
  }
}
