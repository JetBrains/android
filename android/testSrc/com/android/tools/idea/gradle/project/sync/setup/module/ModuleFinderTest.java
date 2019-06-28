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

import com.intellij.openapi.module.Module;
import com.intellij.testFramework.JavaProjectTestCase;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings.CompositeBuild;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.util.Collections;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ModuleFinder}.
 */
public class ModuleFinderTest extends JavaProjectTestCase {
  private GradleProjectSettings myProjectSettings;
  private ModuleFinder myFinder;

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

    myFinder = new ModuleFinder(myProject);
    assertFalse(myFinder.isCompositeBuild(myModule));
  }

  public void testIsCompositeBuildWithCompositeModule() {
    // Set current module as composite build.
    BuildParticipant participant = new BuildParticipant();
    participant.setProjects(Collections.singleton(myModule.getModuleFile().getParent().getPath()));

    CompositeBuild compositeBuild = new CompositeBuild();
    compositeBuild.setCompositeParticipants(Collections.singletonList(participant));
    myProjectSettings.setCompositeBuild(compositeBuild);

    myFinder = new ModuleFinder(myProject);
    assertTrue(myFinder.isCompositeBuild(myModule));
  }

  public void testIsCompositeBuildWithBackSlashInPath() {
    // Set current module as composite build.
    BuildParticipant participant = new BuildParticipant();
    participant.setProjects(Collections.singleton(toCanonicalPath("c:\\path\\to\\module")));

    CompositeBuild compositeBuild = new CompositeBuild();
    compositeBuild.setCompositeParticipants(Collections.singletonList(participant));
    myProjectSettings.setCompositeBuild(compositeBuild);

    Module module = mock(Module.class);
    when(module.getModuleFilePath()).thenReturn("c:\\path\\to\\module\\module.iml");
    myFinder = new ModuleFinder(myProject);
    assertTrue(myFinder.isCompositeBuild(module));
  }

  public void testIsCompositeBuildWithSlashInPath() {
    // Set current module as composite build.
    BuildParticipant participant = new BuildParticipant();
    participant.setProjects(Collections.singleton(toCanonicalPath("/path/to/module")));

    CompositeBuild compositeBuild = new CompositeBuild();
    compositeBuild.setCompositeParticipants(Collections.singletonList(participant));
    myProjectSettings.setCompositeBuild(compositeBuild);

    Module module = mock(Module.class);
    when(module.getModuleFilePath()).thenReturn("/path/to/module/module.iml");
    myFinder = new ModuleFinder(myProject);
    assertTrue(myFinder.isCompositeBuild(module));
  }
}
