/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.sync;

import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

public class Ide2GradleProjectSyncTest extends AndroidGradleTestCase {

  public void testModuleDependencyAddition() throws Exception {
    loadProject("projects/sync/multiproject");

    // Ensure that 'module1' doesn't have 'module2' configured as a dependency.
    ProjectStructureHelper helper = ServiceManager.getService(ProjectStructureHelper.class);
    final Module ideModule1 = helper.findIdeModule("module1", getProject());
    assertNotNull(ideModule1);
    final Module ideModule2 = helper.findIdeModule("module2", getProject());
    assertNotNull(ideModule2);
    VirtualFile module1GradleConfigFile = GradleUtil.getGradleBuildFile(ideModule1);
    assertNotNull(module1GradleConfigFile);
    assertFalse(VfsUtilCore.loadText(module1GradleConfigFile).contains("module2"));

    // Explicitly configure 'project modification' changes processing as corresponding handlers are not configured by default
    // for test project (and we don't them to be configured).
    Ide2GradleProjectSyncFacade facade = new Ide2GradleProjectSyncFacade();
    facade.testCheckChanges(getProject()); // Store current project state.

    // Configure 'module2' as a dependency for 'module1' at the ide side.
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(ideModule1);
        ModifiableRootModel model = moduleRootManager.getModifiableModel();
        model.addModuleOrderEntry(ideModule2);
        model.commit();
      }
    });

    facade.testCheckChanges(getProject());
    PsiFile module1GradlePsiFile = PsiManager.getInstance(getProject()).findFile(module1GradleConfigFile);
    assertNotNull(module1GradlePsiFile);
    // We check the PSI here because gradle config modification (GradleBuildFile.setValue()) is performed via PSI and file/document
    // syncs are performed from a separate thread, so, we just don't want to wait the sync to be finished.
    if (!module1GradlePsiFile.getText().contains("module2")) {
      fail("New module dependency added at the IDE side is not propagated to gradle config files");
    }
  }
}
