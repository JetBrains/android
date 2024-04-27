/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.testutils.junit4;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.testutils.JarTestSuiteRunner;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.internal.builders.AllDefaultPossibilitiesBuilder;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.model.InitializationError;

/**
 * Tests {@link OldAgpSuite} with nested internal test classes.
 */
@RunWith(JUnit4.class)
public class OldAgpSuiteTest {

  @RunWith(JUnit4.class)
  @OldAgpTest(gradleVersions = {"4.1", "4.2"}, agpVersions = {"4.2"})
  public static class AgpTestMultiple {
    @Test
    public void shouldRun() {
    }
  }

  @RunWith(JUnit4.class)
  @OldAgpTest(gradleVersions = {"4.1", "4.2"}, agpVersions = {"4.1", "4.2"})
  public static class OverrideAgpTest {

    @Test
    @OldAgpTest(gradleVersions = {"4.2"}, agpVersions = {"4.1", "4.2"})
    public void shouldRunGradleOnly42() {
    }

    @Test
    @OldAgpTest(gradleVersions = {"4.1", "4.2"}, agpVersions = {"4.2"})
    public void shouldRunAgpOnly42() {
    }
  }

  @RunWith(JUnit4.class)
  public static class MethodOnly {
    @Test
    @OldAgpTest(gradleVersions = "4.2", agpVersions = "4.2")
    public void shouldRun() {
    }
  }

  @RunWith(JUnit4.class)
  public static class InvalidAnnotation {
    @Test
    @OldAgpTest(gradleVersions = "4.2")
    public void missingAgpVersions() {
    }
  }

  @RunWith(JUnit4.class)
  public static class MissingAnnotation {
    @Test
    public void missingAnnotation() {
    }
  }

  @RunWith(JUnit4.class)
  @OldAgpTest
  public static class MissingVersions {
    @Test
    public void missingAnnotation() {
    }
  }

  @OldAgpTest
  @RunWith(Parameterized.class)
  public static class ParametrizedAgpTest {
    @Parameterized.Parameter(0)
    public boolean param = true;

    @Parameterized.Parameters(name = "{0}")
    public static List<Boolean> paramValues() {
      return ImmutableList.of(false, true);
    }

    @Test
    public void shouldRun() {
    }
  }

  @RunWith(Parameterized.class)
  public static class ParametrizedOtherTest {
    @Parameterized.Parameter(0)
    public boolean param = true;

    @Parameterized.Parameters(name = "{0}")
    public static List<Boolean> paramValues() {
      return ImmutableList.of(false, true);
    }

    @Test
    public void shouldNotRun() {
    }
  }

  @JarTestSuiteRunner.ExcludeClasses({ExampleSuite.class, MethodOnly.class})
  public static class ExampleSuite {
  }

  @Test
  public void filterRunners_keepsTestMethods() throws Throwable {
    List<Runner> runners = createRunners(AgpTestMultiple.class, OverrideAgpTest.class);
    OldAgpFilter filter = new OldAgpFilter("4.2", "4.2", false);

    List<Runner> filteredRunners = OldAgpSuite.filterRunners(filter, runners);

    hasTest(filteredRunners, "AgpTestMultiple.shouldRun");
    hasTest(filteredRunners, "OverrideAgpTest.shouldRunGradleOnly42");
    hasTest(filteredRunners, "OverrideAgpTest.shouldRunAgpOnly42");
  }

  @Test
  public void filterRunners_filterGradleVersion() throws Throwable {
    List<Runner> runners = createRunners(AgpTestMultiple.class, OverrideAgpTest.class);
    OldAgpFilter filter = new OldAgpFilter("4.1", "4.2", false);

    List<Runner> filteredRunners = OldAgpSuite.filterRunners(filter, runners);

    hasTest(filteredRunners, "AgpTestMultiple.shouldRun");
    hasTest(filteredRunners, "OverrideAgpTest.shouldRunAgpOnly42");
  }

  @Test
  public void filterRunners_noTestsFailsSuite() throws Throwable {
    List<Runner> runners = createRunners(MethodOnly.class);
    OldAgpFilter filter = new OldAgpFilter("0.0", "0.0", false);

    try {
      List<Runner> filteredRunners = OldAgpSuite.filterRunners(filter, runners);
      fail("Expected 0 runners, got: " + filteredRunners);
    }
    catch (InitializationError e) {
      // expected as no runners were left to run
    }
  }

  @Test
  public void filterRunners_methodOnly() throws Throwable {
    List<Runner> runners = createRunners(MethodOnly.class);
    OldAgpFilter filter = new OldAgpFilter("4.2", "4.2", false);

    List<Runner> filteredRunners = OldAgpSuite.filterRunners(filter, runners);

    hasTest(filteredRunners, "MethodOnly.shouldRun");
    assertThat(runnerTestCount(filteredRunners)).isEqualTo(1);
  }

