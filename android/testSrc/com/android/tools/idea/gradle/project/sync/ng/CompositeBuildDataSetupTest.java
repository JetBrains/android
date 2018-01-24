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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.intellij.testFramework.IdeaTestCase;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.mockito.Mock;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link CompositeBuildDataSetup}.
 */
public class CompositeBuildDataSetupTest extends IdeaTestCase {
  @Mock private CachedProjectModels myCachedProjectModels;
  @Mock private SyncProjectModels mySyncProjectModels;

  private CompositeBuildDataSetup myCompositeBuildDataSetup;
  private GradleProjectSettings myProjectSettings;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myCompositeBuildDataSetup = new CompositeBuildDataSetup();
    myProjectSettings = new GradleProjectSettings();
    myProjectSettings.setExternalProjectPath(getBaseDirPath(myProject).getPath());
    GradleSettings.getInstance(myProject).setLinkedProjectsSettings(singletonList(myProjectSettings));
  }

  public void testSetupCompositeBuildDataFromCache() throws Exception {
    BuildParticipant buildParticipant = new BuildParticipant();
    when(myCachedProjectModels.getBuildParticipants()).thenReturn(singletonList(buildParticipant));

    // Setup composite build data from cache.
    myCompositeBuildDataSetup.setupCompositeBuildData(myCachedProjectModels, myProject);
    // Verify CompositeBuild data is setup in ProjectSettings.
    assertThat(myProjectSettings.getCompositeBuild().getCompositeParticipants()).containsExactly(buildParticipant);
  }

  public void testSetupCompositeBuildDataFromSyncModels() throws Exception {
    // Setup SyncProjectModels in the following structure:
    // RootProject  :app
    // RootProject  :lib
    //     Project1 :app
    //     Project1 :lib
    //     Project2 :app
    //     Project2 :lib

    List<SyncModuleModels> moduleModels = new ArrayList<>();
    // Modules for root project.
    moduleModels.addAll(createSyncModuleModels("/path/to/root", true));
    // Modules for project1.
    moduleModels.addAll(createSyncModuleModels("/path/to/project1", false));
    // Modules for project2.
    moduleModels.addAll(createSyncModuleModels("/path/to/project2", false));

    when(mySyncProjectModels.getSyncModuleModels()).thenReturn(moduleModels);

    CachedProjectModels.Factory cacheFactory = new CachedProjectModels.Factory();
    CachedProjectModels cache = cacheFactory.createNew();
    // Setup composite build data from SyncProjectModels.
    myCompositeBuildDataSetup.setupCompositeBuildData(mySyncProjectModels, cache, myProject);

    // Verify Composite participants contains Project1 and Project2.
    List<BuildParticipant> participants = myProjectSettings.getCompositeBuild().getCompositeParticipants();
    assertThat(participants).hasSize(2);

    // Verify root path for build participants are correct, and doesn't contain root project.
    List<String> rootPaths = participants.stream().map(p -> p.getRootPath()).collect(Collectors.toList());
    assertThat(rootPaths).containsExactly("/path/to/project1", "/path/to/project2");

    // Verify that build participants is saved to cache .
    assertEquals(cache.getBuildParticipants(), participants);
  }

  private List<SyncModuleModels> createSyncModuleModels(@NotNull String projectPath, boolean isRootProject) {
    BuildIdentifier buildId = mock(BuildIdentifier.class);
    when(buildId.getRootDir()).thenReturn(new File(projectPath));
    if (isRootProject) {
      when(mySyncProjectModels.getRootBuildId()).thenReturn(buildId);
    }
    // Create app module.
    SyncModuleModels appModule = mock(SyncModuleModels.class);
    when(appModule.getBuildId()).thenReturn(buildId);
    GradleProject appModuleGradleProject = mock(GradleProject.class);
    when(appModuleGradleProject.getProjectDirectory()).thenReturn(new File(projectPath, "app"));
    when(appModule.findModel(GradleProject.class)).thenReturn(appModuleGradleProject);
    // Create lib module.
    SyncModuleModels libModule = mock(SyncModuleModels.class);
    when(libModule.getBuildId()).thenReturn(buildId);
    GradleProject libModuleGradleProject = mock(GradleProject.class);
    when(libModuleGradleProject.getProjectDirectory()).thenReturn(new File(projectPath, "lib"));
    when(libModule.findModel(GradleProject.class)).thenReturn(libModuleGradleProject);

    return Arrays.asList(appModule, libModule);
  }
}
