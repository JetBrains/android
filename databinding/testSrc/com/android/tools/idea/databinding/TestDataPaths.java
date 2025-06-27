/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.databinding;

import com.android.test.testutils.TestUtils;

/**
 * Constants for databinding test data locations.
 */
public final class TestDataPaths {
  public static final String TEST_DATA_ROOT = TestUtils.resolveWorkspacePath("tools/adt/idea/databinding/testData").toString();

  public static final String PROJECT_WITH_DATA_BINDING_SUPPORT = "projects/projectWithDataBindingSupport";
  public static final String PROJECT_WITH_DATA_BINDING_ANDROID_X = "projects/projectWithDataBindingAndroidX";
  public static final String PROJECT_WITH_DATA_BINDING_AND_SIMPLE_LIB = "projects/projectWithDataBindingAndSimpleLib";
  public static final String PROJECT_WITH_CIRCULAR_DEPENDENCIES = "projects/projectWithCircularDependencies";
  public static final String PROJECT_WITH_COMPILE_ERRORS = "projects/projectWithCompileErrors";
  public static final String PROJECT_WITH_SAME_PACKAGE_MODULES = "projects/projectWithSamePackageModules";
  public static final String PROJECT_FOR_TRACKING = "projects/projectForTracking";
  public static final String PROJECT_FOR_VIEWBINDING = "projects/projectForViewBinding";
  public static final String PROJECT_USING_JDK11 = "projects/projectUsingJdk11";
}
