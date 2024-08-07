/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync;

import static com.google.idea.blaze.qsync.QuerySyncTestUtils.NOOP_CONTEXT;
import static com.google.idea.blaze.qsync.query.QuerySummaryTestUtil.createProtoForPackages;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Expect;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange.Operation;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.query.QuerySummaryTestBuilder;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AffectedPackagesTest {

  @Rule public final Expect expect = Expect.create();

  @Test
  public void testModifyBuildFile() {
    QuerySummary query =
        QuerySummary.create(
            createProtoForPackages("//my/build/package1:rule", "//my/build/package2:rule"));

    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.MODIFY, Path.of("my/build/package1/BUILD"))))
            .build()
            .getAffectedPackages();

    expect.that(affected.isEmpty()).isFalse();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).containsExactly(Path.of("my/build/package1"));
    expect.that(affected.getDeletedPackages()).isEmpty();
  }

  @Test
  public void testAddBuildFile_siblingPackage() {
    QuerySummary query = QuerySummary.create(createProtoForPackages("//my/build/package1:rule"));

    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.ADD, Path.of("my/build/package2/BUILD"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isEmpty()).isFalse();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).containsExactly(Path.of("my/build/package2"));
    expect.that(affected.getDeletedPackages()).isEmpty();
  }

  @Test
  public void testAddBuildFile_childPackage() {
    QuerySummary query = QuerySummary.create(createProtoForPackages("//my/build/package1:rule"));

    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.ADD, Path.of("my/build/package1/subpackage/BUILD"))))
            .build()
            .getAffectedPackages();

    expect.that(affected.isEmpty()).isFalse();
    expect.that(affected.isIncomplete()).isFalse();
    expect
        .that(affected.getModifiedPackages())
        .containsExactly(Path.of("my/build/package1"), Path.of("my/build/package1/subpackage"));
    expect.that(affected.getDeletedPackages()).isEmpty();
  }

  @Test
  public void testAddBuildFile_andDeleteParent() {
    QuerySummary query =
        QuerySummary.create(
            createProtoForPackages(
                "//my/build/package1:rule1", "//my/build/package1/subpackage:subrule"));

    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.DELETE, Path.of("my/build/package1/subpackage/BUILD")),
                    new WorkspaceFileChange(
                        Operation.ADD, Path.of("my/build/package1/subpackage/nested/BUILD"))))
            .build()
            .getAffectedPackages();

    expect.that(affected.isEmpty()).isFalse();
    expect.that(affected.isIncomplete()).isFalse();
    expect
        .that(affected.getModifiedPackages())
        .containsExactly(
            Path.of("my/build/package1"), Path.of("my/build/package1/subpackage/nested"));
    expect
        .that(affected.getDeletedPackages())
        .containsExactly(Path.of("my/build/package1/subpackage"));
  }

  @Test
  public void testDeleteBuildFile_siblingPackage() {
    QuerySummary query =
        QuerySummary.create(
            createProtoForPackages("//my/build/package1:rule1", "//my/build/package2:rule2"));

    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.DELETE, Path.of("my/build/package2/BUILD"))))
            .build()
            .getAffectedPackages();

    expect.that(affected.isEmpty()).isFalse();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).isEmpty();
    expect.that(affected.getDeletedPackages()).containsExactly(Path.of("my/build/package2"));
  }

  @Test
  public void testDeleteBuildFile_childPackage() {
    QuerySummary query =
        QuerySummary.create(
            createProtoForPackages(
                "//my/build/package1:rule1", "//my/build/package1/subpackage:subrule"));

    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.DELETE, Path.of("my/build/package1/subpackage/BUILD"))))
            .build()
            .getAffectedPackages();

    expect.that(affected.isEmpty()).isFalse();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).containsExactly(Path.of("my/build/package1"));
    expect
        .that(affected.getDeletedPackages())
        .containsExactly(Path.of("my/build/package1/subpackage"));
  }

  @Test
  public void testDeleteBuildFile_childPackage_nested() {
    QuerySummary query =
        QuerySummary.create(
            createProtoForPackages(
                "//my/build/package1:rule1",
                "//my/build/package1/subpackage:subrule",
                "//my/build/package1/subpackage/nested:nestedrule"));

    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.DELETE, Path.of("my/build/package1/subpackage/BUILD")),
                    new WorkspaceFileChange(
                        Operation.DELETE, Path.of("my/build/package1/subpackage/nested/BUILD"))))
            .build()
            .getAffectedPackages();

    expect.that(affected.isEmpty()).isFalse();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).containsExactly(Path.of("my/build/package1"));
    expect
        .that(affected.getDeletedPackages())
        .containsExactly(
            Path.of("my/build/package1/subpackage"),
            Path.of("my/build/package1/subpackage/nested"));
  }

  @Test
  public void testModifyBuildFile_outsideProject() {
    QuerySummary query = QuerySummary.create(createProtoForPackages("//my/build/package1:rule"));

    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build/package1")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.MODIFY, Path.of("my/build/package2/BUILD"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isEmpty()).isTrue();
    expect.that(affected.isIncomplete()).isTrue();
    expect.that(affected.getModifiedPackages()).isEmpty();
    expect.that(affected.getDeletedPackages()).isEmpty();
  }

  @Test
  public void testModifyBuildFile_excluded() {
    QuerySummary query = QuerySummary.create(createProtoForPackages("//my/build/package1:rule"));

    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .projectExcludes(ImmutableSet.of(Path.of("my/build/package2")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.MODIFY, Path.of("my/build/package2/BUILD"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isEmpty()).isTrue();
    expect.that(affected.isIncomplete()).isTrue();
    expect.that(affected.getModifiedPackages()).isEmpty();
    expect.that(affected.getDeletedPackages()).isEmpty();
  }

  @Test
  public void testModifyBzlFile_included() {
    QuerySummary summary =
        QuerySummary.create(
            new QuerySummaryTestBuilder()
                .addPackages("//my/build/package1:rule", "//my/build/package2:rule")
                .addSubincludes(
                    ImmutableMultimap.<String, String>builder()
                        .put("//my/build/package1:BUILD", "//my/build/package1:macro.bzl")
                        .put("//my/build/package2:BUILD", "//my/build/package1:macro.bzl")
                        .build())
                .build());

    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(summary)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.MODIFY, Path.of("my/build/package1/macro.bzl"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isIncomplete()).isFalse();
    expect
        .that(affected.getModifiedPackages())
        .containsExactly(Path.of("my/build/package1"), Path.of("my/build/package2"));
  }

  @Test
  public void testModifyBzlFile_excluded() {
    QuerySummary summary =
        QuerySummary.create(
            new QuerySummaryTestBuilder()
                .addPackages("//my/build/package:rule")
                .addSubincludes(
                    ImmutableMultimap.of(
                        "//my/build/package:BUILD", "//other/build/package1:macro.bzl"))
                .build());

    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(summary)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.MODIFY, Path.of("other/build/package1/macro.bzl"))))
            .build()
            .getAffectedPackages();
    // we edited a bzl file outside of the project:
    expect.that(affected.isIncomplete()).isTrue();
    // but we can know that it affected a BUILD file inside a project:
    expect.that(affected.getModifiedPackages()).containsExactly(Path.of("my/build/package"));
  }

  @Test
  public void testModifySourceFile_included() {
    QuerySummary query = QuerySummary.create(createProtoForPackages("//my/build/package:rule"));
    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.MODIFY, Path.of("my/build/package/NewClass.java"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).isEmpty();
  }

  @Test
  public void testAddSourceFile_included() {
    QuerySummary query =
        QuerySummary.create(createProtoForPackages("//my/build/package:rule", "//my/build:rule"));
    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.ADD, Path.of("my/build/package/NewClass.java"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).containsExactly(Path.of("my/build/package"));
  }

  @Test
  public void testAddSourceFile_included_packageSubdirectory() {
    QuerySummary query =
        QuerySummary.create(createProtoForPackages("//my/build/package:rule", "//my/build:rule"));
    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.ADD, Path.of("my/build/package/lib/NewClass.java"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).containsExactly(Path.of("my/build/package"));
  }

  @Test
  public void testAddSourceFile_noPackage() {
    QuerySummary query = QuerySummary.create(createProtoForPackages("//my/build/package:rule"));
    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.ADD, Path.of("my/build/NewClass.java"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).isEmpty();
    expect.that(affected.getUnownedSources()).containsExactly(Path.of("my/build/NewClass.java"));
  }

  @Test
  public void testAddSourceFile_withNewSiblingPackage() {
    QuerySummary query = QuerySummary.create(createProtoForPackages("//my/build/package:rule"));
    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.ADD, Path.of("my/build/newpackage/BUILD")),
                    new WorkspaceFileChange(
                        Operation.ADD, Path.of("my/build/newpackage/NewClass.java"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).containsExactly(Path.of("my/build/newpackage"));
    expect.that(affected.getUnownedSources()).isEmpty();
  }

  @Test
  public void testAddSourceFile_withNewChildPackage() {
    QuerySummary query = QuerySummary.create(createProtoForPackages("//my/build/package:rule"));
    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.ADD, Path.of("my/build/package/lib/BUILD")),
                    new WorkspaceFileChange(
                        Operation.ADD, Path.of("my/build/package/lib/NewClass.java"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isIncomplete()).isFalse();
    expect
        .that(affected.getModifiedPackages())
        .containsExactly(Path.of("my/build/package"), Path.of("my/build/package/lib"));
    expect.that(affected.getUnownedSources()).isEmpty();
  }

  @Test
  public void testDeleteSourceFile() {
    QuerySummary query =
        QuerySummary.create(createProtoForPackages("//my/build/package:rule", "//my/build:rule"));
    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.DELETE, Path.of("my/build/package/NewClass.java"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).containsExactly(Path.of("my/build/package"));
  }

  @Test
  public void testDeleteSourceFileAndPackage() {
    QuerySummary query = QuerySummary.create(createProtoForPackages("//my/build/package:rule"));
    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.DELETE, Path.of("my/build/package/BUILD")),
                    new WorkspaceFileChange(
                        Operation.DELETE, Path.of("my/build/package/NewClass.java"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).isEmpty();
    expect.that(affected.getDeletedPackages()).containsExactly(Path.of("my/build/package"));
    expect.that(affected.getUnownedSources()).isEmpty();
  }

  @Test
  public void testDeleteBuildFileAndAddSourceInSamePackage() {
    QuerySummary query =
        QuerySummary.create(
            createProtoForPackages("//my/build/package/lib:rule", "//my/build/package:rule"));
    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.DELETE, Path.of("my/build/package/lib/BUILD")),
                    new WorkspaceFileChange(
                        Operation.ADD, Path.of("my/build/package/lib/NewClass.java"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).containsExactly(Path.of("my/build/package"));
    expect.that(affected.getDeletedPackages()).containsExactly(Path.of("my/build/package/lib"));
    expect.that(affected.getUnownedSources()).isEmpty();
  }

  @Test
  public void testDeleteBuildFileAndAddUnownedSourceInSamePackage() {
    QuerySummary query = QuerySummary.create(createProtoForPackages("//my/build/package:rule"));
    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.DELETE, Path.of("my/build/package/BUILD")),
                    new WorkspaceFileChange(
                        Operation.ADD, Path.of("my/build/package/NewClass.java"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).isEmpty();
    expect.that(affected.getDeletedPackages()).containsExactly(Path.of("my/build/package"));
    expect
        .that(affected.getUnownedSources())
        .containsExactly(Path.of("my/build/package/NewClass.java"));
  }

  @Test
  public void testPackagesWithErrorsAreAffected() {
    QuerySummary query =
        QuerySummary.create(
            new QuerySummaryTestBuilder()
                .addPackages("//my/build/package:rule", "//my/build/package/lib:rule")
                .addBuildFileLabelsWithErrors("//my/build/package:BUILD")
                .build());
    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(ImmutableSet.of())
            .build()
            .getAffectedPackages();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).containsExactly(Path.of("my/build/package"));
  }

  @Test
  public void testPackagesWithErrorsThenDeleted() {
    QuerySummary query =
        QuerySummary.create(
            new QuerySummaryTestBuilder()
                .addPackages("//my/build/package/lib1:rule", "//my/build/package/lib2:rule")
                .addBuildFileLabelsWithErrors("//my/build/package/lib1:BUILD")
                .build());
    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(NOOP_CONTEXT)
            .lastQuery(query)
            .projectIncludes(ImmutableSet.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.DELETE, Path.of("my/build/package/lib1/BUILD"))))
            .build()
            .getAffectedPackages();
    expect.that(affected.isIncomplete()).isFalse();
    expect.that(affected.getModifiedPackages()).isEmpty();
    expect.that(affected.getDeletedPackages()).containsExactly(Path.of("my/build/package/lib1"));
  }
}
