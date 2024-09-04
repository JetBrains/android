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

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration.BlazeCommandRunConfigurationSettingsEditor;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.util.Disposer;
import java.util.List;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for {@link BlazeCommandRunConfiguration} saved by {@link
 * com.intellij.execution.impl.RunManagerImpl}.
 */
@RunWith(JUnit4.class)
public class BlazeCommandRunConfigurationRunManagerImplTest extends BlazeIntegrationTestCase {

  private RunManagerImpl runManager;
  private Element defaultRunManagerState;
  private BlazeCommandRunConfiguration configuration;

  @Before
  public final void doSetup() {
    runManager = RunManagerImpl.getInstanceImpl(getProject());
    defaultRunManagerState = runManager.getState();
    // Without BlazeProjectData, the configuration editor is always disabled.
    BlazeProjectDataManager mockProjectDataManager =
        new MockBlazeProjectDataManager(MockBlazeProjectDataBuilder.builder(workspaceRoot).build());
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);
    BlazeCommandRunConfigurationType type = BlazeCommandRunConfigurationType.getInstance();

    RunnerAndConfigurationSettings runnerAndConfigurationSettings =
        runManager.createConfiguration("Blaze Configuration", type.getFactory());
    runnerAndConfigurationSettings.storeInLocalWorkspace();
    runManager.addConfiguration(runnerAndConfigurationSettings);
    configuration =
        (BlazeCommandRunConfiguration) runnerAndConfigurationSettings.getConfiguration();
  }

  @After
  public final void doTeardown() {
    runManager.clearAll();
    runManager.loadState(defaultRunManagerState);
    // We don't need to do this at setup, because it is handled by RunManagerImpl's constructor.
    // However, clearAll() clears the configuration types, so we need to reinitialize them.
    runManager.initializeConfigurationTypes(
        ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList());
  }

  @Test
  public void loadStateAndGetStateShouldMatch() {
    final Label label = Label.create("//package:rule");
    configuration.setTarget(label);

    final Element element = runManager.getState();
    runManager.loadState(element);
    final List<RunConfiguration> configurations = runManager.getAllConfigurationsList();
    assertThat(configurations).hasSize(1);
    assertThat(configurations.get(0)).isInstanceOf(BlazeCommandRunConfiguration.class);
    final BlazeCommandRunConfiguration readConfiguration =
        (BlazeCommandRunConfiguration) configurations.get(0);

    assertThat(readConfiguration.getTargets()).containsExactly(label);
  }

  @Test
  public void loadStateAndGetStateElementShouldMatch() {
    final XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());
    configuration.setTarget(Label.create("//package:rule"));

    final Element initialElement = runManager.getState();
    runManager.loadState(initialElement);
    final Element newElement = runManager.getState();

    assertThat(xmlOutputter.outputString(newElement))
        .isEqualTo(xmlOutputter.outputString(initialElement));
  }

  @Test
  public void loadStateAndGetStateElementShouldMatchAfterChangeAndRevert() {
    final XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());
    final Label label = Label.create("//package:rule");
    configuration.setTarget(label);

    final Element initialElement = runManager.getState();
    runManager.loadState(initialElement);
    final BlazeCommandRunConfiguration modifiedConfiguration =
        (BlazeCommandRunConfiguration) runManager.getAllConfigurationsList().get(0);
    modifiedConfiguration.setTarget(Label.create("//new:label"));

    final Element modifiedElement = runManager.getState();
    assertThat(xmlOutputter.outputString(modifiedElement))
        .isNotEqualTo(xmlOutputter.outputString(initialElement));
    runManager.loadState(modifiedElement);
    final BlazeCommandRunConfiguration revertedConfiguration =
        (BlazeCommandRunConfiguration) runManager.getAllConfigurationsList().get(0);
    revertedConfiguration.setTarget(label);

    final Element revertedElement = runManager.getState();
    assertThat(xmlOutputter.outputString(revertedElement))
        .isEqualTo(xmlOutputter.outputString(initialElement));
  }

  @Test
  public void getStateElementShouldMatchAfterEditorApplyToAndResetFrom() {
    final XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());
    final BlazeCommandRunConfigurationSettingsEditor editor =
        new BlazeCommandRunConfigurationSettingsEditor(configuration);
    configuration.setTarget(Label.create("//package:rule"));

    final Element initialElement = runManager.getState();
    editor.resetFrom(configuration);
    editor.applyEditorTo(configuration);
    final Element newElement = runManager.getState();

    assertThat(xmlOutputter.outputString(newElement))
        .isEqualTo(xmlOutputter.outputString(initialElement));

    Disposer.dispose(editor);
  }
}
