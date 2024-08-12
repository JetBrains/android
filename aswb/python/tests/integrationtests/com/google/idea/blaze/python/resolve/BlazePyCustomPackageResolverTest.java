/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.python.resolve;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that directories without __init__.py files are still treated as python packages. */
@RunWith(JUnit4.class)
public class BlazePyCustomPackageResolverTest extends BlazeIntegrationTestCase {

  @Test
  public void testDirectoryWithInitPyFile() {
    // test that default behavior still works
    PsiDirectory directory = workspace.createPsiDirectory(WorkspacePath.createIfValid("dir"));
    PsiFile initPy =
        workspace.createPsiFile(WorkspacePath.createIfValid("dir/" + PyNames.INIT_DOT_PY));
    assertThat(PyUtil.isPackage(directory, null)).isTrue();
    assertThat(PyUtil.isPackage(initPy)).isTrue();
  }

  @Test
  public void testDirectoryWithoutInitPyFile() {
    PsiDirectory directory = workspace.createPsiDirectory(WorkspacePath.createIfValid("dir"));
    assertThat(PyUtil.isPackage(directory, null)).isTrue();
  }
}
