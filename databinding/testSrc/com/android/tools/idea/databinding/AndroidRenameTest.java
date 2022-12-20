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
package com.android.tools.idea.databinding;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.move.moveFilesOrDirectories.JavaMoveFilesOrDirectoriesHandler;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AndroidRenameTest {
  @NotNull
  @Rule
  public final AndroidProjectRule myProject = AndroidProjectRule.withSdk().initAndroid(true);

  @NotNull
  @Rule
  public final EdtRule myEdtRule = new EdtRule();

  @Before
  public void setUp() {
    JavaCodeInsightTestFixture fixture = getFixture();

    fixture.setTestDataPath(TestDataPaths.TEST_DATA_ROOT + "/databinding");
    fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  @Test
  @RunsInEdt
  public void testMoveDataBindingClass() throws Exception {
    getFixture().copyFileToProject("src/p1/p2/SampleClass.java", "src/p1/p2/SampleClass.java");
    getFixture().copyFileToProject("res/layout/basic_binding.xml", "res/layout/basic_binding.xml");
    moveClassNoTextReferences("p1.p2.SampleClass", "p1");
    getFixture().checkResultByFile("res/layout/basic_binding.xml", "res/layout/basic_binding_after.xml", true);
  }

  /**
   * Where as {@link #moveClass(String, String)} will move a class and update all references, including text references
   * This method will ONLY update code references when moving a class
   *
   * @see #moveClass(String, String)
   */
  private void moveClassNoTextReferences(String className, String newPackageName) {
    JavaPsiFacadeEx myJavaFacade = JavaPsiFacadeEx.getInstanceEx(myProject.getProject());
    final PsiElement element = myJavaFacade.findClass(className, GlobalSearchScope.projectScope(myProject.getProject()));
    assertThat(element).isNotNull();

    PsiManagerImpl myPsiManager = (PsiManagerImpl)PsiManager.getInstance(myProject.getProject());
    PsiPackage aPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(newPackageName);
    assertThat(aPackage).isNotNull();
    assertThat(aPackage).isNotNull();
    final PsiDirectory[] dirs = aPackage.getDirectories();
    assertThat(dirs.length).isEqualTo(1);

    final JavaMoveFilesOrDirectoriesHandler handler = new JavaMoveFilesOrDirectoriesHandler();
    PsiElement[] elements = new PsiElement[]{element};
    assertThat(handler.canMove(elements, dirs[0])).isTrue();
    handler.doMove(myProject.getProject(), elements, dirs[0], null);
    PsiDocumentManager.getInstance(myProject.getProject()).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  /**
   * Expose the underlying project rule fixture directly.
   * <p>
   * We know that the underlying fixture is a {@link JavaCodeInsightTestFixture} because our
   * {@link AndroidProjectRule} is initialized to use the disk.
   * <p>
   * In some cases, using the specific subclass provides us with additional methods we can
   * use to inspect the state of our parsed files. In other cases, it's just fewer characters
   * to type.
   */
  private JavaCodeInsightTestFixture getFixture() {
    return ((JavaCodeInsightTestFixture)myProject.getFixture());
  }
}