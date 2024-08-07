/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.projectview;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.projectview.ImportRoots.ProjectDirectoriesHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TargetExpressionList} */
@RunWith(JUnit4.class)
public class TargetExpressionListTest extends BlazeTestCase {

  @Test
  public void testAllInPackageWildcardTargetsHandled() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.create(ImmutableList.of(TargetExpression.fromString("//foo:all")));

    assertThat(helper.includesTarget(Label.create("//foo:target"))).isTrue();
    assertThat(helper.includesTarget(Label.create("//bar:target"))).isFalse();

    assertThat(helper.includesPackage(new WorkspacePath("foo"))).isTrue();
    assertThat(helper.includesPackage(new WorkspacePath("foo/bar"))).isFalse();
    assertThat(helper.includesPackage(new WorkspacePath("bar"))).isFalse();
  }

  @Test
  public void testRecursiveWildcardTargetsHandled() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.create(ImmutableList.of(TargetExpression.fromString("//foo/...")));

    assertThat(helper.includesTarget(Label.create("//foo:target"))).isTrue();
    assertThat(helper.includesTarget(Label.create("//foo/bar/baz:target"))).isTrue();
    assertThat(helper.includesTarget(Label.create("//bar:target"))).isFalse();

    assertThat(helper.includesPackage(new WorkspacePath("foo"))).isTrue();
    assertThat(helper.includesPackage(new WorkspacePath("foo/bar"))).isTrue();
    assertThat(helper.includesPackage(new WorkspacePath("bar"))).isFalse();
  }

  @Test
  public void testSingleTargetHandled() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.create(ImmutableList.of(TargetExpression.fromString("//foo:target")));

    assertThat(helper.includesTarget(Label.create("//foo:target"))).isTrue();
    assertThat(helper.includesTarget(Label.create("//foo:other"))).isFalse();
    assertThat(helper.includesTarget(Label.create("//bar:target"))).isFalse();

    assertThat(helper.includesPackage(new WorkspacePath("foo"))).isFalse();
    assertThat(helper.includesPackage(new WorkspacePath("foo/bar"))).isFalse();
    assertThat(helper.includesPackage(new WorkspacePath("bar"))).isFalse();
  }

  @Test
  public void testLaterTargetsOverrideEarlierTargets() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.create(
            ImmutableList.of(
                TargetExpression.fromString("//foo:target"),
                TargetExpression.fromString("-//foo:target"),
                TargetExpression.fromString("-//bar:target"),
                TargetExpression.fromString("//bar:all")));

    assertThat(helper.includesTarget(Label.create("//foo:target"))).isFalse();
    assertThat(helper.includesTarget(Label.create("//bar:target"))).isTrue();

    assertThat(helper.includesPackage(new WorkspacePath("foo"))).isFalse();
    assertThat(helper.includesPackage(new WorkspacePath("bar"))).isTrue();
  }

  @Test
  public void includesAnyTargetInPackage_acceptsIndividualPackageTarget() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.create(ImmutableList.of(TargetExpression.fromString("//foo/bar:baz")));

    assertThat(helper.includesAnyTargetInPackage(new WorkspacePath("foo/bar"))).isTrue();
  }

  @Test
  public void includesAnyTargetInPackage_acceptsPackageWildcard() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.create(ImmutableList.of(TargetExpression.fromString("//foo/bar:all")));

    assertThat(helper.includesAnyTargetInPackage(new WorkspacePath("foo/bar"))).isTrue();
  }

  @Test
  public void includesAnyTargetInPackage_acceptsParentPackageRecursiveWildcard() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.create(ImmutableList.of(TargetExpression.fromString("//foo/...")));

    assertThat(helper.includesAnyTargetInPackage(new WorkspacePath("foo/bar"))).isTrue();
  }

  @Test
  public void includesAnyTargetInPackage_ignoresParentPackageNonRecursiveWildcard()
      throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.create(ImmutableList.of(TargetExpression.fromString("//foo:all")));

    assertThat(helper.includesAnyTargetInPackage(new WorkspacePath("foo/bar"))).isFalse();
  }

  @Test
  public void includesAnyTargetInPackage_ignoresTargetInOtherPackage() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.create(ImmutableList.of(TargetExpression.fromString("//foo:target")));

    assertThat(helper.includesAnyTargetInPackage(new WorkspacePath("bar"))).isFalse();
  }

  @Test
  public void includesAnyTargetInPackage_ignoresTargetInParentPackage() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.create(ImmutableList.of(TargetExpression.fromString("//foo:target")));

    assertThat(helper.includesAnyTargetInPackage(new WorkspacePath("foo/bar"))).isFalse();
  }

  @Test
  public void includesAnyTargetInPackage_ignoresExcludedPackage() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.create(
            ImmutableList.of(TargetExpression.fromString("-//foo/bar:all")));

    assertThat(helper.includesAnyTargetInPackage(new WorkspacePath("foo/bar"))).isFalse();
  }

  @Test
  public void includesAnyTargetInPackage_parentPackageRecursivelyExcluded() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.create(
            ImmutableList.of(
                TargetExpression.fromString("//foo/bar:all"),
                TargetExpression.fromString("//foo/bar:target"),
                TargetExpression.fromString("-//foo/...")));

    assertThat(helper.includesAnyTargetInPackage(new WorkspacePath("foo/bar"))).isFalse();
  }

  @Test
  public void includesAnyTargetInPackage_ignoresExcludedTargets() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.create(
            ImmutableList.of(
                TargetExpression.fromString("//foo:target"),
                TargetExpression.fromString("-//foo:target"),
                TargetExpression.fromString("//bar:other"),
                TargetExpression.fromString("-//bar:target")));

    assertThat(helper.includesAnyTargetInPackage(new WorkspacePath("foo"))).isFalse();
    assertThat(helper.includesAnyTargetInPackage(new WorkspacePath("bar"))).isTrue();
  }

  @Test
  public void testInferringTargetsFromDirectoriesSimple() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.createWithTargetsDerivedFromDirectories(
            ImmutableList.of(),
            new ProjectDirectoriesHelper(
                /* rootDirectories= */ ImmutableList.of(new WorkspacePath("foo")),
                /* excludeDirectories= */ ImmutableSet.of()));

    assertThat(helper.includesTarget(Label.create("//foo:target"))).isTrue();
    assertThat(helper.includesTarget(Label.create("//foo/bar:target"))).isTrue();
    assertThat(helper.includesTarget(Label.create("//bar:target"))).isFalse();

    assertThat(helper.includesPackage(new WorkspacePath("foo"))).isTrue();
    assertThat(helper.includesPackage(new WorkspacePath("bar"))).isFalse();
  }

  @Test
  public void testExplictTargetsOverrideInferredTargets() throws Exception {
    TargetExpressionList helper =
        TargetExpressionList.createWithTargetsDerivedFromDirectories(
            ImmutableList.of(
                TargetExpression.fromString("-//foo:target"),
                TargetExpression.fromString("//bar:target")),
            new ProjectDirectoriesHelper(
                /* rootDirectories= */ ImmutableList.of(new WorkspacePath("foo")),
                /* excludeDirectories= */ ImmutableSet.of(new WorkspacePath("bar"))));

    assertThat(helper.includesTarget(Label.create("//foo:target"))).isFalse();
    assertThat(helper.includesTarget(Label.create("//foo:other"))).isTrue();
    assertThat(helper.includesTarget(Label.create("//foo/bar:target"))).isTrue();
    assertThat(helper.includesTarget(Label.create("//bar:target"))).isTrue();
    assertThat(helper.includesTarget(Label.create("//bar:other"))).isFalse();

    assertThat(helper.includesPackage(new WorkspacePath("foo"))).isTrue();
    assertThat(helper.includesPackage(new WorkspacePath("bar"))).isFalse();
  }

  @Test
  public void testInferredTargetsWithExcludedSubdirectories() {
    TargetExpressionList helper =
        TargetExpressionList.createWithTargetsDerivedFromDirectories(
            ImmutableList.of(),
            new ProjectDirectoriesHelper(
                /* rootDirectories= */ ImmutableList.of(new WorkspacePath("foo")),
                /* excludeDirectories= */ ImmutableSet.of(new WorkspacePath("foo/bar"))));

    assertThat(helper.includesTarget(Label.create("//foo:target"))).isTrue();
    assertThat(helper.includesTarget(Label.create("//foo/bar:target"))).isFalse();
    assertThat(helper.includesTarget(Label.create("//foo/other:target"))).isTrue();

    assertThat(helper.includesPackage(new WorkspacePath("foo"))).isTrue();
    assertThat(helper.includesPackage(new WorkspacePath("foo/bar"))).isFalse();
    assertThat(helper.includesPackage(new WorkspacePath("foo/other"))).isTrue();
  }
}
