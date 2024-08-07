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
package com.google.idea.blaze.base.run;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazePackageHeuristic}. */
@RunWith(JUnit4.class)
public class BlazePackageHeuristicTest extends BlazeIntegrationTestCase {

  @Test
  public void testPredicateMatchesSamePackage() {
    workspace.createFile(new WorkspacePath("foo/BUILD"));
    VirtualFile testSource = workspace.createFile(new WorkspacePath("foo/com/foo/test.sh"));
    TargetInfo target =
        TargetIdeInfo.builder().setLabel("//foo:test").setKind("sh_test").build().toTargetInfo();

    assertThat(
            new BlazePackageHeuristic()
                .matchesSource(getProject(), target, null, new File(testSource.getPath()), null))
        .isTrue();
  }

  @Test
  public void testPredicateDoesNotMatchDifferentPackage() {
    workspace.createFile(new WorkspacePath("foo/BUILD"));
    VirtualFile testSource = workspace.createFile(new WorkspacePath("foo/com/foo/test.sh"));
    TargetInfo target =
        TargetIdeInfo.builder().setLabel("//bar:test").setKind("sh_test").build().toTargetInfo();

    assertThat(
            new BlazePackageHeuristic()
                .matchesSource(getProject(), target, null, new File(testSource.getPath()), null))
        .isFalse();
  }

  @Test
  public void testChooseSourceFileFromMatchingPackage() {
    workspace.createFile(new WorkspacePath("foo/BUILD"));
    VirtualFile testSource = workspace.createFile(new WorkspacePath("foo/com/foo/test.sh"));
    Collection<TargetInfo> targets =
        ImmutableList.of(
            TargetIdeInfo.builder()
                .setLabel("//bar:test")
                .setKind("sh_test")
                .build()
                .toTargetInfo(),
            TargetIdeInfo.builder()
                .setLabel("//foo:test")
                .setKind("sh_test")
                .build()
                .toTargetInfo());

    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, new File(testSource.getPath()), targets, TestSize.SMALL);

    assertThat(match.label).isEqualTo(Label.create("//foo:test"));
  }
}
