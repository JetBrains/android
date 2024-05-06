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

import com.google.common.collect.ImmutableList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

/**
 * A Filter for running a subset tests using @OldAgpTest.
 *
 * <p>Testcases may use annotations on a method, or the test class. Annotations on the method
 * take priority over class annotations. At least one of them is required. Errors are reported on any tests without annotations.
 *
 * <p> Any parameterized tests need to take care of filtering themselves by inspecting the values of {@code OldAgpSuite.AGP_VERSION} and
 * {@code OldAgpSuite.GRADLE_VERSION}.
 *
 * <p>NOTE: If none of the configured shards provides the expected combination of the AGP and Gradle versions the test will silently
 * not run.
 */
class OldAgpFilter extends Filter {
  private final String allowedGradleVersion;
  private final String allowedAgpVersion;
  private final boolean ignoreOtherTests;

  private final Set<Description> expectedAllowedChildren = new HashSet<>();

  OldAgpFilter(String allowedGradleVersion, String allowedAgpVersion, boolean ignoreOtherTests) {
    this.allowedGradleVersion = allowedGradleVersion;
    this.allowedAgpVersion = allowedAgpVersion;
    this.ignoreOtherTests = ignoreOtherTests;
  }

  @Override
  public boolean shouldRun(Description description) {
    List<String> gradleVersions = getGradleVersions(description);
    List<String> agpVersions = getAgpVersions(description);

    if (description.getTestClass() == null && gradleVersions.isEmpty() && agpVersions.isEmpty()) {
      // Parametrised test group.
      expectedAllowedChildren.addAll(description.getChildren());
      return true;
    }
    if (!isOldAgpTest(description)) {
      if (!ignoreOtherTests) {
        throw new IllegalStateException("@OldAgpTest is missing for " + description);
      }
      expectedAllowedChildren.remove(description);
      return false;
    }
    boolean result;
    if (expectedAllowedChildren.remove(description)) {
      expectedAllowedChildren.addAll(description.getChildren());
      result = true; // parametrised tests.
    }
    else {
      if (gradleVersions.isEmpty() || agpVersions.isEmpty()) {
        throw new IllegalStateException(
          "@OldAgpTest must set both gradleVersions and agpVersions. At least one of these"
          + " is missing for " + description);
      }

      result = gradleVersions.contains(allowedGradleVersion)
               && agpVersions.contains(allowedAgpVersion);
    }

    return result;
  }

  @Override
  public String describe() {
    return "Filters on gradleVersion=" + allowedGradleVersion
           + " agpVersions=" + allowedAgpVersion;
  }

  static boolean isOldAgpTest(Description description) {
    OldAgpTest annotation = description.getAnnotation(OldAgpTest.class);
    if (annotation != null) {
      return true;
    }
    Class<?> testClass = description.getTestClass();
    return testClass != null && testClass.getAnnotation(OldAgpTest.class) != null;
  }

  private List<String> getGradleVersions(Description description) {
    OldAgpTest annotation = description.getAnnotation(OldAgpTest.class);
    if (annotation == null) {
      Class<?> testClass = description.getTestClass();
      if (testClass == null) {
        return ImmutableList.of();
      }
      annotation = testClass.getAnnotation(OldAgpTest.class);
      if (annotation == null) {
        return ImmutableList.of();
      }
    }
    return ImmutableList.copyOf(annotation.gradleVersions());
  }

  private List<String> getAgpVersions(Description description) {
    OldAgpTest annotation = description.getAnnotation(OldAgpTest.class);
    if (annotation == null) {
      Class<?> testClass = description.getTestClass();
      if (testClass == null) {
        return ImmutableList.of();
      }
      annotation = testClass.getAnnotation(OldAgpTest.class);
      if (annotation == null) {
        return ImmutableList.of();
      }
    }
    return ImmutableList.copyOf(annotation.agpVersions());
  }
}
