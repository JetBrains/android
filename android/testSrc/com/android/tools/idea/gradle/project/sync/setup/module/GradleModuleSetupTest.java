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
package com.android.tools.idea.gradle.project.sync.setup.module;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.findFacet;
import static com.android.tools.idea.gradle.util.GradleProjects.findModuleRootFolderPath;
import static com.android.tools.idea.gradle.util.GradleWrapper.getDefaultPropertiesFilePath;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleModuleModels;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.io.IOException;
import org.gradle.tooling.model.GradleProject;
import org.mockito.Mock;

/**
 * Tests for {@link GradleModuleSetup}.
 */
public class GradleModuleSetupTest extends PlatformTestCase {
  @Mock private GradleModuleModels myModuleModels;
  @Mock private GradleSyncState mySyncState;

  private Module myModule;
  private File myBuildFile;
  private IdeModifiableModelsProvider myModelsProvider;
  private GradleProjectStub myGradleProject;
  private GradleModuleSetup myModuleSetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    new IdeComponents(project).replaceProjectService(GradleSyncState.class, mySyncState);

    String moduleName = "app";
    myModule = createModule(moduleName);

    myBuildFile = new File(findModuleRootFolderPath(myModule), SdkConstants.FN_BUILD_GRADLE);
    createIfNotExists(myBuildFile);

    myGradleProject = new GradleProjectStub("app", ":app", getBaseDirPath(project), myBuildFile, "assemble");

    myModelsProvider = new IdeModifiableModelsProviderImpl(project);
    myModuleSetup = new GradleModuleSetup();
  }

  public void testSetUpModule() throws IOException {
    when(myModuleModels.findModel(GradleProject.class)).thenReturn(myGradleProject);
    File propertiesFilePath = getDefaultPropertiesFilePath(new File(myProject.getBasePath()));
    String gradleVersion = "2.14.1";
    writeToFile(propertiesFilePath,
                String.format("distributionUrl=https\\://services.gradle.org/distributions/gradle-%s-all.zip", gradleVersion));

    myModuleSetup.setUpModule(myModule, myModelsProvider, myModuleModels);

    // Apply changes to verify state.
    ApplicationManager.getApplication().runWriteAction(() -> myModelsProvider.commit());

    GradleFacet facet = findFacet(myModule, myModelsProvider, GradleFacet.getFacetTypeId());
    assertNotNull(facet);

    GradleModuleModel gradleModuleModel = facet.getGradleModuleModel();
    assertNotNull(gradleModuleModel);

    assertEquals(":app", gradleModuleModel.getGradlePath());
    assertEquals(gradleVersion, gradleModuleModel.getGradleVersion());

    assertAbout(file()).that(gradleModuleModel.getBuildFilePath()).isEquivalentAccordingToCompareTo(myBuildFile);

    verify(mySyncState, times(1)).setLastSyncedGradleVersion(argThat(version -> version.toString().equals(gradleVersion)));
  }
}
