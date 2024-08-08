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
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeCommandRunConfiguration.BlazeCommandRunConfigurationSettingsEditor}. */
@RunWith(JUnit4.class)
public class BlazeCommandRunConfigurationSettingsEditorTest extends BlazeIntegrationTestCase {

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
  public void testEditorApplyToAndResetFromMatches() throws ConfigurationException {
    BlazeCommandRunConfigurationSettingsEditor editor =
        new BlazeCommandRunConfigurationSettingsEditor(configuration);
    Label label = Label.create("//package:rule");
    configuration.setTarget(label);

    editor.resetFrom(configuration);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(getProject());
    editor.applyEditorTo(readConfiguration);

    assertThat(readConfiguration.getTargets()).containsExactly(label);

    Disposer.dispose(editor);
  }

  @Test
  public void testEditorApplyToAndResetFromHandlesNulls() throws ConfigurationException {
    BlazeCommandRunConfigurationSettingsEditor editor =
        new BlazeCommandRunConfigurationSettingsEditor(configuration);

    editor.resetFrom(configuration);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(getProject());
    editor.applyEditorTo(readConfiguration);

    assertThat(readConfiguration.getTargets()).isEqualTo(configuration.getTargets());

    Disposer.dispose(editor);
  }
}
