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

import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
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
import org.fest.swing.timing.Condition;
import org.jetbrains.android.util.AndroidBundle;
import org.junit.Ignore;
import org.junit.Test;

import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.*;

@BelongsToTestGroups({PROJECT_SUPPORT})
@Ignore("Cause of IDE fatal errors")
public class RenameTest extends GuiTestCase {

  @Test @IdeGuiTest
  public void sourceRoot() throws Exception {
    myProjectFrame = importSimpleApplication();
    final Project project = myProjectFrame.getProject();
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
            final RenameDialogFixture renameDialog = RenameDialogFixture.startFor(directory, handler, myRobot);
            assertFalse(renameDialog.warningExists(null));
            renameDialog.setNewName(renameDialog.getNewName() + 1);
            // 'Rename dialog' show a warning asynchronously to the text change, that's why we wait here for the
            // warning to appear
            final Ref<Boolean> ok = new Ref<Boolean>();
            pause(new Condition("Wait until error text appears") {
              @Override
              public boolean test() {
                ok.set(renameDialog.warningExists(AndroidBundle.message("android.refactoring.gradle.warning.rename.source.root")));
                return ok.get();
              }
            }, SHORT_TIMEOUT);
            assertTrue(ok.get());
            return;
          }
        }
      }
    }
  }
}
