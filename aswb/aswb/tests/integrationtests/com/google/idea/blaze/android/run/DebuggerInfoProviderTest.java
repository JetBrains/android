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
import static com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes.ANDROID_BINARY;
import static com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes.ANDROID_INSTRUMENTATION_TEST;

import com.android.tools.idea.run.editor.AndroidDebuggerInfoProvider;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.cppimpl.debug.BlazeNativeAndroidDebugger;
import com.google.idea.blaze.android.run.binary.AndroidBinaryLaunchMethodsUtils;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestLaunchMethodsProvider.AndroidTestLaunchMethod;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestRunConfigurationState;
import com.google.idea.blaze.android.tools.idea.run.editor.AndroidDebuggerCompat;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.RunManagerImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test that ensures required android debuggers are available through debugger info providers. */
@RunWith(JUnit4.class)
public class DebuggerInfoProviderTest extends BlazeAndroidIntegrationTestCase {
  private AndroidDebuggerInfoProvider debuggerInfoProvider;

  @Before
  public void setupProject() {
    setProjectView(
        "targets:",
        "  //java/com/foo/app:app",
        "  //javatests/com/foo/app:test",
        "android_sdk_platform: android-27",
        "additional_languages:",
        "  c");
    MockSdkUtil.registerSdk(workspace, "27");
    runFullBlazeSyncWithNoIssues();
    debuggerInfoProvider =
        AndroidDebuggerInfoProvider.EP_NAME.getExtensionList().stream()
            .filter(provider -> provider.supportsProject(getProject()))
            .findFirst()
            .get();
  }

  @Test
  public void getDebuggerFromProvider_nonNativeDebugging_returnsAndroidJavaDebugger() {
    assertThat(
            AndroidDebuggerCompat.getSelectedAndroidDebugger(
                debuggerInfoProvider, createAndroidBinaryRunConfiguration(false)))
        .isInstanceOf(AndroidDebuggerCompat.getAndroidJavaDebugger().getClass());
  }

  @Test
  public void getDebuggerFromProvider_withNativeDebugging_returnsBlazeAndroidNativeDebugger() {
    assertThat(
            AndroidDebuggerCompat.getSelectedAndroidDebugger(
                debuggerInfoProvider, createAndroidBinaryRunConfiguration(true)))
        .isInstanceOf(AndroidDebuggerCompat.getAndroidJavaDebugger().getClass());
  }

  @Test
  @SuppressWarnings({"rawtypes"}) // Raw type from upstream.
  public void getAndroidDebuggers_withAndroidBinaryRunConfiguration_returnsJavaAndNative() {
    ImmutableList<Class> classList =
        AndroidDebuggerCompat.getAndroidDebuggers(
                debuggerInfoProvider, createAndroidBinaryRunConfiguration(true))
            .stream()
            .map(Object::getClass)
            .collect(toImmutableList());
    assertThat(classList)
        .containsExactly(
            AndroidDebuggerCompat.getAndroidJavaDebugger().getClass(),
            BlazeNativeAndroidDebugger.class);
  }

  @Test
  @SuppressWarnings({"rawtypes"}) // Raw type from upstream.
  public void getAndroidDebuggers_withAndroidTestRunConfiguration_returnsJavaAndNative() {
    ImmutableList<Class> classList =
        AndroidDebuggerCompat.getAndroidDebuggers(
                debuggerInfoProvider, createAndroidTestRunConfiguration(true))
            .stream()
            .map(Object::getClass)
            .collect(toImmutableList());
    assertThat(classList)
        .containsExactly(
            AndroidDebuggerCompat.getAndroidJavaDebugger().getClass(),
            BlazeNativeAndroidDebugger.class);
  }

  private BlazeCommandRunConfiguration createAndroidBinaryRunConfiguration(
      boolean enableNativeDebugging) {
    RunManager runManager = RunManagerImpl.getInstanceImpl(getProject());
    RunnerAndConfigurationSettings runnerAndConfigurationSettings =
        runManager.createConfiguration(
            "Blaze Android Binary Run Configuration",
            BlazeCommandRunConfigurationType.getInstance().getFactory());
    runManager.addConfiguration(runnerAndConfigurationSettings, false);
    BlazeCommandRunConfiguration runConfig =
        (BlazeCommandRunConfiguration) runnerAndConfigurationSettings.getConfiguration();
    TargetInfo target =
        TargetInfo.builder(
                Label.create("//java/com/foo/app:app"), ANDROID_BINARY.getKind().getKindString())
            .build();
    runConfig.setTargetInfo(target);
    BlazeAndroidBinaryRunConfigurationState androidBinaryConfig =
        (BlazeAndroidBinaryRunConfigurationState) runConfig.getHandler().getState();
    androidBinaryConfig.setLaunchMethod(
        AndroidBinaryLaunchMethodsUtils.AndroidBinaryLaunchMethod.NON_BLAZE);
    androidBinaryConfig.getCommonState().setNativeDebuggingEnabled(enableNativeDebugging);
    return runConfig;
  }

  private BlazeCommandRunConfiguration createAndroidTestRunConfiguration(
      boolean enableNativeDebugging) {
    RunManager runManager = RunManagerImpl.getInstanceImpl(getProject());
    RunnerAndConfigurationSettings runnerAndConfigurationSettings =
        runManager.createConfiguration(
            "Blaze Instrumentation Test Run Configuration",
            BlazeCommandRunConfigurationType.getInstance().getFactory());
    runManager.addConfiguration(runnerAndConfigurationSettings, false);
    BlazeCommandRunConfiguration runConfig =
        (BlazeCommandRunConfiguration) runnerAndConfigurationSettings.getConfiguration();
    TargetInfo target =
        TargetInfo.builder(
                Label.create("//javatests/com/foo/app:test"),
                ANDROID_INSTRUMENTATION_TEST.getKind().getKindString())
            .build();
    runConfig.setTargetInfo(target);
    BlazeAndroidTestRunConfigurationState androidTestConfig =
        (BlazeAndroidTestRunConfigurationState) runConfig.getHandler().getState();
    androidTestConfig.setLaunchMethod(AndroidTestLaunchMethod.NON_BLAZE);
    androidTestConfig.getCommonState().setNativeDebuggingEnabled(enableNativeDebugging);
    return runConfig;
  }
}
