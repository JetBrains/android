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
package com.android.tools.idea.actions;

import com.android.tools.idea.databinding.TestDataPaths;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.io.IOException;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public final class ConvertLayoutToDataBindingActionTest extends AndroidTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.setTestDataPath(TestDataPaths.TEST_DATA_ROOT + "/actions");
  }

  public void testLayout() throws IOException {
    String path = "res/layout/classic_layout.xml";
    VirtualFile file = myFixture.copyFileToProject("classic_layout.xml", path);
    doTest("classic_layout_after.xml", path, file);
  }

  private void doTest(@NotNull String after,
                      @NotNull String afterPath,
                      @NotNull VirtualFile resourceFile) {
    myFixture.configureFromExistingVirtualFile(resourceFile);
    final PsiFile xmlFile = myFixture.getFile();
    final ConvertLayoutToDataBindingAction action = new ConvertLayoutToDataBindingAction() {
      @Override
      protected boolean isUsingDataBinding(@NotNull Project project) {
        return true;
      }
    };

    assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), xmlFile));
    Project project = getProject();
    CommandProcessor processor = CommandProcessor.getInstance();
    processor.executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(()
           -> action.invoke(myFixture.getProject(), myFixture.getEditor(), xmlFile)), "", "");
    myFixture.checkResultByFile(afterPath, after, false);
  }
}
