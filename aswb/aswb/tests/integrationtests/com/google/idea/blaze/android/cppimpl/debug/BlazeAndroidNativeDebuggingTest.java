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
package com.google.idea.blaze.android.cppimpl.debug;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.intellij.execution.ExecutionException;
import com.jetbrains.cidr.execution.debugger.CidrDebuggerLanguageSupportManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Simple integration test to ensure that Android native debugging extensions are registered
 * correctly in ASWB's META-INF files.
 */
@RunWith(JUnit4.class)
public class BlazeAndroidNativeDebuggingTest extends BlazeIntegrationTestCase {
  @Test
  public void languageSupportRegistered() {
    BlazeCommandRunConfiguration runProfile =
        new BlazeCommandRunConfiguration(
            getProject(), BlazeCommandRunConfigurationType.getInstance().getFactory(), "test");

    // Use an android_binary target for the run config so that it uses
    // BlazeAndroidRunConfigurationHandler.
    runProfile.setTargetInfo(
        TargetInfo.builder(
                Label.create("//test:test"),
                AndroidBlazeRules.RuleTypes.ANDROID_BINARY.getKind().getKindString())
            .build());

    // Enable native debugging.
    BlazeAndroidBinaryRunConfigurationState state =
        runProfile.getHandlerStateIfType(BlazeAndroidBinaryRunConfigurationState.class);
    assertThat(state).isNotNull();
    state.getCommonState().setNativeDebuggingEnabled(true);

    try {
      CidrDebuggerLanguageSupportManager.getInstance().createEditor(getProject(), runProfile);
    } catch (ExecutionException e) {
      fail("BlazeNativeDebuggerLanguageSupport extension not registered correctly.");
    }
  }
}
