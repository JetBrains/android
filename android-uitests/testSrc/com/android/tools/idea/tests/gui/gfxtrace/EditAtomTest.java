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
import com.android.tools.rpclib.schema.Method;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(GuiTestRunner.class)
public class EditAtomTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testEditAtom() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("CapturesApplication")
      .getCapturesToolWindow()
      .openFile("domination-launch.gfxtrace");
    guiTest.ideFrame()
      .getEditor()
      .getGfxTraceEditor()
      .selectContext("OpenGL ES context 1")
      .openEditDialog(Method.Bool).clickCancel()
      //.openEditDialog(Method.Int8).clickCancel()
      .openEditDialog(Method.Uint8).clickCancel()
      //.openEditDialog(Method.Int16).clickCancel()
      //.openEditDialog(Method.Uint16).clickCancel()
      .openEditDialog(Method.Int32).clickCancel()
      .openEditDialog(Method.Uint32).clickCancel()
      .openEditDialog(Method.Int64).clickCancel()
      //.openEditDialog(Method.Uint64).clickCancel()
      .openEditDialog(Method.Float32).clickCancel()
      //.openEditDialog(Method.Float64).clickCancel()
      .openEditDialog(Method.String).clickCancel()
      .openEditDialog(GfxTraceFixture.ENUM).clickCancel()
      .openEditDialog(GfxTraceFixture.FLAG).clickCancel()
      .assertNoEditDialogForAtomWithoutPrimitiveFields();
  }
}
