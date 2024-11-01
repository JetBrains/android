/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.impl.java.AndroidJavaDebugger;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.cppimpl.debug.BlazeAutoAndroidDebugger;
import com.google.idea.blaze.android.cppimpl.debug.BlazeNativeAndroidDebugger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test that ensures required android debuggers are available to blaze android projects. */
@RunWith(JUnit4.class)
public class AvailableDebuggersTest extends BlazeAndroidIntegrationTestCase {
  @Test
  public void getDebuggers_noCLanguageSupport_returnsJavaAndAutoDebuggers() {
    setProjectView("targets:", "  //java/com/foo/app:app", "android_sdk_platform: android-27");
    MockSdkUtil.registerSdk(workspace, "27");
    runFullBlazeSyncWithNoIssues();

    assertThat(
        AndroidDebugger.EP_NAME.getExtensionList().stream()
                .filter((debugger) -> debugger.supportsProject(getProject()))
                .map(Object::getClass))
        .containsExactly(
          AndroidJavaDebugger.class,
            BlazeAutoAndroidDebugger.class);
  }

  @Test
  public void getDebuggers_withCLanguageSupport_returnsJavaAndAutoAndNativeDebuggers() {
    setProjectView(
        "targets:",
        "  //java/com/foo/app:app",
        "additional_languages:",
        "  c",
        "android_sdk_platform: android-27");
    MockSdkUtil.registerSdk(workspace, "27");
    runFullBlazeSyncWithNoIssues();

    assertThat(
            AndroidDebugger.EP_NAME.getExtensionList().stream()
                .filter((debugger) -> debugger.supportsProject(getProject()))
                .map(Object::getClass)
                .collect(toImmutableList()))
        .containsExactly(
            AndroidJavaDebugger.class,
            BlazeAutoAndroidDebugger.class,
            BlazeNativeAndroidDebugger.class);
  }
}
