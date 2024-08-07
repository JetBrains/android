/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.sharding;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.query.BlazeQueryLabelKindParser;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeQueryLabelKindParser}. */
@RunWith(JUnit4.class)
public class QueryResultLineProcessorTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    applicationServices.register(ExperimentService.class, new MockExperimentService());
  }

  @Test
  public void testRecognizesStandardResultLines() {
    BlazeQueryLabelKindParser processor = new BlazeQueryLabelKindParser(x -> true);

    processor.processLine("css_library rule //java/com/google/foo/styles:global");
    processor.processLine("java_library rule //java/com/google/bar/console:runtime_deps");

    ImmutableList<TargetInfo> targets = processor.getTargets();
    assertThat(targets)
        .containsExactly(
            TargetInfo.builder(Label.create("//java/com/google/foo/styles:global"), "css_library")
                .build(),
            TargetInfo.builder(
                    Label.create("//java/com/google/bar/console:runtime_deps"), "java_library")
                .build());
  }

  @Test
  public void testIgnoresNonRules() {
    BlazeQueryLabelKindParser processor = new BlazeQueryLabelKindParser(x -> true);

    processor.processLine("generated file //java/com/google/foo:libthrowable_utils.jar");
    processor.processLine("source file //java/com/google/foo:BUILD");
    processor.processLine("package group //java/com/google/foo:packages");

    ImmutableList<TargetInfo> targets = processor.getTargets();
    assertThat(targets).isEmpty();
  }

  @Test
  public void testFilterRuleTypes() {
    ImmutableSet<String> acceptedRuleTypes =
        ImmutableSet.of("java_library", "custom_type", "sh_test");
    BlazeQueryLabelKindParser processor =
        new BlazeQueryLabelKindParser(t -> acceptedRuleTypes.contains(t.ruleType));

    processor.processLine("css_library rule //java/com/google/foo/styles:global");
    processor.processLine("java_library rule //java/com/google/bar/console:runtime_deps");
    processor.processLine("java_test rule //java/com/google/bar/console:test1");
    processor.processLine("test_suite rule //java/com/google/bar/console:all_tests");
    processor.processLine("custom_type rule //java/com/google/bar/console:custom");
    processor.processLine("sh_test rule //java/com/google/bar/console:sh_test");

    ImmutableList<TargetInfo> targets = processor.getTargets();
    assertThat(targets)
        .containsExactly(
            TargetInfo.builder(
                    Label.create("//java/com/google/bar/console:runtime_deps"), "java_library")
                .build(),
            TargetInfo.builder(Label.create("//java/com/google/bar/console:custom"), "custom_type")
                .build(),
            TargetInfo.builder(Label.create("//java/com/google/bar/console:sh_test"), "sh_test")
                .build());
  }

  @Test
  public void testFilterRuleTypesRetainingExplicitlySpecifiedTargets() {
    ImmutableSet<String> acceptedRuleTypes =
        ImmutableSet.of("java_library", "custom_type", "sh_test");
    ImmutableSet<String> explicitTargets = ImmutableSet.of("//java/com/google/foo/styles:global");

    BlazeQueryLabelKindParser processor =
        new BlazeQueryLabelKindParser(
            t -> explicitTargets.contains(t.label) || acceptedRuleTypes.contains(t.ruleType));

    processor.processLine("css_library rule //java/com/google/foo/styles:global");
    processor.processLine("java_library rule //java/com/google/bar/console:runtime_deps");
    processor.processLine("java_test rule //java/com/google/bar/console:test1");
    processor.processLine("test_suite rule //java/com/google/bar/console:all_tests");
    processor.processLine("custom_type rule //java/com/google/bar/console:custom");
    processor.processLine("sh_test rule //java/com/google/bar/console:sh_test");

    ImmutableList<TargetInfo> targets = processor.getTargets();
    assertThat(targets)
        .containsExactly(
            TargetInfo.builder(Label.create("//java/com/google/foo/styles:global"), "css_library")
                .build(),
            TargetInfo.builder(
                    Label.create("//java/com/google/bar/console:runtime_deps"), "java_library")
                .build(),
            TargetInfo.builder(Label.create("//java/com/google/bar/console:custom"), "custom_type")
                .build(),
            TargetInfo.builder(Label.create("//java/com/google/bar/console:sh_test"), "sh_test")
                .build());
  }
}
