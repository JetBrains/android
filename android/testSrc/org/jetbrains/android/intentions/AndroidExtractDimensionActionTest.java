/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.jetbrains.android.intentions;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.android.resources.ResourceType.DIMEN;

@SuppressWarnings("SpellCheckingInspection")
public class AndroidExtractDimensionActionTest extends AndroidTestCase {
  private static final String BASE_PATH = "extractDimen/";

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(getProject(), getProject());
  }

  public void testFromLayout() throws IOException {
    createManifest();
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "res/layout/layout.xml");
    doExtractAndCheckStringsXml("dimens.xml", null, true, "dimens_after.xml", file);
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  public void testFromLayout1() throws IOException {
    createManifest();
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "res/layout/layout.xml");
    myFixture.configureFromExistingVirtualFile(file);
    assertFalse(new AndroidExtractDimensionAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
  }

  public void testFromJava() {
    VirtualFile javaFile = myFixture.copyFileToProject(BASE_PATH + "fromJava.java", "src/p1/p2/Class.java");
    myFixture.configureFromExistingVirtualFile(javaFile);
    final PsiFile javaPsiFile = myFixture.getFile();
    assertFalse(new AndroidExtractDimensionAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), javaPsiFile));
  }

  private void doExtractAndCheckStringsXml(@NotNull String stringsXml,
                                           @Nullable final Runnable invokeAfterTemplate,
                                           final boolean closePopup,
                                           @NotNull String dimensAfter,
                                           @NotNull VirtualFile editedFile) {
    myFixture.copyFileToProject(BASE_PATH + stringsXml, "res/values/dimens.xml");
    myFixture.copyFileToProject("R.java", "src/p1/p2/R.java");
    myFixture.configureFromExistingVirtualFile(editedFile);
    final PsiFile editedPsiFile = myFixture.getFile();
    assertTrue(new AndroidExtractDimensionAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), editedPsiFile));
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            AndroidAddStringResourceAction.doInvoke(myFixture.getProject(), myFixture.getEditor(), editedPsiFile, "hello", DIMEN);
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
    myFixture.checkResultByFile("res/values/dimens.xml", BASE_PATH + dimensAfter, false);
  }
}
