/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.projectview.formatting;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.lang.projectview.ProjectViewIntegrationTestCase;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests rearranging of project view files */
@RunWith(JUnit4.class)
public class ProjectViewRearrangerTest extends ProjectViewIntegrationTestCase {

  private PsiFile setInput(String... fileContents) {
    return testFixture.configureByText(".blazeproject", Joiner.on("\n").join(fileContents));
  }

  private void assertResult(String... resultingFileContents) {
    testFixture.getFile().getText();
    testFixture.checkResult(Joiner.on("\n").join(resultingFileContents));
  }

  private void rearrangeCode() {
    rearrangeCode(ImmutableList.of(testFixture.getFile().getTextRange()));
  }

  private void rearrangeCode(Collection<TextRange> ranges) {
    ArrangementEngine engine = getProject().getService(ArrangementEngine.class);
    CommandProcessor.getInstance()
        .executeCommand(
            getProject(),
            () -> engine.arrange(testFixture.getEditor(), testFixture.getFile(), ranges),
            null,
            null);
  }

  @Test
  public void testSingleListSectionOrdered() {
    setInput("directories:", "  one", "  two", "  three", "  four");
    rearrangeCode();
    assertResult("directories:", "  four", "  one", "  three", "  two");
  }

  @Test
  public void testMultipleListSectionsOrdered() {
    setInput(
        "targets:",
        "  //z",
        "  //f",
        "  //a",
        "import some_file",
        "directories:",
        "  one",
        "  two",
        "  three",
        "  four");
    rearrangeCode();
    assertResult(
        "targets:",
        "  //a",
        "  //f",
        "  //z",
        "import some_file",
        "directories:",
        "  four",
        "  one",
        "  three",
        "  two");
  }

  @Test
  public void testRearrangeLimitedToSubsetOfSection() {
    setInput(
        "targets:",
        "  //z",
        "  //f",
        "  //a",
        "import some_file",
        "directories:",
        "  one",
        "  two",
        "  three",
        "  four");
    // range containing only '//f' and '//a' lines
    rearrangeCode(ImmutableList.of(TextRange.create(17, 25)));
    assertResult(
        "targets:",
        "  //z",
        "  //a",
        "  //f",
        "import some_file",
        "directories:",
        "  one",
        "  two",
        "  three",
        "  four");
  }
}
