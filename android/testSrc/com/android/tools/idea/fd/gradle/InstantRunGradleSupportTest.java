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
package com.android.tools.idea.fd.gradle;

import com.android.builder.model.InstantRun;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.util.ReflectionUtil;

import java.util.stream.Collectors;

public class InstantRunGradleSupportTest extends JavaProjectTestCase {
  /**
   * Check that the set of fields in {@link InstantRun} is not changed without updating consumers.
   *
   * <p>If this test fails, update the method {@link InstantRunGradleSupport#fromModel(AndroidModuleModel)} to consume
   * the new field, then update this test.</p>
   */
  public void testModelConstants() {
    assertContainsElements(
      ReflectionUtil.collectFields(InstantRun.class).stream().map(f -> f.getName()).collect(Collectors.toList()),
      "STATUS_SUPPORTED", "STATUS_NOT_SUPPORTED_FOR_NON_DEBUG_VARIANT", "STATUS_NOT_SUPPORTED_VARIANT_USED_FOR_TESTING",
      "STATUS_NOT_SUPPORTED_FOR_JACK", "STATUS_NOT_SUPPORTED_FOR_EXTERNAL_NATIVE_BUILD", "STATUS_NOT_SUPPORTED_FOR_EXPERIMENTAL_PLUGIN",
      "STATUS_NOT_SUPPORTED_FOR_MULTI_APK");
  }
}
