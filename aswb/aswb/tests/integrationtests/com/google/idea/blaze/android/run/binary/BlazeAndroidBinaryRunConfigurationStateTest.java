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

package com.google.idea.blaze.android.run.binary;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.binary.AndroidBinaryLaunchMethodsUtils.AndroidBinaryLaunchMethod;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeAndroidBinaryRunConfigurationState}. */
@RunWith(JUnit4.class)
public class BlazeAndroidBinaryRunConfigurationStateTest extends BlazeIntegrationTestCase {

  @Rule
  public final AndroidIntegrationTestSetupRule androidSetupRule =
      new AndroidIntegrationTestSetupRule();

  private BlazeAndroidBinaryRunConfigurationState state;

  @Before
  public final void doSetup() {
    state = new BlazeAndroidBinaryRunConfigurationState(buildSystem().getName());
  }

  @Test
  public void readAndWriteShouldMatch() throws InvalidDataException, WriteExternalException {
    BlazeAndroidRunConfigurationCommonState commonState = state.getCommonState();
    commonState.getBlazeFlagsState().setRawFlags(ImmutableList.of("--flag1", "--flag2"));
    commonState.setNativeDebuggingEnabled(true);

    state.setActivityClass("com.example.TestActivity");
    state.setMode(BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY);
    state.setLaunchMethod(AndroidBinaryLaunchMethod.MOBILE_INSTALL);
    state.setUseSplitApksIfPossible(false);
    state.setUseWorkProfileIfPresent(true);
    state.setUserId(2);
    state.setShowLogcatAutomatically(true);
    state.setDeepLink("http://deeplink");
    state.setAmStartOptions("-S");

    Element element = new Element("test");
    state.writeExternal(element);
    BlazeAndroidBinaryRunConfigurationState readState =
        new BlazeAndroidBinaryRunConfigurationState(buildSystem().getName());
    readState.readExternal(element);

    BlazeAndroidRunConfigurationCommonState readCommonState = readState.getCommonState();
    assertThat(readCommonState.getBlazeFlagsState().getRawFlags())
        .containsExactly("--flag1", "--flag2")
        .inOrder();
    assertThat(readCommonState.isNativeDebuggingEnabled()).isTrue();

    assertThat(readState.getActivityClass()).isEqualTo("com.example.TestActivity");
    assertThat(readState.getMode())
        .isEqualTo(BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY);
    assertThat(readState.getLaunchMethod()).isEqualTo(AndroidBinaryLaunchMethod.MOBILE_INSTALL);
    assertThat(readState.useSplitApksIfPossible()).isFalse();
    assertThat(readState.useWorkProfileIfPresent()).isTrue();
    assertThat(readState.getUserId()).isEqualTo(2);
    assertThat(readState.showLogcatAutomatically()).isTrue();
    assertThat(readState.getDeepLink()).isEqualTo("http://deeplink");
    assertThat(readState.getAmStartOptions()).isEqualTo("-S");
  }

  @Test
  public void readAndWriteShouldHandleNulls() throws InvalidDataException, WriteExternalException {
    Element element = new Element("test");
    state.writeExternal(element);
    BlazeAndroidBinaryRunConfigurationState readState =
        new BlazeAndroidBinaryRunConfigurationState(buildSystem().getName());
    readState.readExternal(element);

    BlazeAndroidRunConfigurationCommonState commonState = state.getCommonState();
    BlazeAndroidRunConfigurationCommonState readCommonState = readState.getCommonState();
    assertThat(readCommonState.getBlazeFlagsState().getRawFlags())
        .isEqualTo(commonState.getBlazeFlagsState().getRawFlags());
    assertThat(readCommonState.isNativeDebuggingEnabled())
        .isEqualTo(commonState.isNativeDebuggingEnabled());

    assertThat(readState.getActivityClass()).isEqualTo(state.getActivityClass());
    assertThat(readState.getMode()).isEqualTo(state.getMode());
    assertThat(readState.getLaunchMethod()).isEqualTo(state.getLaunchMethod());
    assertThat(readState.useSplitApksIfPossible()).isEqualTo(state.useSplitApksIfPossible());
    assertThat(readState.useWorkProfileIfPresent()).isEqualTo(state.useWorkProfileIfPresent());
    assertThat(readState.getUserId()).isEqualTo(state.getUserId());
    assertThat(readState.getDeepLink()).isEqualTo(state.getDeepLink());
    assertThat(readState.getAmStartOptions()).isEqualTo(state.getDeepLink());
  }

  @Test
  public void repeatedWriteShouldNotChangeElement() throws WriteExternalException {
    final XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());

    BlazeAndroidRunConfigurationCommonState commonState = state.getCommonState();
    commonState.getBlazeFlagsState().setRawFlags(ImmutableList.of("--flag1", "--flag2"));
    commonState.setNativeDebuggingEnabled(true);

