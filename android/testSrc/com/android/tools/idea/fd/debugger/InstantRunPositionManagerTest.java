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
package com.android.tools.idea.fd.debugger;

import com.android.sdklib.AndroidVersion;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;

import static org.mockito.Mockito.mock;

public class InstantRunPositionManagerTest extends AndroidTestCase {
  private DebugProcess mockProcess;
  private InstantRunPositionManager myPositionManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mockProcess = mock(DebugProcessImpl.class);
    InstantRunPositionManagerFactory factory = new InstantRunPositionManagerFactory();
    myPositionManager = (InstantRunPositionManager)factory.createPositionManager(mockProcess);
  }

  public void testGetPsiByLocationWithNull() {
    assertNull(myPositionManager.getPsiFileByLocation(null, null));
  }

  public void testGetSourceForPsiFileWithNonAndroid() {
    Project project = getProject();

    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "class Foo {\n" +
                  "  \n" +
                  "  private void Bar() {\n" +
                  "    int test = 2;\n" +
                  "  }\n" +
                  "}";

    PsiFile file = myFixture.addFileToProject("src/p1/Foo.java", text);
    //Not an android sdk file so it returns null
    assertNull(myPositionManager.getApiSpecificPsi(project, file, new AndroidVersion(24, null)));
  }

  public void testGetSourceForPsiFileWithAndroidFile() {
    Project project = getProject();

    // need to add the sdk to JdkTable for getSourceFolder(version) to return the source folder
    ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().addJdk(createLatestAndroidSdk()));

    // get reference to the file through the class
    PsiClass cls = JavaPsiFacade.getInstance(project).findClass("android.view.View", GlobalSearchScope.allScope(project));
    PsiElement element = cls.getNavigationElement();
    PsiFile file = element.getContainingFile();

    // call the method and should get source
    checkSourceForApiVersion(project, file, 24);
    checkSourceForApiVersion(project, file, 25);
  }

  private void checkSourceForApiVersion(Project project, PsiFile file, int version) {
    PsiFile sourceFile = myPositionManager.getApiSpecificPsi(project, file, new AndroidVersion(version, null));
    assertNotNull(sourceFile);
    assertTrue(sourceFile.getVirtualFile().getPath().endsWith(String.format("sources/android-%0$d/android/view/View.java", version)));
  }
}