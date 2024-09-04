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
package com.google.idea.blaze.base.run.state;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeCommandRunConfigurationCommonState}. */
@RunWith(JUnit4.class)
public class BlazeCommandRunConfigurationCommonStateTest extends BlazeTestCase {
  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", BuildSystemName.Blaze, ProjectType.ASPECT_SYNC);
  private static final BlazeCommandName COMMAND = BlazeCommandName.fromString("command");

  private BlazeCommandRunConfigurationCommonState state;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);

    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    state = new BlazeCommandRunConfigurationCommonState(Blaze.getBuildSystemName(project));
  }

  @Test
  public void readAndWriteShouldMatch() throws Exception {
    state.getCommandState().setCommand(COMMAND);
    state.getBlazeFlagsState().setRawFlags(ImmutableList.of("--flag1", "--flag2"));
    state.getExeFlagsState().setRawFlags(ImmutableList.of("--exeFlag1"));
    state.getBlazeBinaryState().setBlazeBinary("/usr/bin/blaze");

    Element element = new Element("test");
    state.writeExternal(element);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.getBuildSystemName(project));
    readState.readExternal(element);

    assertThat(readState.getCommandState().getCommand()).isEqualTo(COMMAND);
    assertThat(readState.getBlazeFlagsState().getRawFlags())
        .containsExactly("--flag1", "--flag2")
        .inOrder();
    assertThat(readState.getExeFlagsState().getRawFlags()).containsExactly("--exeFlag1");
    assertThat(readState.getBlazeBinaryState().getBlazeBinary()).isEqualTo("/usr/bin/blaze");
  }

  @Test
  public void readAndWriteShouldHandleNulls() throws Exception {
    Element element = new Element("test");
    state.writeExternal(element);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.getBuildSystemName(project));
    readState.readExternal(element);

    assertThat(readState.getCommandState().getCommand())
        .isEqualTo(state.getCommandState().getCommand());
    assertThat(readState.getBlazeFlagsState().getRawFlags())
        .isEqualTo(state.getBlazeFlagsState().getRawFlags());
    assertThat(readState.getExeFlagsState().getRawFlags())
        .isEqualTo(state.getExeFlagsState().getRawFlags());
    assertThat(readState.getBlazeBinaryState().getBlazeBinary())
        .isEqualTo(state.getBlazeBinaryState().getBlazeBinary());
  }

  @Test
  public void readShouldOmitEmptyFlags() throws Exception {
    state
        .getBlazeFlagsState()
        .setRawFlags(Lists.newArrayList("hi ", "", "I'm", " ", "\t", "Josh\r\n", "\n"));
    state
        .getExeFlagsState()
        .setRawFlags(Lists.newArrayList("hi ", "", "I'm", " ", "\t", "Josh\r\n", "\n"));

    Element element = new Element("test");
    state.writeExternal(element);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.getBuildSystemName(project));
    readState.readExternal(element);

    assertThat(readState.getBlazeFlagsState().getRawFlags())
        .containsExactly("hi", "I'm", "Josh")
        .inOrder();
    assertThat(readState.getExeFlagsState().getRawFlags())
        .containsExactly("hi", "I'm", "Josh")
        .inOrder();
  }

  @Test
  public void repeatedWriteShouldNotChangeElement() throws Exception {
    final XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());

    state.getCommandState().setCommand(COMMAND);
    state.getBlazeFlagsState().setRawFlags(ImmutableList.of("--flag1", "--flag2"));
    state.getExeFlagsState().setRawFlags(ImmutableList.of("--exeFlag1"));
    state.getBlazeBinaryState().setBlazeBinary("/usr/bin/blaze");

    Element firstWrite = new Element("test");
    state.writeExternal(firstWrite);
    Element secondWrite = firstWrite.clone();
    state.writeExternal(secondWrite);

    assertThat(xmlOutputter.outputString(secondWrite))
        .isEqualTo(xmlOutputter.outputString(firstWrite));
  }

  @Test
  public void editorApplyToAndResetFromShouldMatch() throws Exception {
    RunConfigurationStateEditor editor = state.getEditor(project);

    state.getCommandState().setCommand(COMMAND);
    state.getBlazeFlagsState().setRawFlags(ImmutableList.of("--flag1", "--flag2"));
    state.getExeFlagsState().setRawFlags(ImmutableList.of("--exeFlag1", "--exeFlag2"));
    state.getBlazeBinaryState().setBlazeBinary("/usr/bin/blaze");

    editor.resetEditorFrom(state);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.getBuildSystemName(project));
    editor.applyEditorTo(readState);

    assertThat(readState.getCommandState().getCommand())
        .isEqualTo(state.getCommandState().getCommand());
    assertThat(readState.getBlazeFlagsState().getRawFlags())
        .isEqualTo(state.getBlazeFlagsState().getRawFlags());
    assertThat(readState.getExeFlagsState().getRawFlags())
        .isEqualTo(state.getExeFlagsState().getRawFlags());
    assertThat(readState.getBlazeBinaryState().getBlazeBinary())
        .isEqualTo(state.getBlazeBinaryState().getBlazeBinary());
  }

  @Test
  public void editorApplyToAndResetFromShouldHandleNulls() throws Exception {
    RunConfigurationStateEditor editor = state.getEditor(project);

    editor.resetEditorFrom(state);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.getBuildSystemName(project));
    editor.applyEditorTo(readState);

    assertThat(readState.getCommandState().getCommand())
        .isEqualTo(state.getCommandState().getCommand());
    assertThat(readState.getBlazeFlagsState().getRawFlags())
        .isEqualTo(state.getBlazeFlagsState().getRawFlags());
    assertThat(readState.getExeFlagsState().getRawFlags())
        .isEqualTo(state.getExeFlagsState().getRawFlags());
    assertThat(readState.getBlazeBinaryState().getBlazeBinary())
        .isEqualTo(state.getBlazeBinaryState().getBlazeBinary());
  }
}
