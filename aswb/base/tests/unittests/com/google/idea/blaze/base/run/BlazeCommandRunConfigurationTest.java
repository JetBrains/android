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

import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.project.Project;
import java.util.concurrent.Future;
import org.jdom.Element;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeCommandRunConfiguration}. */
@RunWith(JUnit4.class)
public class BlazeCommandRunConfigurationTest extends BlazeTestCase {
  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", BuildSystemName.Blaze, ProjectType.ASPECT_SYNC);

  private final BlazeCommandRunConfigurationType type = new BlazeCommandRunConfigurationType();
  private BlazeCommandRunConfiguration configuration;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);

    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    applicationServices.register(ExperimentService.class, new MockExperimentService());
    registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());

    ExtensionPointImpl<TargetFinder> targetFinderEp =
        registerExtensionPoint(TargetFinder.EP_NAME, TargetFinder.class);
    targetFinderEp.registerExtension(new MockTargetFinder());

    ExtensionPointImpl<BlazeCommandRunConfigurationHandlerProvider> handlerProviderEp =
        registerExtensionPoint(
            BlazeCommandRunConfigurationHandlerProvider.EP_NAME,
            BlazeCommandRunConfigurationHandlerProvider.class);
    handlerProviderEp.registerExtension(new MockBlazeCommandRunConfigurationHandlerProvider());

    this.configuration = this.type.getFactory().createTemplateConfiguration(project);
  }

  @Test
  public void readAndWriteShouldMatch() throws Exception {
    Label label = Label.create("//package:rule");
    configuration.setTarget(label);

    Element element = new Element("test");
    configuration.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(project);
    readConfiguration.readExternal(element);

    assertThat(readConfiguration.getTargets()).containsExactly(label);
  }

  @Test
  public void readAndWriteShouldHandleNulls() throws Exception {
    Element element = new Element("test");
    configuration.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(project);
    readConfiguration.readExternal(element);

    assertThat(readConfiguration.getTargets()).isEqualTo(configuration.getTargets());
  }

  private static class MockTargetFinder implements TargetFinder {
    @Override
    public Future<TargetInfo> findTarget(Project project, Label label) {
      return Futures.immediateFuture(null);
    }
  }
}
