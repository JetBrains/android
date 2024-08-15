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
package com.google.idea.blaze.java.lang.build.references;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.ArgumentList;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link JavaClassQualifiedNameReference}. */
@RunWith(JUnit4.class)
public class JavaClassQualifiedNameReferenceTest extends BuildFileIntegrationTestCase {

  @Test
  public void testReferencesJavaClass() {
    PsiFile javaFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/bin/Main.java"),
            "package com.google.bin;",
            "public class Main {",
            "  public void main() {}",
            "}");
    PsiClass javaClass = ((PsiClassOwner) javaFile).getClasses()[0];

    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_binary(",
            "    name = 'binary',",
            "    main_class = 'com.google.bin.Main',",
            ")");

    ArgumentList args = file.firstChildOfClass(FuncallExpression.class).getArgList();
    assertThat(args.getKeywordArgument("main_class").getValue().getReferencedElement())
        .isEqualTo(javaClass);
  }
}
