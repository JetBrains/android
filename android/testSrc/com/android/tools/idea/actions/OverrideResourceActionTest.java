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
package com.android.tools.idea.actions;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class OverrideResourceActionTest extends AndroidTestCase {
  private static final String BASE_PATH = "forkResource/";

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(getProject(), getProject());
  }

  public void testLayout() throws IOException {
    createManifest();
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "res/layout/layout.xml");
    doTest("layout-sw600dp", null, "layout_after.xml", "res/layout-sw600dp/layout.xml", file, true);
  }

  public void testStrings() throws IOException {
    createManifest();
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "res/values/myStrings.xml");
    doTest("values-en", null, "strings_after.xml", "res/values-en/myStrings.xml", file, true);
  }

  public void testStyles() throws IOException {
    // Ensures that we copy child content
    // This tests creating a new file
    createManifest();
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "res/values/styles.xml");
    doTest("values-v21", null, "styles_after.xml", "res/values-v21/styles.xml", file, true);
  }

  public void testStyles2() throws IOException {
    // This tests inserting into an existing overridden folder
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "styles2v21" + ".xml", "res/values-v21/styles.xml");
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "res/values/styles.xml");
    doTest("values-v21", null, "styles2_after.xml", "res/values-v21/styles.xml", file, true);
  }

  private void doTest(@NotNull String newFolder,
                      @Nullable final Runnable invokeAfterTemplate,
                      @NotNull String after,
                      String afterPath,
                      @NotNull VirtualFile resourceFile,
                      final boolean closePopup) {
    myFixture.configureFromExistingVirtualFile(resourceFile);
    final PsiFile xmlFile = myFixture.getFile();
    final OverrideResourceAction action = new OverrideResourceAction();
    OverrideResourceAction.ourTargetFolderName = newFolder;
    assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), xmlFile));
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            action.invoke(myFixture.getProject(), myFixture.getEditor(), xmlFile);
            if (invokeAfterTemplate != null) {
              invokeAfterTemplate.run();
            }
          }
        });
        if (closePopup) {
          myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
        }
      }
    }, "", "");
    UIUtil.dispatchAllInvocationEvents();
    myFixture.checkResultByFile(afterPath, BASE_PATH + after, false);
  }
}
