/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.language;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that BUILD files are recognized as such */
@RunWith(JUnit4.class)
public class BuildFileTypeTest extends BuildFileIntegrationTestCase {

  @Test
  public void testSkylarkExtensionRecognized() {
    PsiFile file = workspace.createPsiFile(new WorkspacePath("java/com/google/foo/build_defs.bzl"));
    assertThat(file).isInstanceOf(BuildFile.class);
  }

  @Test
  public void testExactNameMatch() {
    PsiFile file = workspace.createPsiFile(new WorkspacePath("java/com/google/foo/BUILD"));
    assertThat(file).isInstanceOf(BuildFile.class);
  }

}
