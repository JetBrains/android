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

import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.openPreparedProject;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.prepareGradleProject;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.junit.Assert.assertTrue;

import com.android.testutils.junit4.OldAgpTest;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.GradleIntegrationTest;
import com.android.tools.idea.testing.OpenPreparedProjectOptions;
import com.android.tools.idea.testing.TestProjectPaths;
import java.io.File;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.SystemIndependent;
import org.junit.Rule;
import org.junit.Test;

/**
 * Integration test for Gradle Sync with old versions of Android plugin.
 */
@OldAgpTest(agpVersions = "3.1.4", gradleVersions = "5.3.1")
public class SyncWithUnsupportedAGPPluginTest implements GradleIntegrationTest {
  @Rule
  public AndroidProjectRule projectRule = AndroidProjectRule.withAndroidModels();

  @Test
  public void testGradleSyncFails() {
    String[] exceptionTest = new String[1];
    prepareGradleProject(this, SIMPLE_APPLICATION, "root", "5.3.1", "3.1.4", null, null, "32");
    openPreparedProject(this, "root",
                        new OpenPreparedProjectOptions(
                          emptySet(),
                          project -> null,
                          (project, string) -> null,
                          (project, e) -> {
                            exceptionTest[0] = e.getMessage();
                            return null;
                          })
      , project -> null);
    assertThat(exceptionTest[0]).contains("The project is using an incompatible version (AGP 3.1.4) of the Android " +
                                                           "Gradle plugin. Minimum supported version is AGP 3.2.0.");
  }

  @NotNull
  @Override
  public @SystemDependent String getBaseTestPath() {
    return projectRule.fixture.getTempDirPath();
  }

  @NotNull
  @Override
  public @SystemIndependent String getTestDataDirectoryWorkspaceRelativePath() {
    return TestProjectPaths.TEST_DATA_PATH;
  }

  @NotNull
  @Override
  public Collection<File> getAdditionalRepos() {
    return emptyList();
  }
}
