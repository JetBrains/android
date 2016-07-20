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

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.CapturesToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.HprofEditorFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class HprofEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String SAMPLE_SNAPSHOT_NAME = "snapshot.hprof";
  private static final String CAPTURES_APPLICATION = "CapturesApplication";

  private CapturesToolWindowFixture myCapturesToolWindowFixture;
  private HprofEditorFixture myDefaultEditor;

  @Before
  public void init() throws IOException {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
    guiTest.importProjectAndWaitForProjectSyncToFinish(CAPTURES_APPLICATION);

    myCapturesToolWindowFixture = guiTest.ideFrame().getCapturesToolWindow();
    myCapturesToolWindowFixture.openFile(SAMPLE_SNAPSHOT_NAME);
    myDefaultEditor = HprofEditorFixture.findByFileName(guiTest.robot(), guiTest.ideFrame(), SAMPLE_SNAPSHOT_NAME);
  }

  @Ignore("go/studio-builder/builders/ubuntu-studio-master-dev-uitests/builds/72")
  @Test
  public void testInitialState() throws IOException {
    myDefaultEditor.assertCurrentHeapName("App heap");
    myDefaultEditor.assertCurrentClassesViewMode("Class List View");
    JTreeFixture classesTree = myDefaultEditor.getClassesTree().requireNotEditable().requireNoSelection();
    assertNotNull(classesTree.node(0));

    JTreeFixture instancesTree = myDefaultEditor.getInstancesTree().requireNotEditable().requireNoSelection();
    try {
      instancesTree.node(0);
      fail();
    }
    catch (IndexOutOfBoundsException ignored) {}

    JTreeFixture referencesTree = myDefaultEditor.getInstanceReferenceTree().requireNotEditable().requireNoSelection();
    try {
      referencesTree.node(0);
      fail();
    }
    catch (IndexOutOfBoundsException ignored) {}
  }
}