    state.setActivityClass("com.example.TestActivity");
    state.setMode(BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY);
    state.setLaunchMethod(AndroidBinaryLaunchMethod.MOBILE_INSTALL);
    state.setUseSplitApksIfPossible(false);
    state.setUseWorkProfileIfPresent(true);
    state.setUserId(2);
    state.setShowLogcatAutomatically(true);
    state.setDeepLink("http://deeplink");

    Element firstWrite = new Element("test");
    state.writeExternal(firstWrite);
    Element secondWrite = firstWrite.clone();
    state.writeExternal(secondWrite);

    assertThat(xmlOutputter.outputString(secondWrite))
        .isEqualTo(xmlOutputter.outputString(firstWrite));
  }

  @Test
  public void editorApplyToAndResetFromShouldMatch() throws ConfigurationException {
    RunConfigurationStateEditor editor = state.getEditor(getProject());

    BlazeAndroidRunConfigurationCommonState commonState = state.getCommonState();
    commonState.getBlazeFlagsState().setRawFlags(ImmutableList.of("--flag1", "--flag2"));
    commonState.setNativeDebuggingEnabled(true);

    state.setActivityClass("com.example.TestActivity");
    state.setMode(BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY);
    state.setLaunchMethod(AndroidBinaryLaunchMethod.MOBILE_INSTALL);
    state.setUseSplitApksIfPossible(false);
    state.setUseWorkProfileIfPresent(true);
    state.setUserId(2);
    state.setShowLogcatAutomatically(true);
    state.setAmStartOptions("-S");
    // We don't test DeepLink because it is not exposed in the editor.
    // state.setDeepLink("http://deeplink");

    editor.resetEditorFrom(state);
    BlazeAndroidBinaryRunConfigurationState readState =
        new BlazeAndroidBinaryRunConfigurationState(buildSystem().getName());
    editor.applyEditorTo(readState);

    BlazeAndroidRunConfigurationCommonState readCommonState = readState.getCommonState();
    assertThat(readCommonState.getBlazeFlagsState().getRawFlags())
        .isEqualTo(commonState.getBlazeFlagsState().getRawFlags());
    assertThat(readCommonState.isNativeDebuggingEnabled())
        .isEqualTo(commonState.isNativeDebuggingEnabled());

    assertThat(readState.getActivityClass()).isEqualTo(state.getActivityClass());
    assertThat(readState.getMode()).isEqualTo(state.getMode());
    assertThat(readState.getLaunchMethod()).isEqualTo(state.getLaunchMethod());
    assertThat(readState.useSplitApksIfPossible()).isEqualTo(state.useSplitApksIfPossible());
    assertThat(readState.useWorkProfileIfPresent()).isEqualTo(state.useWorkProfileIfPresent());
    assertThat(readState.getUserId()).isEqualTo(state.getUserId());
    assertThat(readState.showLogcatAutomatically()).isEqualTo(state.showLogcatAutomatically());
    assertThat(readState.getAmStartOptions()).isEqualTo(state.getAmStartOptions());
    // We don't test DeepLink because it is not exposed in the editor.
    // assertThat(readState.getDeepLink()).isEqualTo(state.getDeepLink());
  }

  @Test
  public void editorApplyToAndResetFromShouldHandleNulls() throws ConfigurationException {
    RunConfigurationStateEditor editor = state.getEditor(getProject());

    editor.resetEditorFrom(state);
    BlazeAndroidBinaryRunConfigurationState readState =
        new BlazeAndroidBinaryRunConfigurationState(buildSystem().getName());
    editor.applyEditorTo(readState);

    BlazeAndroidRunConfigurationCommonState commonState = state.getCommonState();
    BlazeAndroidRunConfigurationCommonState readCommonState = readState.getCommonState();
    assertThat(readCommonState.getBlazeFlagsState().getRawFlags())
        .isEqualTo(commonState.getBlazeFlagsState().getRawFlags());
    assertThat(readCommonState.isNativeDebuggingEnabled())
        .isEqualTo(commonState.isNativeDebuggingEnabled());

    assertThat(readState.getActivityClass()).isEqualTo(state.getActivityClass());
    assertThat(readState.getMode()).isEqualTo(state.getMode());
    assertThat(readState.getLaunchMethod()).isEqualTo(state.getLaunchMethod());
    assertThat(readState.useSplitApksIfPossible()).isEqualTo(state.useSplitApksIfPossible());
    assertThat(readState.useWorkProfileIfPresent()).isEqualTo(state.useWorkProfileIfPresent());
    assertThat(readState.getUserId()).isEqualTo(state.getUserId());
    assertThat(readState.getAmStartOptions()).isEqualTo(state.getAmStartOptions());
    // We don't test DeepLink because it is not exposed in the editor.
    // assertThat(readState.getDeepLink()).isEqualTo(state.getDeepLink());
  }
}
