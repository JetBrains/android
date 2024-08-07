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
package com.google.idea.blaze.base.lang.projectview;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewPsiElement;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafElement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the project view file parser */
@RunWith(JUnit4.class)
public class ProjectViewParserIntegrationTest extends ProjectViewIntegrationTestCase {

  private final List<String> errors = Lists.newArrayList();

  @Before
  public final void before() {
    errors.clear();
  }

  @Test
  public void testStandardFile() {
    assertThat(
            parse(
                "directories:",
                "  java/com/google/work",
                "  java/com/google/other",
                "",
                "targets:",
                "  //java/com/google/work/...:all",
                "  //java/com/google/other/...:all"))
        .isEqualTo(
            Joiner.on("")
                .join(
                    "list_section(list_item, list_item), ", "list_section(list_item, list_item)"));
    assertNoErrors();
  }

  @Test
  public void testIncludeScalarSections() {
    assertThat(
            parse(
                "import java/com/google/work/.blazeproject",
                "",
                "workspace_type: intellij_plugin",
                "",
                "import_target_output:",
                "  //java/com/google/work:target",
                "",
                "test_sources:",
                "  java/com/google/common/*"))
        .isEqualTo(
            Joiner.on("")
                .join(
                    "scalar_section(scalar_item), ",
                    "scalar_section(scalar_item), ",
                    "list_section(list_item), ",
                    "list_section(list_item)"));
    assertNoErrors();
  }

  @Test
  public void testUnrecognizedKeyword() {
    parse("impart java/com/google/work/.blazeproject", "", "workspace_trype: intellij_plugin");

    assertContainsErrors("Unrecognized keyword: impart", "Unrecognized keyword: workspace_trype");
  }

  private String parse(String... lines) {
    PsiFile file = workspace.createPsiFile(new WorkspacePath(".blazeproject"), lines);
    collectErrors(file);
    return treeToString(file);
  }

  private String treeToString(PsiElement psi) {
    StringBuilder builder = new StringBuilder();
    nodeToString(psi, builder);
    return builder.toString();
  }

  private void nodeToString(PsiElement psi, StringBuilder builder) {
    if (psi.getNode() instanceof LeafElement) {
      return;
    }
    PsiElement[] children =
        Arrays.stream(psi.getChildren())
            .filter(t -> t instanceof ProjectViewPsiElement)
            .toArray(PsiElement[]::new);
    if (psi instanceof ProjectViewPsiElement) {
      builder.append(psi.getNode().getElementType());
      appendChildren(children, builder, true);
    } else {
      appendChildren(children, builder, false);
    }
  }

  private void appendChildren(PsiElement[] childPsis, StringBuilder builder, boolean bracket) {
    if (childPsis.length == 0) {
      return;
    }
    if (bracket) {
      builder.append("(");
    }
    nodeToString(childPsis[0], builder);
    for (int i = 1; i < childPsis.length; i++) {
      builder.append(", ");
      nodeToString(childPsis[i], builder);
    }
    if (bracket) {
      builder.append(")");
    }
  }

  private void assertNoErrors() {
    assertThat(errors).isEmpty();
  }

  private void assertContainsErrors(String... errors) {
    assertThat(this.errors).containsAllIn(Arrays.asList(errors));
  }

  private void collectErrors(PsiElement psi) {
    errors.addAll(
        PsiUtils.findAllChildrenOfClassRecursive(psi, PsiErrorElement.class)
            .stream()
            .map(PsiErrorElement::getErrorDescription)
            .collect(Collectors.toList()));
  }
}
