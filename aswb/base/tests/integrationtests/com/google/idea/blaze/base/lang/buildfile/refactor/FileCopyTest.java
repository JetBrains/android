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
package com.google.idea.blaze.base.lang.buildfile.refactor;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.copy.CopyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests copying files */
@RunWith(JUnit4.class)
public class FileCopyTest extends BuildFileIntegrationTestCase {

  @Test
  public void testCopyingJavaFileReferencedByGlob() {
    workspace.createDirectory(new WorkspacePath("java"));
    PsiFile javaFile =
        workspace.createPsiFile(
            new WorkspacePath("java/Test.java"), "package java;", "public class Test {}");

    PsiFile javaFile2 =
        workspace.createPsiFile(
            new WorkspacePath("java/Test2.java"), "package java;", "public class Test2 {}");

    createBuildFile(
        new WorkspacePath("java/BUILD"),
        "java_library(",
        "    name = 'lib',",
        "    srcs = glob(['**/*.java']),",
        ")");

    PsiDirectory otherDir = workspace.createPsiDirectory(new WorkspacePath("java/other"));

    WriteCommandAction.runWriteCommandAction(
        null,
        () -> {
          CopyHandler.doCopy(new PsiElement[] {javaFile, javaFile2}, otherDir);
        });
  }
}
