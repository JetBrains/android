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
package com.android.tools.idea.apk.debugging;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;

import java.util.List;

import static com.android.tools.idea.apk.debugging.SimpleApplicationContents.getMyActivityApkClass;
import static com.android.tools.idea.apk.debugging.SimpleApplicationContents.getMyActivityFile;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JavaFiles}.
 */
public class JavaFilesTest extends AndroidGradleTestCase {
  private JavaFiles myJavaFiles;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    loadSimpleApplication();
    myJavaFiles = new JavaFiles();
  }

  public void testIsJavaFileWithJavaFile() {
    VirtualFile file = mock(VirtualFile.class);

    when(file.isDirectory()).thenReturn(false);
    when(file.getExtension()).thenReturn("java");
    assertTrue(JavaFiles.isJavaFile(file));
  }

  public void testIsJavaFileWithDirectory() {
    VirtualFile file = mock(VirtualFile.class);

    when(file.isDirectory()).thenReturn(true);
    assertFalse(JavaFiles.isJavaFile(file));
  }

  public void testIsJavaFileWithTextFile() {
    VirtualFile file = mock(VirtualFile.class);

    when(file.isDirectory()).thenReturn(false);
    when(file.getExtension()).thenReturn("text");
    assertFalse(JavaFiles.isJavaFile(file));
  }

  public void testIsJavaFileWithFileWithoutExtension() {
    VirtualFile file = mock(VirtualFile.class);

    when(file.isDirectory()).thenReturn(false);
    when(file.getExtension()).thenReturn("");
    assertFalse(JavaFiles.isJavaFile(file));
  }

  public void testFindClass() throws Exception {
    ApkClass myActivityClass = getMyActivityApkClass();
    String fqn = myActivityClass.getFqn();
    PsiClass found = myJavaFiles.findClass(fqn, getProject());
    assertNotNull(found);
    assertEquals(fqn, found.getQualifiedName());
  }

  public void testFindClasses() {
    Module appModule = myModules.getAppModule();
    VirtualFile myActivityFile = getMyActivityFile(appModule);

    List<String> found = myJavaFiles.findClasses(myActivityFile, getProject());
    assertNotNull(found);
    assertThat(found).containsExactly(getMyActivityApkClass().getFqn());
  }

  public void testFindPackage() {
    Module appModule = myModules.getAppModule();
    VirtualFile myActivityFile = getMyActivityFile(appModule);

    String found = myJavaFiles.findPackage(myActivityFile, getProject());
    assertNotNull(found);
    assertEquals(getMyActivityApkClass().getParent().getFqn(), found);
  }
}