/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.manifest;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.model.MergedManifestModificationTracker;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ManifestModificationTrackerTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testModificationTracker_incrementAfterStartup() throws Exception {
    guiTest.openProjectAndWaitForIndexingToFinish("simple");
    Module module = guiTest.ideFrame().getModule("simple");
    MergedManifestModificationTracker tracker = MergedManifestModificationTracker.getInstance(module);

    DumbService.getInstance(guiTest.ideFrame().getProject()).waitForSmartMode();

    assertThat(tracker.getModificationCount()).isEqualTo(1);
    guiTest.ideFrame().closeProject();
  }
}
