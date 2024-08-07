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
package com.google.idea.blaze.base.lang.buildfile.references;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiReference;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that package references in string literals are correctly resolved. */
@RunWith(JUnit4.class)
public class PackageReferenceTest extends BuildFileIntegrationTestCase {

  @Test
  public void testDirectReferenceResolves() {
    BuildFile buildFile1 =
        createBuildFile(new WorkspacePath("java/com/google/tools/BUILD"), "# contents");

    BuildFile buildFile2 =
        createBuildFile(
            new WorkspacePath("java/com/google/other/BUILD"),
            "package_group(name = \"grp\", packages = [\"//java/com/google/tools\"])");

    Argument.Keyword packagesArg =
        buildFile2
            .firstChildOfClass(FuncallExpression.class)
            .getArgList()
            .getKeywordArgument("packages");
    StringLiteral string =
        PsiUtils.findFirstChildOfClassRecursive(packagesArg, StringLiteral.class);
    assertThat(string.getReferencedElement()).isEqualTo(buildFile1);
  }

  @Test
  public void testLabelFragmentResolves() {
    BuildFile buildFile1 =
        createBuildFile(
            new WorkspacePath("java/com/google/tools/BUILD"), "java_library(name = \"lib\")");

    BuildFile buildFile2 =
        createBuildFile(
            new WorkspacePath("java/com/google/other/BUILD"),
            "java_library(name = \"lib2\", exports = [\"//java/com/google/tools:lib\"])");

    FuncallExpression libTarget = buildFile1.firstChildOfClass(FuncallExpression.class);
    assertThat(libTarget).isNotNull();

    Argument.Keyword packagesArg =
        buildFile2
            .firstChildOfClass(FuncallExpression.class)
            .getArgList()
            .getKeywordArgument("exports");
    StringLiteral string =
        PsiUtils.findFirstChildOfClassRecursive(packagesArg, StringLiteral.class);

    PsiReference[] references = string.getReferences();
    assertThat(Arrays.stream(references).map(PsiReference::resolve).collect(Collectors.toList()))
        .containsAllOf(libTarget, buildFile1);
  }
}
