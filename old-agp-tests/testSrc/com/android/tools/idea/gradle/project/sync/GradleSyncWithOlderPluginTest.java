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
package com.android.tools.idea.gradle.project.sync;

import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.text.StringUtil.equalsIgnoreCase;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;

import com.android.testutils.junit4.OldAgpTest;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.List;
import org.jetbrains.plugins.gradle.internal.daemon.DaemonState;
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

/**
 * Integration test for Gradle Sync with old versions of Android plugin.
 */
@OldAgpTest(agpVersions = "3.3.2", gradleVersions = "5.3.1")
public class GradleSyncWithOlderPluginTest extends AndroidGradleTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(projectSettings));
  }

  /**
   * Verify that Gradle daemons can be stopped for Gradle 5.3.1 (b/155991417).
   * @throws Exception
   */
  public void testDaemonStops5Dot3Dot1() throws Exception {
    loadProject(SIMPLE_APPLICATION, null, "5.3.1", "3.3.2", "32");
    verifyDaemonStops();
  }

  void verifyDaemonStops() throws Exception {
    GradleDaemonServices.stopDaemons();
    assertThat(areGradleDaemonsRunning()).isFalse();
    requestSyncAndWait();
    assertThat(areGradleDaemonsRunning()).isTrue();
    GradleDaemonServices.stopDaemons();
    assertThat(areGradleDaemonsRunning()).isFalse();
  }

  static boolean areGradleDaemonsRunning() {
    List<DaemonState> daemonStatus = GradleDaemonServices.getDaemonsStatus();
    for (DaemonState status : daemonStatus) {
      if (!equalsIgnoreCase(status.getStatus(), "stopped")) {
        return true;
      }
    }
    return false;
  }
}
