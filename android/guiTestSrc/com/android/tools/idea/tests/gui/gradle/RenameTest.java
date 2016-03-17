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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.RenameDialogFixture;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.rename.DirectoryAsPackageRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.android.util.AndroidBundle;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.*;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class RenameTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Ignore("Left modal dialog showing with title 'Rename'")
  @Test
  public void sourceRoot() throws Exception {
    guiTest.importSimpleApplication();
    final Project project = guiTest.ideFrame().getProject();
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
      for (final VirtualFile sourceRoot : sourceRoots) {
        PsiDirectory directory = execute(new GuiQuery<PsiDirectory>() {
          @Override
          protected PsiDirectory executeInEDT() throws Throwable {
            return PsiManager.getInstance(project).findDirectory(sourceRoot);
          }
        });
        assertNotNull(directory);
        for (final RenameHandler handler : Extensions.getExtensions(RenameHandler.EP_NAME)) {
          if (handler instanceof DirectoryAsPackageRenameHandler) {
            final RenameDialogFixture renameDialog = RenameDialogFixture.startFor(directory, handler, guiTest.robot());
            assertFalse(renameDialog.warningExists(null));
            renameDialog.setNewName(renameDialog.getNewName() + 1);
            // 'Rename dialog' show a warning asynchronously to the text change, that's why we wait here for the
            // warning to appear
            final Ref<Boolean> ok = new Ref<Boolean>();
            Wait.minutes(2).expecting("error text to appear").until(new Wait.Objective() {
              @Override
              public boolean isMet() {
                ok.set(renameDialog.warningExists(AndroidBundle.message("android.refactoring.gradle.warning.rename.source.root")));
                return ok.get();
              }
            });
            assertTrue(ok.get());
            return;
          }
        }
      }
    }
  }
}
