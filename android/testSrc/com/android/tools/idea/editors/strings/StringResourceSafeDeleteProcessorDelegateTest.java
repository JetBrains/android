/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public final class StringResourceSafeDeleteProcessorDelegateTest extends AndroidTestCase {
  private static final String PATH = "stringsEditor/safeDeleteResource/";
  private static final String RESOURCE = "<string name=\"app_name\">safeDeleteResource</string>";

  public void test() {
    Project project = getProject();

    VirtualFile androidManifestXml = myFixture.copyFileToProject(PATH + "AndroidManifest.xml", "AndroidManifest.xml");
    VirtualFile mainActivityJava = myFixture.copyFileToProject(PATH + "MainActivity.java", "src/p1/p2/MainActivity.java");
    myFixture.copyFileToProject(PATH + "R.java", "gen/p1/p2/R.java");
    VirtualFile stringsXml = myFixture.copyFileToProject(PATH + "strings.xml", "res/values/strings.xml");

    Collection<PsiElement> resourceCollection = collectElements(stringsXml, RESOURCE);
    PsiElement[] resourceArray = resourceCollection.toArray(new PsiElement[0]);

    try {
      SafeDeleteHandler.invoke(project, resourceArray, true);
      fail();
    }
    catch (ConflictsInTestsException exception) {
      Object expected = ImmutableSet.of(
        "attribute value app_name has 1 usage that is not safe to delete.",
        "field string.app_name has 1 usage that is not safe to delete.");

      assertEquals(expected, ImmutableSet.copyOf(exception.getMessages()));
    }

    assertEquals(resourceCollection, collectElements(stringsXml, RESOURCE));

    deleteElements(androidManifestXml, "@string/app_name");
    deleteElements(mainActivityJava, "R.string.app_name");

    SafeDeleteHandler.invoke(project, resourceArray, true);
    assertTrue(collectElements(stringsXml, RESOURCE).isEmpty());
  }

  private void deleteElements(@NotNull VirtualFile virtualFile, @NotNull String text) {
    WriteCommandAction.runWriteCommandAction(getProject(), () -> collectElements(virtualFile, text).forEach(PsiElement::delete));
  }

  @NotNull
  private Collection<PsiElement> collectElements(@NotNull VirtualFile virtualFile, @NotNull String text) {
    PsiElement psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    return Arrays.asList(PsiTreeUtil.collectElements(psiFile, element -> element.getText().equals(text)));
  }
}
