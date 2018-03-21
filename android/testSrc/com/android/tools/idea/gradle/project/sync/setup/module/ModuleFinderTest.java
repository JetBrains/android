/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings.CompositeBuild;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.util.Collections;

import static com.android.tools.idea.Projects.getBaseDirPath;

/**
 * Tests for {@link ModuleFinder}.
 */
public class ModuleFinderTest extends IdeaTestCase {
  private GradleProjectSettings myProjectSettings;
  private ModuleFinder myModulesByGradlePath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myProjectSettings = new GradleProjectSettings();
    myProjectSettings.setExternalProjectPath(getBaseDirPath(myProject).getPath());
    GradleSettings.getInstance(myProject).setLinkedProjectsSettings(Collections.singletonList(myProjectSettings));
  }

  public void testIsCompositeBuildWithoutCompositeModule() {
    // Populate projectSettings with empty composite build.
    myProjectSettings.setCompositeBuild(new CompositeBuild());

    myModulesByGradlePath = new ModuleFinder(myProject);
    assertFalse(myModulesByGradlePath.isCompositeBuild(myModule));
  }

  public void testIsCompositeBuildWithCompositeModule() {
    // Set current module as composite build.
    BuildParticipant participant = new BuildParticipant();
    participant.setProjects(Collections.singleton(myModule.getModuleFile().getParent().getPath()));

    CompositeBuild compositeBuild = new CompositeBuild();
    compositeBuild.setCompositeParticipants(Collections.singletonList(participant));
    myProjectSettings.setCompositeBuild(compositeBuild);

    myModulesByGradlePath = new ModuleFinder(myProject);
    assertTrue(myModulesByGradlePath.isCompositeBuild(myModule));
  }
}
