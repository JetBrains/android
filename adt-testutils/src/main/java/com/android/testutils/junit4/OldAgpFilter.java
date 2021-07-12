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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.junit.runner.Describable;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;

/**
 * A Filter for running a subset tests using @OldAgpTest.
 *
 * <p>Testcases may use annotations on a method, or the test class. Annotations on the method
 * take priority over class annotations.
 *
 * <p>Tests not using @OldAgpTest are filtered out. If the Gradle version or AGP version
 * provided to this filter is not found on any @OldAgpTest annotations for the test case,
 * it is filtered out.
 */
class OldAgpFilter extends Filter {
  private final String allowedGradleVersion;
  private final String allowedAgpVersion;

  /*
   * Deques are used as stacks to keep track of the last specified value
   * at any level in the (class/method) test hierarchy.
   */
  private final Deque<List<String>> gradleVersionsStack = new ArrayDeque<>();
  private final Deque<List<String>> agpVersionsStack = new ArrayDeque<>();

  OldAgpFilter(String allowedGradleVersion, String allowedAgpVersion) {
    this.allowedGradleVersion = allowedGradleVersion;
    this.allowedAgpVersion = allowedAgpVersion;
  }

  @Override
  public void apply(Object child) throws NoTestsRemainException {
    // applies default gradle and AGP versions.
    // For example, test classes may apply @OldAgpTest to all child test cases.
    List<String> appliedGradleVersions = Collections.emptyList();
    List<String> appliedAgpVersions = Collections.emptyList();
    if (child instanceof Describable) {
      Description description = ((Describable)child).getDescription();
      appliedGradleVersions = getGradleVersions(description);
      appliedAgpVersions = getAgpVersions(description);
    }
    gradleVersionsStack.push(appliedGradleVersions);
    agpVersionsStack.push(appliedAgpVersions);
    try {
      super.apply(child);
    }
    finally {
      gradleVersionsStack.pop();
      agpVersionsStack.pop();
    }
  }

  @Override
  public boolean shouldRun(Description description) {
    List<String> gradleVersions = getGradleVersions(description);
    if (gradleVersions.isEmpty()) {
      gradleVersions = gradleVersionsStack.peek();
    }
    List<String> agpVersions = getAgpVersions(description);
    if (agpVersions.isEmpty()) {
      agpVersions = agpVersionsStack.peek();
    }

    if ((gradleVersions.isEmpty() && !agpVersions.isEmpty())
        || (!gradleVersions.isEmpty() && agpVersions.isEmpty())) {
      throw new IllegalStateException(
        "@OldAgpTest must set both gradleVersions and agpVersions. One of these"
        + " is missing for " + description);
    }

    return gradleVersions.contains(allowedGradleVersion)
           && agpVersions.contains(allowedAgpVersion);
  }

  @Override
  public String describe() {
    return "Filters on gradleVersion=" + allowedGradleVersion
           + " agpVersions=" + allowedAgpVersion;
  }

  private List<String> getGradleVersions(Description description) {
    OldAgpTest annotation = description.getAnnotation(OldAgpTest.class);
    if (annotation == null) {
      return Collections.emptyList();
    }
    return ImmutableList.copyOf(annotation.gradleVersions());
  }

  private List<String> getAgpVersions(Description description) {
    OldAgpTest annotation = description.getAnnotation(OldAgpTest.class);
    if (annotation == null) {
      return Collections.emptyList();
    }
    return ImmutableList.copyOf(annotation.agpVersions());
  }
}
