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
package com.google.idea.blaze.java.lang.build;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiJavaFile;
import com.intellij.refactoring.rename.RenameProcessor;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that BUILD file references are correctly updated when performing rename refactors. */
@RunWith(JUnit4.class)
public class JavaClassRenameTest extends BuildFileIntegrationTestCase {

  @Test
  public void testRenameJavaClass() {
    PsiJavaFile javaFile =
        (PsiJavaFile)
            workspace.createPsiFile(
                new WorkspacePath("com/google/foo/JavaClass.java"),
                "package com.google.foo;",
                "public class JavaClass {}");

    BuildFile buildFile =
        createBuildFile(
            new WorkspacePath("com/google/foo/BUILD"),
            "java_library(name = \"ref2\", srcs = [\"JavaClass.java\"])");

    new RenameProcessor(getProject(), javaFile.getClasses()[0], "NewName", false, false).run();

    assertFileContents(buildFile, "java_library(name = \"ref2\", srcs = [\"NewName.java\"])");
  }
}
