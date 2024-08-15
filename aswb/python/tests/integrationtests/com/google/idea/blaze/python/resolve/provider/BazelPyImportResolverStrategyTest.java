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

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test behavior of {@link BazelPyImportResolverStrategy}. */
@RunWith(JUnit4.class)
public class BazelPyImportResolverStrategyTest extends PyImportResolverStrategyTestCase {

  @Override
  protected BuildSystemName buildSystem() {
    return BuildSystemName.Bazel;
  }

  @Test
  public void testResolveWorkspaceImport() {
    PsiFile bar = workspace.createPsiFile(WorkspacePath.createIfValid("foo/bar.py"));
    PsiFile source =
        workspace.createPsiFile(
            WorkspacePath.createIfValid("lib/source.py"), "from foo import bar");
    List<PyFromImportStatement> imports = ((PyFile) source).getFromImports();
    assertThat(imports).hasSize(1);
    assertThat(imports.get(0).getImportElements()[0].resolve()).isEqualTo(bar);
  }

  @Test
  public void testResolveInitPy() {
    PsiFile initPy =
        workspace.createPsiFile(WorkspacePath.createIfValid("pyglib/flags/__init__.py"));
    PsiFile source =
        workspace.createPsiFile(
            WorkspacePath.createIfValid("lib/source.py"), "from pyglib import flags");
    List<PyFromImportStatement> imports = ((PyFile) source).getFromImports();
    assertThat(imports).hasSize(1);
    assertThat(imports.get(0).getImportElements()[0].resolve()).isEqualTo(initPy);
  }

  @Test
  public void testImportQuickFix() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//foo:foo")
                    .setBuildFile(source("foo/BUILD"))
                    .setKind("py_library")
                    .addSource(source("foo/lib/bar.py")))
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    workspace.createPsiFile(WorkspacePath.createIfValid("foo/lib/bar.py"));
    PsiFile source = workspace.createPsiFile(WorkspacePath.createIfValid("baz/source.py"), "bar");

    PyReferenceExpression ref =
        PsiUtils.findFirstChildOfClassRecursive(source, PyReferenceExpression.class);
    assertThat(ref).isNotNull();

    AutoImportQuickFix quickFix = getImportQuickFix(ref);
    assertThat(quickFix.isAvailable()).isTrue();
    assertThat(quickFix.getText()).isEqualTo("Import 'foo.lib.bar'");
    quickFix.applyFix();

    assertThat(source.getText())
        .isEqualTo(Joiner.on('\n').join("from foo.lib import bar", "", "bar"));
  }

  private static ArtifactLocation source(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
