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
package com.google.idea.blaze.base.lang.buildfile.highlighting;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.ReferenceExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link HighlightingAnnotator}. */
@RunWith(JUnit4.class)
public class HighlightingAnnotatorTest extends BuildFileIntegrationTestCase {

  @Test
  public void testBuiltInNamesSimple() throws Throwable {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "None");

    ReferenceExpression ref = file.findChildByClass(ReferenceExpression.class);
    assertThat(ref.getText()).isEqualTo("None");

    editorTest.openFileInEditor(file.getVirtualFile());
    List<HighlightInfo> result = testFixture.doHighlighting();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).startOffset).isEqualTo(ref.getNode().getStartOffset());
    assertThat(result.get(0).forcedTextAttributesKey)
        .isEqualTo(BuildSyntaxHighlighter.BUILD_BUILTIN_NAME);
  }

  @Test
  public void testBuiltInNames() throws Throwable {
    BuildFile file =
        createBuildFile(new WorkspacePath("BUILD"), "a = True", "b = False", "type(a)");

    List<ReferenceExpression> refs =
        PsiUtils.findAllChildrenOfClassRecursive(file, ReferenceExpression.class);
    assertThat(refs).hasSize(4);
    assertThat(refs.get(0).getText()).isEqualTo("True");
    assertThat(refs.get(1).getText()).isEqualTo("False");
    assertThat(refs.get(2).getText()).isEqualTo("type");

    editorTest.openFileInEditor(file.getVirtualFile());
    List<HighlightInfo> result = testFixture.doHighlighting();
    assertThat(result).hasSize(3);
    for (int i = 0; i < result.size(); i++) {
      assertThat(result.get(i).startOffset).isEqualTo(refs.get(i).getNode().getStartOffset());
      assertThat(result.get(i).forcedTextAttributesKey)
          .isEqualTo(BuildSyntaxHighlighter.BUILD_BUILTIN_NAME);
    }
  }
}
