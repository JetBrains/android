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

import static com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link SyncProjectModelsSetup}.
 */
public class SyncProjectModelsSetupTest extends AndroidGradleTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.NEW_SYNC_INFRA_ENABLED.clearOverride();
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
}
