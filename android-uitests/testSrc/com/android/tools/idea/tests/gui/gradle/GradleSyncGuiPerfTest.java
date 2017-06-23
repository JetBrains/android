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
package com.android.tools.idea.tests.gui.gradle;

import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.JournalingUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.android.tools.idea.tests.gui.framework.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@RunIn(TestGroup.SYNC_PERFORMANCE)
@RunWith(GuiTestRunner.class)
public class GradleSyncGuiPerfTest {

  @Rule public final GradleSyncGuiPerfTestRule guiTest = new GradleSyncGuiPerfTestRule().withTimeout(20, TimeUnit.MINUTES);

  private VirtualTimeScheduler myScheduler = new VirtualTimeScheduler();
  private UsageTracker myUsageTracker;

  @Before
  public void setUp() {
    Assume.assumeNotNull(System.getenv("ANDROID_SPOOL_HOME"));
    Assume.assumeNotNull(System.getenv("SYNC_PERFTEST_PROJECT_DIR"));
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;

    myUsageTracker = new JournalingUsageTracker(new AnalyticsSettings(), myScheduler, Paths.get(System.getenv("ANDROID_SPOOL_HOME")));
    UsageTracker.setInstanceForTest(myUsageTracker);
  }

  @After
  public void tearDown() throws Exception {
    myScheduler.advanceBy(0);
    myUsageTracker.close();
    UsageTracker.cleanAfterTesting();
  }
  
  @Test
  @RunIn(TestGroup.SYNC_PERFORMANCE)
  public void syncPerfTest() throws IOException, NullPointerException {
    guiTest.openProject();

    // Run a bunch of syncs to collect a warm sync time
    for(int i = 0; i < 5; i++) {
      guiTest.ideFrame().requestProjectSync(Wait.seconds(5 * 60)).waitForGradleProjectSyncToFinish(Wait.seconds(5 * 60));
    }
  }
}

class GradleSyncGuiPerfTestRule extends GuiTestRule {

  public void openProject() throws IOException {
    File projectPath = new File(System.getenv("SYNC_PERFTEST_PROJECT_DIR"));
    GradleSyncGuiPerfSetupTestRule.createGradleWrapper(projectPath);
    updateGradleVersions(projectPath);
    VirtualFile toSelect = VfsUtil.findFileByIoFile(projectPath, true);
    ApplicationManager.getApplication().invokeAndWait(() -> GradleProjectImporter.getInstance().openProject(toSelect));
  }

  @Override
  protected void tearDownProject() {
    if (ideFrame().target().isShowing()) {
      ideFrame().closeProject();
    }
    GuiTests.refreshFiles();
  }

  @Override
  public GradleSyncGuiPerfTestRule withTimeout(long timeout, @NotNull TimeUnit timeUnits) {
    super.withTimeout(timeout, timeUnits);
    return this;
  }
}