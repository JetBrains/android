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
package com.google.idea.blaze.python.resolve.provider;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import java.io.File;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test behavior of {@link BazelPyGenfilesImportResolverStrategy}. */
@RunWith(JUnit4.class)
public class BazelPyGenfilesImportResolverStrategyTest extends PyImportResolverStrategyTestCase {

  @Override
  protected BuildSystemName buildSystem() {
    return BuildSystemName.Bazel;
  }

  @Test
  public void testResolveGenfiles() {
    BlazeInfo roots =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData().getBlazeInfo();
    PsiFile genfile =
        fileSystem.createPsiFile(new File(roots.getGenfilesDirectory(), "foo/bar.py").getPath());
    PsiFile source =
        workspace.createPsiFile(
            WorkspacePath.createIfValid("lib/source.py"), "from foo import bar");
    List<PyFromImportStatement> imports = ((PyFile) source).getFromImports();
    assertThat(imports).hasSize(1);
    assertThat(imports.get(0).getImportElements()[0].resolve()).isEqualTo(genfile);
  }
}
