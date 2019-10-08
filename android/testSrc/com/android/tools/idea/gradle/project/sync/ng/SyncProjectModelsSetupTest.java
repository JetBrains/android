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

import static com.android.tools.idea.gradle.project.sync.ng.SyncProjectModelsSetup.renameProject;
import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

/**
 * Tests for {@link SyncProjectModelsSetup}.
 */
public class SyncProjectModelsSetupTest extends AndroidGradleTestCase {
  private IdeModifiableModelsProvider myModelsProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true);
    myModelsProvider = new IdeModifiableModelsProviderImpl(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.NEW_SYNC_INFRA_ENABLED.clearOverride();
      ApplicationManager.getApplication().runWriteAction(() -> myModelsProvider.dispose());
      myModelsProvider = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testReSyncWithNativeBuildRemoved() throws Throwable {
    loadProject(HELLO_JNI);
    Module appModule = myModules.getAppModule();

    // Verify that NdkFacet and cpp folder exists.
    assertNotNull(NdkFacet.getInstance(appModule));
    assertTrue(cppFolderFoundInModule(appModule));

    // Remove cmake and ndkBuild.
    ProjectBuildModel projectModel = ProjectBuildModel.get(getProject());
    projectModel.getModuleBuildModel(appModule).android().externalNativeBuild().removeCMake().removeNdkBuild();
    runWriteCommandAction(getProject(), projectModel::applyChanges);

    // Sync the project again.
    requestSyncAndWait();
    appModule = myModules.getAppModule();

    // Verify that NdkFacet and cpp folder doesn't exist.
    assertNull(NdkFacet.getInstance(appModule));
    assertFalse(cppFolderFoundInModule(appModule));
  }

  public void testRenameProject() {
    SyncProjectModels projectModels = mock(SyncProjectModels.class);
    when(projectModels.getProjectName()).thenReturn("newName");
    Project project = getProject();

    // Verify the default name is test method name.
    assertEquals("testRenameProject", project.getName());
    // Invoke method to test.
    renameProject(projectModels, project);
    // Verify that project name is updated to the name in SyncProjectModels.
    assertEquals("newName", project.getName());
  }

  private static boolean cppFolderFoundInModule(@NotNull Module module) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    VirtualFile[] roots = rootManager.getSourceRoots(false /* do not include tests */);

    for (VirtualFile root : roots) {
      if (root.getName().equals("cpp")) {
        return true;
      }
    }
    return false;
  }

  public void testContentEntriesWithNativeProject() throws Throwable {
    loadProject(HELLO_JNI);

    ModifiableRootModel rootModel = myModelsProvider.getModifiableRootModel(myModules.getAppModule());
    ContentEntry[] entries = rootModel.getContentEntries();
    assertThat(entries.length).isEqualTo(1);
    ContentEntry entry = entries[0];
    Set<String> sourceFolders = entry.getSourceFolders(JavaSourceRootType.SOURCE)
                                     .stream()
                                     .map(SourceFolder::toString)
                                     .collect(Collectors.toSet());
    Set<String> testSourceFolders = entry.getSourceFolders(JavaSourceRootType.TEST_SOURCE)
                                         .stream()
                                         .map(SourceFolder::toString)
                                         .collect(Collectors.toSet());

    // Verify that both of cpp and java folders are setup correctly.
    assertThat(sourceFolders).contains(getIdeaUrl("app/src/main/cpp"));
    assertThat(sourceFolders).contains(getIdeaUrl("app/src/main/java"));
    assertThat(testSourceFolders).contains(getIdeaUrl("app/src/test/java"));
    assertThat(testSourceFolders).contains(getIdeaUrl("app/src/androidTest/java"));
  }

  @NotNull
  private String getIdeaUrl(@NotNull String relativePath) {
    return pathToIdeaUrl(new File(getProjectFolderPath(), relativePath));
  }
}
