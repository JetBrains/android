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

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;

import java.util.List;

import static com.android.tools.idea.navigator.nodes.apk.java.SimpleApplicationContents.getMyActivityApkClass;
import static com.android.tools.idea.navigator.nodes.apk.java.SimpleApplicationContents.getMyActivityFile;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link ClassFinder}.
 */
public class ClassFinderTest extends AndroidGradleTestCase {
  private ClassFinder myClassFinder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    loadSimpleApplication();
    myClassFinder = new ClassFinder(getProject());
  }

  public void testFindClass() throws Exception {
    ApkClass myActivityClass = getMyActivityApkClass();
    PsiClass found = myClassFinder.findClass(myActivityClass);
    assertNotNull(found);
    assertEquals(myActivityClass.getFqn(), found.getQualifiedName());
  }

  public void testFindClasses() {
    Module appModule = myModules.getAppModule();
    VirtualFile myActivityFile = getMyActivityFile(appModule);

    List<String> found = myClassFinder.findClasses(myActivityFile);
    assertNotNull(found);
    assertThat(found).containsExactly(getMyActivityApkClass().getFqn());
  }

  public void testFindPackage() {
    Module appModule = myModules.getAppModule();
    VirtualFile myActivityFile = getMyActivityFile(appModule);

    String found = myClassFinder.findPackage(myActivityFile);
    assertNotNull(found);
    assertEquals(getMyActivityApkClass().getParent().getFqn(), found);
  }
}