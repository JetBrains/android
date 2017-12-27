/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static com.android.SdkConstants.DOT_XML;

public class ConvertLayoutToDataBindingActionTest extends AndroidTestCase {
  private static final String BASE_PATH = "convertToDataBinding/";

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testLayout() throws IOException {
    String path = "res/layout/layout.xml";
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + DOT_XML, path);
    doTest("layout_after.xml", path, file);
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
    myFixture.checkResultByFile(afterPath, BASE_PATH + after, false);
  }
}