/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.editors.hprof;

import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.CapturesToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.HprofEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.junit.Assert.assertEquals;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class HprofEditorTest extends GuiTestCase {
  private static final String SAMPLE_SNAPSHOT_NAME = "snapshot.hprof";
  private static final String CAPTURES_APPLICATION = "CapturesApplication";

  @Test
  @IdeGuiTest
  public void testOpenHprof() throws IOException {
    final IdeFrameFixture ideFrame = importProjectAndWaitForProjectSyncToFinish(CAPTURES_APPLICATION);

    CapturesToolWindowFixture capturesToolWindow = ideFrame.getCapturesToolWindow();
    capturesToolWindow.openFile(SAMPLE_SNAPSHOT_NAME);
    HprofEditorFixture editor = HprofEditorFixture.findByFileName(myRobot, ideFrame, SAMPLE_SNAPSHOT_NAME);
  }

  @Test
  @IdeGuiTest
  public void testInitialState() throws IOException {
    final IdeFrameFixture ideFrame = importProjectAndWaitForProjectSyncToFinish(CAPTURES_APPLICATION);

    CapturesToolWindowFixture capturesToolWindow = ideFrame.getCapturesToolWindow();
    capturesToolWindow.openFile(SAMPLE_SNAPSHOT_NAME);
    HprofEditorFixture editor = HprofEditorFixture.findByFileName(myRobot, ideFrame, SAMPLE_SNAPSHOT_NAME);
    assertEquals(editor.getCurrentHeapName(), "app heap");
  }
}
