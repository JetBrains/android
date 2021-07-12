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

import com.android.testutils.TestGroup;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
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

  private static final String TEST_JAR_PATH = System.getProperty("test_jar_path");

  public OldAgpSuite(Class<?> klass, RunnerBuilder builder) throws InitializationError, IOException, ClassNotFoundException {
    super(klass, filteredAgpTests(klass, builder));
  }

  private static List<Runner> filteredAgpTests(Class<?> klass, RunnerBuilder builder)
    throws IOException, ClassNotFoundException, InitializationError {
    OldAgpFilter filter = new OldAgpFilter(GRADLE_VERSION, AGP_VERSION);
    List<Class<?>> testClasses = TestGroup.builder()
      .includeJUnit3()
      .build()
      .scanTestClasses(TEST_JAR_PATH);

    return filterRunners(filter, builder.runners(klass, testClasses));
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
