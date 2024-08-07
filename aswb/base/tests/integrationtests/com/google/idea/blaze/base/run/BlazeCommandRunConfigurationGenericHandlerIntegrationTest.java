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
package com.google.idea.blaze.base.run;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration.BlazeCommandRunConfigurationSettingsEditor;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationHandler;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import org.jdom.Element;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for {@link BlazeCommandRunConfiguration} with a {@link
 * BlazeCommandGenericRunConfigurationHandler}.
 */
@RunWith(JUnit4.class)
public class BlazeCommandRunConfigurationGenericHandlerIntegrationTest
    extends BlazeIntegrationTestCase {
  private static final BlazeCommandName COMMAND = BlazeCommandName.fromString("command");

  private BlazeCommandRunConfigurationType type;
  private BlazeCommandRunConfiguration configuration;

  @Before
  public final void doSetup() throws Exception {
    // Without BlazeProjectData, the configuration editor is always disabled.
    BlazeProjectDataManager mockProjectDataManager =
        new MockBlazeProjectDataManager(MockBlazeProjectDataBuilder.builder(workspaceRoot).build());
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);
    type = BlazeCommandRunConfigurationType.getInstance();
    configuration = type.getFactory().createTemplateConfiguration(getProject());
  }

  @Test
  public void testNewConfigurationHasGenericHandler() {
    assertThat(configuration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);
  }

  @Test
  public void testSetTargetNullMakesGenericHandler() {
    configuration.setTarget(null);
    assertThat(configuration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);
  }

  @Test
  public void testTargetExpressionMakesGenericHandler() {
    configuration.setTarget(TargetExpression.fromStringSafe("//..."));
    assertThat(configuration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);
  }

  @Test
  public void testReadAndWriteMatches() throws Exception {
    TargetExpression targetExpression = TargetExpression.fromStringSafe("//...");
    configuration.setTarget(targetExpression);

    BlazeCommandRunConfigurationCommonState state =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
    state.getCommandState().setCommand(COMMAND);
    state.getBlazeFlagsState().setRawFlags(ImmutableList.of("--flag1", "--flag2"));
    state.getExeFlagsState().setRawFlags(ImmutableList.of("--exeFlag1"));
    state.getBlazeBinaryState().setBlazeBinary("/usr/bin/blaze");

    Element element = new Element("test");
    configuration.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(getProject());
    readConfiguration.readExternal(element);

    assertThat(readConfiguration.getTargets()).containsExactly(targetExpression);
    assertThat(readConfiguration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);

    BlazeCommandRunConfigurationCommonState readState =
        (BlazeCommandRunConfigurationCommonState) readConfiguration.getHandler().getState();
    assertThat(readState.getCommandState().getCommand()).isEqualTo(COMMAND);
    assertThat(readState.getBlazeFlagsState().getRawFlags())
        .containsExactly("--flag1", "--flag2")
        .inOrder();
    assertThat(readState.getExeFlagsState().getRawFlags()).containsExactly("--exeFlag1");
    assertThat(readState.getBlazeBinaryState().getBlazeBinary()).isEqualTo("/usr/bin/blaze");
  }

  @Test
  public void testReadAndWriteHandlesNulls() throws Exception {
    Element element = new Element("test");
    configuration.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(getProject());
    readConfiguration.readExternal(element);

    assertThat(readConfiguration.getTargets()).isEqualTo(configuration.getTargets());
    assertThat(readConfiguration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);
  }

  @Test
  public void testEditorApplyToAndResetFromMatches() throws ConfigurationException {
    BlazeCommandRunConfigurationSettingsEditor editor =
        new BlazeCommandRunConfigurationSettingsEditor(configuration);
    TargetExpression targetExpression = TargetExpression.fromStringSafe("//...");
    configuration.setTarget(targetExpression);

    BlazeCommandRunConfigurationCommonState state =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
    state.getCommandState().setCommand(COMMAND);
    state.getBlazeFlagsState().setRawFlags(ImmutableList.of("--flag1", "--flag2"));
    state.getExeFlagsState().setRawFlags(ImmutableList.of("--exeFlag1"));
    state.getBlazeBinaryState().setBlazeBinary("/usr/bin/blaze");

    editor.resetFrom(configuration);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(getProject());
    editor.applyEditorTo(readConfiguration);

    assertThat(readConfiguration.getTargets()).containsExactly(targetExpression);
    assertThat(readConfiguration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);

    BlazeCommandRunConfigurationCommonState readState =
        (BlazeCommandRunConfigurationCommonState) readConfiguration.getHandler().getState();
    assertThat(readState.getCommandState().getCommand())
        .isEqualTo(state.getCommandState().getCommand());
    assertThat(readState.getBlazeFlagsState().getRawFlags())
        .isEqualTo(state.getBlazeFlagsState().getRawFlags());
    assertThat(readState.getExeFlagsState().getRawFlags())
        .isEqualTo(state.getExeFlagsState().getRawFlags());
    assertThat(readState.getBlazeBinaryState().getBlazeBinary())
        .isEqualTo(state.getBlazeBinaryState().getBlazeBinary());

    Disposer.dispose(editor);
  }

  @Test
  public void testEditorApplyToAndResetFromHandlesNulls() throws ConfigurationException {
    BlazeCommandRunConfigurationSettingsEditor editor =
        new BlazeCommandRunConfigurationSettingsEditor(configuration);

    // Call setTarget to initialize a generic handler, or this won't apply anything.
    configuration.setTarget(null);
    assertThat(configuration.getTargets()).isEmpty();
    assertThat(configuration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);
    BlazeCommandRunConfigurationCommonState state =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();

    editor.resetFrom(configuration);

    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(getProject());
    TargetExpression targetExpression = TargetExpression.fromStringSafe("//...");
    readConfiguration.setTarget(targetExpression);

    BlazeCommandRunConfigurationCommonState readState =
        (BlazeCommandRunConfigurationCommonState) readConfiguration.getHandler().getState();
    readState.getCommandState().setCommand(COMMAND);
    readState.getBlazeFlagsState().setRawFlags(ImmutableList.of("--flag1", "--flag2"));
    readState.getExeFlagsState().setRawFlags(ImmutableList.of("--exeFlag1"));
    readState.getBlazeBinaryState().setBlazeBinary("/usr/bin/blaze");

    editor.applyEditorTo(readConfiguration);

    assertThat(readConfiguration.getTargets()).isEmpty();
    assertThat(configuration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);

    readState = (BlazeCommandRunConfigurationCommonState) readConfiguration.getHandler().getState();
    assertThat(readState.getCommandState().getCommand())
        .isEqualTo(state.getCommandState().getCommand());
    assertThat(readState.getBlazeFlagsState().getRawFlags())
        .isEqualTo(state.getBlazeFlagsState().getRawFlags());
    assertThat(readState.getExeFlagsState().getRawFlags())
        .isEqualTo(state.getExeFlagsState().getRawFlags());
    assertThat(readState.getBlazeBinaryState().getBlazeBinary())
        .isEqualTo(state.getBlazeBinaryState().getBlazeBinary());

    Disposer.dispose(editor);
  }
}
