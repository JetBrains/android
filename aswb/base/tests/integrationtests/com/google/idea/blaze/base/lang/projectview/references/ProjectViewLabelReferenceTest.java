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
package com.google.idea.blaze.base.lang.projectview.references;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.projectview.ProjectViewIntegrationTestCase;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewPsiSectionItem;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that paths correctly resolve in project view files */
@RunWith(JUnit4.class)
public class ProjectViewLabelReferenceTest extends ProjectViewIntegrationTestCase {

  @Test
  public void testFileReference() {
    PsiFile referencedFile =
        workspace.createPsiFile(
            new WorkspacePath("ijwb.bazelproject"),
            "directories:",
            "  java/com/google/foo",
            "targets:",
            "  //java/com/google/foo/...");
    PsiFile file =
        workspace.createPsiFile(new WorkspacePath(".bazelproject"), "import ijwb.bazelproject");

    ProjectViewPsiSectionItem importItem =
        PsiUtils.findFirstChildOfClassRecursive(file, ProjectViewPsiSectionItem.class);
    assertThat(importItem).isNotNull();
    assertThat(importItem.getReference().resolve()).isEqualTo(referencedFile);
  }

  @Test
  public void testDirectoryReference() {
    PsiDirectory directory = workspace.createPsiDirectory(new WorkspacePath("foo/bar"));
    PsiFile projectView =
        workspace.createPsiFile(new WorkspacePath(".bazelproject"), "directories:", "  foo/bar");

    ProjectViewPsiSectionItem importItem =
        PsiUtils.findFirstChildOfClassRecursive(projectView, ProjectViewPsiSectionItem.class);
    assertThat(importItem).isNotNull();
    assertThat(importItem.getReference().resolve()).isEqualTo(directory);
  }

  @Test
  public void testTargetReference() {
    PsiFile buildFile =
        workspace.createPsiFile(
            new WorkspacePath("foo/bar/BUILD"), "java_library(", "    name = 'lib',", ")");
    PsiFile projectView =
        workspace.createPsiFile(new WorkspacePath(".bazelproject"), "targets:", "  //foo/bar:lib");

    FuncallExpression target = ((BuildFile) buildFile).findRule("lib");

    ProjectViewPsiSectionItem importItem =
        PsiUtils.findFirstChildOfClassRecursive(projectView, ProjectViewPsiSectionItem.class);
    assertThat(importItem).isNotNull();
    assertThat(importItem.getReference().resolve()).isEqualTo(target);
  }
}
