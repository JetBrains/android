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
package com.android.tools.idea.gradle.project.upgrade;

import com.android.ide.common.repository.AgpVersion;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.upgrade.AndroidPluginVersionUpdater.UpdateResult;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.dsl.api.GradleBuildModel.parseBuildFile;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.CLASSPATH;
import static com.android.tools.idea.testing.TestProjectPaths.SYNC_MULTIPROJECT;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

/**
 * Tests for {@link AndroidPluginVersionUpdater}.
 */
public class AndroidPluginVersionUpdaterIntegrationTest extends AndroidGradleTestCase {
  private AndroidPluginVersionUpdater myVersionUpdater;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myVersionUpdater = new AndroidPluginVersionUpdaterImpl(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    myVersionUpdater = null;
    super.tearDown();
  }

  public void testUpdatePluginVersion() throws Throwable {
    loadProject(SYNC_MULTIPROJECT);
    setAndroidPluginVersion("1.0.0");

    UpdateResult result = myVersionUpdater.updatePluginVersion(AgpVersion.parse("20.0.0"), null, null);
    assertTrue(result.isPluginVersionUpdated());
    assertFalse(result.isGradleVersionUpdated());

    GradleBuildModel buildModel = verifyAndroidPluginVersion("20.0.0");

    // Make sure Google Maven Repository is on buildscript after updating plugin (b/69977310)
    assertTrue(buildModel.buildscript().repositories().hasGoogleMavenRepository());
  }

  public void testUpdatePluginVersionWhenPluginHasAlreadyUpdatedVersion() throws Throwable {
    loadProject(SYNC_MULTIPROJECT);

    setAndroidPluginVersion("20.0.0");

    UpdateResult result = myVersionUpdater.updatePluginVersion(AgpVersion.parse("20.0.0"), null, null);
    assertFalse(result.isPluginVersionUpdated());
    assertFalse(result.isGradleVersionUpdated());

    verifyAndroidPluginVersion("20.0.0");
  }

  private void setAndroidPluginVersion(@NotNull String version) {
    GradleBuildModel buildModel = getTopLevelBuildModel(getProject());
    ArtifactDependencyModel androidPluginDependency = findAndroidPlugin(buildModel);
    androidPluginDependency.version().setValue(version);

    runWriteCommandAction(getProject(), buildModel::applyChanges);
  }

  private GradleBuildModel verifyAndroidPluginVersion(@NotNull String expectedVersion) {
    GradleBuildModel buildModel = getTopLevelBuildModel(getProject());
    assertEquals(expectedVersion, findAndroidPlugin(buildModel).version().toString());
    return buildModel;
  }

  @NotNull
  private static GradleBuildModel getTopLevelBuildModel(@NotNull Project project) {
    VirtualFile buildFile = PlatformTestUtil.getOrCreateProjectBaseDir(project).findChild(FN_BUILD_GRADLE);
    assertNotNull(buildFile);
    return parseBuildFile(buildFile, project);
  }

  @NotNull
  private static ArtifactDependencyModel findAndroidPlugin(@NotNull GradleBuildModel buildModel) {
    List<? extends ArtifactDependencyModel> dependencies = buildModel.buildscript().dependencies().artifacts(CLASSPATH);
    for (ArtifactDependencyModel dependency : dependencies) {
      if (AndroidPluginInfo.isAndroidPlugin(dependency.name().forceString(), dependency.group().toString())) {
        return dependency;
      }
    }
    throw new AssertionFailedError("Failed to find Android plugin dependency");
  }
}