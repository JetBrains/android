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
package com.android.tools.idea.tests.gui.gradle;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.RenameDialogFixture;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.util.containers.ContainerUtil;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.util.AndroidBundle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class RenameTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void sourceRoot() throws Exception {
    guiTest.importSimpleApplication();
    final Project project = guiTest.ideFrame().getProject();
    Module appModule = guiTest.ideFrame().getModule("app");

    VirtualFile mainJavaRoot = ContainerUtil.find(ModuleRootManager.getInstance(appModule).getSourceRoots(),
                                                  vf -> vf.getPath().endsWith("src/main/java"));
    assertWithMessage("Failed to find the main java source root.").that(mainJavaRoot).isNotNull();

    PsiDirectory directory = GuiQuery.getNonNull(() -> PsiManager.getInstance(project).findDirectory(mainJavaRoot));
    DataContext dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PSI_ELEMENT, directory)
        .add(CommonDataKeys.PROJECT, project)
        .add(PlatformCoreDataKeys.MODULE, appModule)
        .build();

    for (final RenameHandler handler : RenameHandler.EP_NAME.getExtensionList()) {
      if (handler.isAvailableOnDataContext(dataContext)) {
        final RenameDialogFixture renameDialog = RenameDialogFixture.startFor(directory, handler, guiTest.robot());
        assertThat(renameDialog.warningExists(null)).isFalse();
        renameDialog.setNewName(renameDialog.getNewName() + 1);
        // 'Rename dialog' show a warning asynchronously to the text change, that's why we wait here for the
        // warning to appear
        Wait.seconds(1).expecting("error text to appear")
          .until(() -> renameDialog.warningExists(AndroidBundle.message("android.refactoring.gradle.warning.rename.source.root")));
        renameDialog.clickCancel();
        return;
      }
    }

    fail("Did not find a RenameHandler for the main java source root directory.");
  }
}
