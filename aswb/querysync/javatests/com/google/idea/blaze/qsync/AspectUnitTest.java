/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaTargetArtifacts;
import com.google.idea.blaze.qsync.java.artifacts.AspectProto.OutputArtifact;
import com.google.idea.blaze.qsync.java.artifacts.AspectProto.OutputArtifact.PathCase;
import com.google.idea.blaze.qsync.testdata.JavaInfoTxt;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AspectUnitTest {

  // TODO(mathewi) Write test cases for existing functionality inside build_dependencies.bzl.

  @Test
  public void external_dep_contains_sources() throws Exception {
    ImmutableMap<Label, JavaTargetArtifacts> byTarget =
        Maps.uniqueIndex(
            JavaInfoTxt.EXTERNAL_DEP.readOnlyProto().getArtifactsList(),
            t -> Label.of(t.getTarget()));
    TestData externalDependency = TestData.JAVA_LIBRARY_NO_DEPS_QUERY;
    assertThat(byTarget.get(externalDependency.getAssumedOnlyLabel()).getSrcsList())
        .containsExactly(
            externalDependency.getOnlySourcePath().resolve("TestClassNoDeps.java").toString());
  }

  @Test
  public void internal_dep_contains_no_sources() throws Exception {
    ImmutableMap<Label, JavaTargetArtifacts> byTarget =
        Maps.uniqueIndex(
            JavaInfoTxt.INTERNAL_DEP.readOnlyProto().getArtifactsList(),
            t -> Label.of(t.getTarget()));
    TestData internalDependency = TestData.JAVA_LIBRARY_NO_DEPS_QUERY;
    assertThat(byTarget.get(internalDependency.getAssumedOnlyLabel()).getSrcsList()).isEmpty();
  }

  @Test
  public void exporting_target_has_no_jars() throws Exception {
    ImmutableMap<Label, JavaTargetArtifacts> byTarget =
        Maps.uniqueIndex(
            JavaInfoTxt.EXTERNAL_EXPORTS.readOnlyProto().getArtifactsList(),
            t -> Label.of(t.getTarget()));

    // This target exports another target but does not include any direct sources:
    Label exportingTarget =
        Label.fromWorkspacePackageAndName(
            "", TestData.JAVA_EXPORTED_DEP_QUERY.getOnlySourcePath(), "exported-collect");
    assertThat(byTarget).containsKey(exportingTarget);
    assertThat(byTarget.get(exportingTarget).getJarsList()).isEmpty();
  }

  @Test
  public void each_jar_beloings_to_a_single_target() throws IOException {
    HashMultimap<Label, String> targetToJar =
        JavaInfoTxt.EXTERNAL_EXPORTS.readOnlyProto().getArtifactsList().stream()
            .collect(
                Multimaps.flatteningToMultimap(
                    t -> Label.of(t.getTarget()),
                    t ->
                        t.getJarsList().stream()
                            .filter(a -> a.getPathCase() == PathCase.FILE)
                            .map(OutputArtifact::getFile),
                    HashMultimap::create));
    Multimap<String, Label> jarToTarget = Multimaps.invertFrom(targetToJar, HashMultimap.create());
    for (String jarPath : jarToTarget.keys()) {
      assertWithMessage("targets for jar " + jarPath).that(jarToTarget.get(jarPath)).hasSize(1);
    }
  }
}
