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
package com.google.idea.blaze.base.vcs.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link GitStatusLineProcessor} */
@RunWith(JUnit4.class)
public class GitStatusLineProcessorTest {
  @Rule public BlazeTestCase.IgnoreOnWindowsRule rule = new BlazeTestCase.IgnoreOnWindowsRule();

  @Test
  public void testGitStatusParser() {
    GitStatusLineProcessor lineProcessor =
        new GitStatusLineProcessor(new WorkspaceRoot(new File("/usr/blah")), "/usr/blah");
    for (String line :
        ImmutableList.of(
            "D    root/README",
            "M    root/blaze-base/src/com/google/idea/blaze/base/root/citc/CitcUtil.java",
            "A    root/blah",
            "A    java/com/google/Test.java",
            "M    java/com/other/")) {
      lineProcessor.processLine(line);
    }
    assertThat(lineProcessor.addedFiles)
        .containsExactly(
            new WorkspacePath("root/blah"), new WorkspacePath("java/com/google/Test.java"));
    assertThat(lineProcessor.modifiedFiles)
        .containsExactly(
            new WorkspacePath(
                "root/blaze-base/src/com/google/idea/blaze/base/root/citc/CitcUtil.java"),
            new WorkspacePath("java/com/other"));
    assertThat(lineProcessor.deletedFiles).containsExactly(new WorkspacePath("root/README"));
  }

  @Test
  public void testGitStatusParserDifferentRoots() {
    GitStatusLineProcessor lineProcessor =
        new GitStatusLineProcessor(new WorkspaceRoot(new File("/usr/blah/root")), "/usr/blah");
    for (String line :
        ImmutableList.of(
            "D    root/README",
            "M    root/blaze-base/src/com/google/idea/blaze/base/root/citc/CitcUtil.java",
            "A    root/blah",
            "A    java/com/google/Test.java",
            "M    java/com/other/")) {
      lineProcessor.processLine(line);
    }
    assertThat(lineProcessor.addedFiles).containsExactly(new WorkspacePath("blah"));
    assertThat(lineProcessor.modifiedFiles)
        .containsExactly(
            new WorkspacePath("blaze-base/src/com/google/idea/blaze/base/root/citc/CitcUtil.java"));
    assertThat(lineProcessor.deletedFiles).containsExactly(new WorkspacePath("README"));
  }
}
