// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.android.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import org.jetbrains.android.AndroidTestCase;

public class AndroidSafeDeleteTest extends AndroidTestCase {
  private static final String TEST_FOLDER = "/createComponent/";

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  public void testDeleteComponent() {
    myFixture.copyFileToProject(TEST_FOLDER + "f1.xml", "AndroidManifest.xml");
    final VirtualFile activityFile = myFixture.copyFileToProject(TEST_FOLDER + "MyActivity.java", "src/p1/p2/MyActivity.java");
    myFixture.configureFromExistingVirtualFile(activityFile);
    final PsiFile psiActivityFile = PsiManager.getInstance(getProject()).findFile(activityFile);
    final PsiClass activityClass = ((PsiJavaFile)psiActivityFile).getClasses()[0];
    final DataContext context = DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent());

    try {
      SafeDeleteHandler.invoke(getProject(), new PsiElement[]{activityClass}, myModule, true, null);
      fail("class p1.p2.MyActivity is not safe to delete");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Class <b><code>p1.p2.MyActivity</code></b> has 1 usage that is not safe to delete.", e.getMessage());
    }
  }

  public void testDeleteResourceFile() throws Exception {
    createManifest();
    final String testName = getTestName(false);
    myFixture.copyFileToProject(TEST_FOLDER + testName + ".java", "src/p1/p2/" + testName + ".java");
    final VirtualFile resVFile = myFixture.copyFileToProject(TEST_FOLDER + testName + ".xml", "res/drawable/my_resource_file.xml");
    final PsiFile resFile = PsiManager.getInstance(getProject()).findFile(resVFile);
    try {
      SafeDeleteHandler.invoke(getProject(), new PsiElement[]{resFile}, myModule, true, null);
      fail("field drawable.my_resource_file is not safe to delete");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Field <b><code>drawable.my_resource_file</code></b> has 1 usage that is not safe to delete.", e.getMessage());
    }
  }
}
