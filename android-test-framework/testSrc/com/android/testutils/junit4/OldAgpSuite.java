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

import com.android.testutils.JarTestSuiteRunner;
import com.android.testutils.TestGroup;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

/**
 * Runs tests annotated with {@link OldAgpTest}. Annotations on methods take precedence over class
 * annotations.
 *
 * <p>OldAgpSuite only allows one version of Gradle or AGP to be specified. The versions are plain
 * strings passed through system properties, gradle.version and agp.version. {@link OldAgpTest}s
 * containing a matching version are run while all other tests are excluded.
 *
 * <p>Gradle and AGP versions are only used to determine which tests are run. It is up to
 * individual tests how {@link AGP_VERSION} and {@link GRADLE_VERSION} are used at test time.
 */
public final class OldAgpSuite extends Suite {
  /** The Android Gradle Plugin version being used for all tests in this suite. */
  public static final String AGP_VERSION = System.getProperty("agp.version");
  /** The Gradle version being used for all tests in this suite. */
  public static final String GRADLE_VERSION = System.getProperty("gradle.version");

  private static final String TEST_JAR_PATH = System.getProperty("test.suite.jar");

  private static final boolean IGNORE_OTHER_TESTS = System.getProperty("ignore_other_tests", "false").equalsIgnoreCase("true");

  public OldAgpSuite(Class<?> suiteClass, RunnerBuilder builder) throws InitializationError, IOException, ClassNotFoundException {
    super(suiteClass, filteredAgpTests(suiteClass, builder));
  }

  private static List<Runner> filteredAgpTests(Class<?> suiteClass, RunnerBuilder builder)
    throws IOException, ClassNotFoundException, InitializationError {
    OldAgpFilter filter = new OldAgpFilter(GRADLE_VERSION, AGP_VERSION, IGNORE_OTHER_TESTS);
    List<Class<?>> testClasses = TestGroup.builder()
      .includeJUnit3()
      .build()
      .scanTestClasses(suiteClass, TEST_JAR_PATH);

    testClasses = excludeTests(testClasses, suiteClass);

    return filterRunners(filter, builder.runners(suiteClass, testClasses));
  }

  static List<Class<?>> excludeTests(List<Class<?>> tests, Class<?> suiteClass) {
    Set<Class<?>> classesToExclude = excludedClasses(suiteClass);
    return tests
      .stream()
      .filter(c -> !classesToExclude.contains(c))
      .collect(Collectors.toList());
  }

  private static Set<Class<?>> excludedClasses(Class<?> suiteClass) {
    JarTestSuiteRunner.ExcludeClasses annotation = suiteClass.getAnnotation(JarTestSuiteRunner.ExcludeClasses.class);
    if (annotation == null) {
      return ImmutableSet.of();
    }
    return ImmutableSet.copyOf(annotation.value());
  }

  static List<Runner> filterRunners(OldAgpFilter filter, Collection<Runner> runners) throws InitializationError {
    List<Runner> runnersAfterFiltering = new ArrayList<>();
    for (Runner runner : runners) {
      try {
        filter.apply(runner);
        runnersAfterFiltering.add(runner);
      }
      catch (NoTestsRemainException e) {
        // runner is excluded
      }
    }
    if (runnersAfterFiltering.isEmpty()) {
      throw new InitializationError(
        String.format("There were %d tests, but they were all removed because none"
                      + " of them met the specified gradle and AGP versions.", runners.size())
      );
    }
    return runnersAfterFiltering;
  }

}
