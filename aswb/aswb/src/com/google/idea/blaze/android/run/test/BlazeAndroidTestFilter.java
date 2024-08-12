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
package com.google.idea.blaze.android.run.test;

import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A test filter specification for Android tests. */
final class BlazeAndroidTestFilter {
  // Specifies that Android tests should be filtered by name.
  private static final String TEST_FILTER_BY_NAME =
      BlazeFlags.TEST_ARG + "--test_filter_spec=TEST_NAME";
  // As part of a name filter spec, the packages to include.
  private static final String TEST_PACKAGE_NAMES = BlazeFlags.TEST_ARG + "--test_package_names=";
  // As part of a name filter spec, the classes to include.
  private static final String TEST_CLASS_NAMES = BlazeFlags.TEST_ARG + "--test_class_names=";
  // As part of a name filter spec, the full names of methods to run.
  private static final String TEST_METHOD_NAMES = BlazeFlags.TEST_ARG + "--test_method_full_names=";

  private final int testingType;
  @Nullable private final String className;
  @Nullable private final String methodName;
  @Nullable private final String packageName;

  public BlazeAndroidTestFilter(
      int testingType,
      @Nullable String className,
      @Nullable String methodName,
      @Nullable String packageName) {
    this.testingType = testingType;
    this.className = className;
    this.methodName = methodName;
    this.packageName = packageName;
  }

  @NotNull
  public ImmutableList<String> getBlazeFlags() {
    if (testingType == BlazeAndroidTestRunConfigurationState.TEST_ALL_IN_TARGET) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<String> flags = ImmutableList.builder();
    flags.add(TEST_FILTER_BY_NAME);
    switch (testingType) {
      case AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE:
        assert packageName != null;
        flags.add(TEST_PACKAGE_NAMES + packageName);
        break;
      case AndroidTestRunConfiguration.TEST_CLASS:
        assert className != null;
        flags.add(TEST_CLASS_NAMES + className);
        break;
      case AndroidTestRunConfiguration.TEST_METHOD:
        assert className != null;
        assert methodName != null;
        flags.add(TEST_METHOD_NAMES + className + "#" + methodName);
        break;
      default:
        assert false : "Unknown testing type.";
    }
    return flags.build();
  }
}
