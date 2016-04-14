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
package com.android.tools.idea.gradle.util;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.CLASSPATH;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

/**
 * Tests for {@link GradleUtil#updateGradlePluginVersion}.
 */
public class UpdateGradlePluginVersionTest extends AndroidGradleTestCase {
  public void testUpdateGradlePluginVersion() throws Throwable {
    loadProject("projects/sync/multiproject", false);
    Project project = myFixture.getProject();

    final GradleBuildModel buildModel = getBuildModel(project);

    ArtifactDependencyModel androidPluginDependency = findGradlePlugin(buildModel);
    assertNotNull(androidPluginDependency);
    androidPluginDependency.setVersion("1.0.0");

    runWriteCommandAction(project, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    boolean updated = GradleUtil.updateGradlePluginVersion(project, "2.0.0", null);
    assertTrue(updated);

    buildModel.reparse();

    androidPluginDependency = findGradlePlugin(buildModel);
    assertNotNull(androidPluginDependency);

    assertEquals("2.0.0", androidPluginDependency.version());
  }

  public void testUpdateGradlePluginVersionWhenPluginHasAlreadyUpdatedVersion() throws Throwable {
    loadProject("projects/sync/multiproject", false);
    Project project = myFixture.getProject();

    final GradleBuildModel buildModel = getBuildModel(project);

    ArtifactDependencyModel androidPluginDependency = findGradlePlugin(buildModel);
    assertNotNull(androidPluginDependency);
    androidPluginDependency.setVersion("2.0.0");

    runWriteCommandAction(project, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    boolean updated = GradleUtil.updateGradlePluginVersion(project, "2.0.0", null);
    assertTrue(updated);

    buildModel.reparse();

    androidPluginDependency = findGradlePlugin(buildModel);
    assertNotNull(androidPluginDependency);

    assertEquals("2.0.0", androidPluginDependency.version());
  }

  @NotNull
  private static GradleBuildModel getBuildModel(@NotNull Project project) {
    VirtualFile buildFile = project.getBaseDir().findChild(FN_BUILD_GRADLE);
    assertNotNull(buildFile);

    return GradleBuildModel.parseBuildFile(buildFile, project);
  }

  @Nullable
  private static ArtifactDependencyModel findGradlePlugin(@NotNull GradleBuildModel buildModel) {
    List<ArtifactDependencyModel> dependencies = buildModel.buildscript().dependencies().artifacts(CLASSPATH);
    for (ArtifactDependencyModel dependency : dependencies) {
      if ("com.android.tools.build".equals(dependency.group()) && "gradle".equals(dependency.name())) {
        return dependency;
      }
    }
    return null;
  }
}