  @Test
  public void filterRunners_missingAnnotation_doNotIgnoreOtherTests() throws Throwable {
    List<Runner> runners = createRunners(MissingAnnotation.class, AgpTestMultiple.class);
    OldAgpFilter filter = new OldAgpFilter("4.2", "4.2", false);

    try {
      List<Runner> filteredRunners = OldAgpSuite.filterRunners(filter, runners);
      fail("Expected to fail on test MissingAnnotation, got: " + filteredRunners);
    }
    catch (IllegalStateException e) {
      // expected as no runners were left to run
    }
  }

  @Test
  public void filterRunners_missingAnnotation_ignoreOtherTests() throws Throwable {
    List<Runner> runners = createRunners(MissingAnnotation.class, AgpTestMultiple.class);
    OldAgpFilter filter = new OldAgpFilter("4.2", "4.2", true);

    List<Runner> filteredRunners = OldAgpSuite.filterRunners(filter, runners);
    hasTest(filteredRunners, "AgpTestMultiple.shouldRun");
    assertThat(runnerTestCount(filteredRunners)).isEqualTo(1);
  }

  @Test
  public void filterRunners_missingVersions() throws Throwable {
    List<Runner> runners = createRunners(MissingVersions.class);
    OldAgpFilter filter = new OldAgpFilter("4.2", "4.2", false);

    try {
    List<Runner> filteredRunners = OldAgpSuite.filterRunners(filter, runners);
      fail("Expected 0 runners, got: " + filteredRunners);
    }
    catch (IllegalStateException e) {
      // expected as no runners were left to run
    }

  }

  @Test
  public void filterRunners_missingAnnotationValue() throws Throwable {
    List<Runner> runners = createRunners(InvalidAnnotation.class);
    OldAgpFilter filter = new OldAgpFilter("4.1", "4.2", true);

    try {
      List<Runner> filteredRunners = OldAgpSuite.filterRunners(filter, runners);
      fail("Expected 0 runners, got: " + filteredRunners);
    }
    catch (IllegalStateException e) {
      // expected as no runners were left to run
    }
  }

  @Test
  public void excludeTests() {
    List<Class<?>> testClasses = ImmutableList.of(ExampleSuite.class, MethodOnly.class, OverrideAgpTest.class);

    assertThat(OldAgpSuite.excludeTests(testClasses, ExampleSuite.class)).containsExactly(OverrideAgpTest.class);
  }

  @Test
  public void testParametrized() throws Throwable {
    List<Runner> runners = createRunners(ParametrizedAgpTest.class, ParametrizedOtherTest.class);
    OldAgpFilter filter = new OldAgpFilter("4.2", "4.2", true);

    List<Runner> filteredRunners = OldAgpSuite.filterRunners(filter, runners);

    hasTest(filteredRunners, "ParametrizedAgpTest.shouldRun[false]");
    hasTest(filteredRunners, "ParametrizedAgpTest.shouldRun[true]");
    assertThat(runnerTestCount(filteredRunners)).isEqualTo(2);
  }

  private static String className(Runner runner) {
    return ((JUnit4)runner).getTestClass().getName();
  }

  private static Stream<Description> descriptionTreeLeaves(Description description) {
    if (!description.getChildren().isEmpty()) {
      return description.getChildren().stream().flatMap(OldAgpSuiteTest::descriptionTreeLeaves);
    }
    else {
      return Stream.of(description);
    }
  }

  private static void hasTest(List<Runner> runners, String testName) {
    // build a list of $className.$methodName
    List<String> testNames = runners.stream()
      .flatMap(r -> descriptionTreeLeaves(r.getDescription()))
      .map(desc -> String.format("%s.%s", desc.getTestClass().getSimpleName(), desc.getMethodName()))
      .collect(Collectors.toList());

    assertThat(testNames).contains(testName);
  }

  private List<Runner> createRunners(Class<?>... classes) throws Throwable {
    AllDefaultPossibilitiesBuilder
      runnerBuilder
      = new AllDefaultPossibilitiesBuilder(/*canUseSuiteMethod=*/true);
    List<Runner> runners = new ArrayList<>();
    for (Class<?> aClass : classes) {
      runners.add(runnerBuilder.runnerForClass(aClass));
    }
    return runners;
  }

  private int runnerTestCount(List<Runner> runners) {
    return runners.stream()
      .map(Runner::testCount)
      .reduce(Integer::sum)
      .get();
  }
}
