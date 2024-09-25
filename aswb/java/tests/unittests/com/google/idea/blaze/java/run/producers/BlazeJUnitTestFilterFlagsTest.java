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
package com.google.idea.blaze.java.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.java.run.producers.BlazeJUnitTestFilterFlags.JUnitVersion;
import com.google.idea.blaze.java.run.producers.JUnitParameterizedClassHeuristic.ParameterizedTestInfo;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeJUnitTestFilterFlags}. */
@RunWith(JUnit4.class)
public class BlazeJUnitTestFilterFlagsTest extends BlazeTestCase {

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    ExperimentService experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);
  }

  @Test
  public void testSingleJUnit4ClassFilter() {
    assertThat(
            BlazeJUnitTestFilterFlags.testFilterForClassAndMethods(
                "com.google.idea.ClassName", JUnitVersion.JUNIT_4, ImmutableList.of(), null))
        .isEqualTo("com.google.idea.ClassName#");
  }

  @Test
  public void testSingleJUnit3ClassFilter() {
    assertThat(
            BlazeJUnitTestFilterFlags.testFilterForClassAndMethods(
                "com.google.idea.ClassName", JUnitVersion.JUNIT_3, ImmutableList.of(), null))
        .isEqualTo("com.google.idea.ClassName");
  }

  @Test
  public void testParameterizedIgnoredForSingleClass() {
    assertThat(
            BlazeJUnitTestFilterFlags.testFilterForClassAndMethods(
                "com.google.idea.ClassName", JUnitVersion.JUNIT_4, ImmutableList.of(), null))
        .isEqualTo("com.google.idea.ClassName#");
  }

  @Test
  public void testJUnit4ClassAndSingleMethod() {
    assertThat(
            BlazeJUnitTestFilterFlags.testFilterForClassAndMethods(
                "com.google.idea.ClassName",
                JUnitVersion.JUNIT_4,
                ImmutableList.of("testMethod1"),
                null))
        .isEqualTo("com.google.idea.ClassName#testMethod1$");
  }

  @Test
  public void testJUnit3ClassAndSingleMethod() {
    assertThat(
            BlazeJUnitTestFilterFlags.testFilterForClassAndMethods(
                "com.google.idea.ClassName",
                JUnitVersion.JUNIT_3,
                ImmutableList.of("testMethod1"),
                null))
        .isEqualTo("com.google.idea.ClassName#testMethod1");
  }

  @Test
  public void testJUnit4ClassAndMultipleMethods() {
    assertThat(
            BlazeJUnitTestFilterFlags.testFilterForClassAndMethods(
                "com.google.idea.ClassName",
                JUnitVersion.JUNIT_4,
                ImmutableList.of("testMethod1", "testMethod2"),
                null))
        .isEqualTo("com.google.idea.ClassName#(testMethod1|testMethod2)$");
  }

  @Test
  public void testJUnit4ParameterizedClassAndMultipleMethods() {
    assertThat(
            BlazeJUnitTestFilterFlags.testFilterForClassAndMethods(
                "com.google.idea.ClassName",
                JUnitVersion.JUNIT_4,
                ImmutableList.of("testMethod1(\\[.*\\])?", "testMethod2(\\[.*\\])?"),
                null))
        .isEqualTo("com.google.idea.ClassName#(testMethod1(\\[.*\\])?|testMethod2(\\[.*\\])?)$");
  }

  @Test
  public void testJUnit3ClassAndMultipleMethods() {
    assertThat(
            BlazeJUnitTestFilterFlags.testFilterForClassAndMethods(
                "com.google.idea.ClassName",
                JUnitVersion.JUNIT_3,
                ImmutableList.of("testMethod1", "testMethod2"),
                null))
        .isEqualTo("com.google.idea.ClassName#testMethod1,testMethod2");
  }

  @Test
  public void testParameterizedClassNameWithNoMethods() {
    ParameterizedTestInfo parameterizedTestInfo =
        ParameterizedTestInfo.builder()
            .runnerClass("BurstJUnit4")
            .testClassSuffixRegex("\\[MY_SUFFIX\\]")
            .testMethodSuffixRegex(".*")
            .build();
    assertThat(
            BlazeJUnitTestFilterFlags.testFilterForClassAndMethods(
                "com.google.idea.ClassName",
                JUnitVersion.JUNIT_4,
                ImmutableList.of(),
                parameterizedTestInfo))
        .isEqualTo("com.google.idea.ClassName\\[MY_SUFFIX\\]#");
  }

  @Test
  public void testParameterizedClassNameWithMethods() {
    ParameterizedTestInfo parameterizedTestInfo =
        ParameterizedTestInfo.builder()
            .runnerClass("BurstJUnit4")
            .testClassSuffixRegex("\\[MY_SUFFIX\\]")
            .testMethodSuffixRegex(".*")
            .build();
    assertThat(
            BlazeJUnitTestFilterFlags.testFilterForClassAndMethods(
                "com.google.idea.ClassName",
                JUnitVersion.JUNIT_4,
                ImmutableList.of("testMethod1", "testMethod2"),
                parameterizedTestInfo))
        .isEqualTo("com.google.idea.ClassName\\[MY_SUFFIX\\]#(testMethod1|testMethod2)$");
  }
}
