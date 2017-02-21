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
package com.android.tools.idea.navigator.nodes.apk.java;

import com.android.tools.idea.apk.debugging.ApkClass;
import com.android.tools.idea.apk.debugging.JavaFiles;
import com.android.tools.idea.apk.debugging.SmaliFiles;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.apk.debugging.SimpleApplicationContents.*;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ClassNode}.
 */
public class ClassNodeTest extends AndroidGradleTestCase {
  @Mock private ViewSettings mySettings;
  @Mock private SmaliFiles mySmaliFiles;

  private ApkClass myActivityApkClass;
  private ApkClass myUnitTestClass;
  private JavaFiles myJavaFiles;

  private ClassNode myNode;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();

    myActivityApkClass = getMyActivityApkClass();
    myUnitTestClass = getUnitTestClass();

    myJavaFiles = new JavaFiles();

    myNode = new ClassNode(project, myActivityApkClass, mySettings, myJavaFiles, mySmaliFiles);
  }

  public void testUpdate() {
    PresentationData presentation = new PresentationData("", "", null, null);
    myNode.update(presentation);
    assertEquals(myActivityApkClass.getName(), presentation.getPresentableText());
  }

  public void testContainsJavaFile() throws Exception {
    loadSimpleApplication();
    Module module = myModules.getAppModule();

    VirtualFile activityFile = getMyActivityFile(module);
    assertTrue(myNode.contains(activityFile));

    VirtualFile testFile = getUnitTestFile(module);
    assertFalse(myNode.contains(testFile));
  }

  public void testContainsSmaliFile() throws Throwable {
    Project project = getProject();
    VirtualFile smaliFile = ApplicationManager.getApplication().runWriteAction((ThrowableComputable<VirtualFile, IOException>)() ->
      project.getBaseDir().createChildData(this, "Test.smali"));
    File smaliFilePath = virtualToIoFile(smaliFile);

    when(mySmaliFiles.findSmaliFilePath(myActivityApkClass.getFqn())).thenReturn(smaliFilePath);
    assertTrue(myNode.contains(smaliFile));

    when(mySmaliFiles.findSmaliFilePath(myActivityApkClass.getFqn())).thenReturn(getBaseDirPath(project));
    assertFalse(myNode.contains(smaliFile));
  }

  public void testCanRepresentVirtualFile() throws Exception {
    loadSimpleApplication();
    Module module = myModules.getAppModule();

    VirtualFile activityFile = getMyActivityFile(module);
    assertTrue(myNode.canRepresent(activityFile));

    VirtualFile testFile = getUnitTestFile(module);
    assertFalse(myNode.canRepresent(testFile));
  }

  public void testCanRepresentPsiClass() throws Exception {
    loadSimpleApplication();

    PsiClass myActivityClass = getPsiClass(myActivityApkClass);
    assertTrue(myNode.canRepresent(myActivityClass));

    PsiClass unitTestClass = getPsiClass(myUnitTestClass);
    assertFalse(myNode.canRepresent(unitTestClass));
  }

  public void testCanRepresentPsiMethod() throws Exception {
    loadSimpleApplication();

    PsiClass myActivityClass = getPsiClass(myActivityApkClass);
    assertTrue(myNode.canRepresent(myActivityClass.getMethods()[0]));

    PsiClass unitTestClass = getPsiClass(myUnitTestClass);
    assertFalse(myNode.canRepresent(unitTestClass.getMethods()[0]));
  }

  @NotNull
  private PsiClass getPsiClass(@NotNull ApkClass apkClass) {
    PsiClass psiClass = myJavaFiles.findClass(apkClass.getFqn(), getProject());
    assertNotNull(psiClass);
    return psiClass;
  }
}