/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.project;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APP_AND_LIB_DEPENDENCY;
import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.NEW_MODULE;

import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate;
import com.android.tools.idea.npw.model.ProjectSyncInvoker;
import com.android.tools.idea.npw.model.RenderTemplateModel;
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep;
import com.android.tools.idea.npw.template.TemplateResolver;
import com.android.tools.idea.observable.BatchInvoker;
import com.android.tools.idea.observable.TestInvokeStrategy;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.template.Template;
import com.google.common.io.Files;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;

public class AndroidLibraryTest extends AndroidGradleTestCase {
  private final TestInvokeStrategy myInvokeStrategy = new TestInvokeStrategy();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    BatchInvoker.setOverrideStrategy(myInvokeStrategy);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      BatchInvoker.clearOverrideStrategy();
    }
    finally {
      super.tearDown();
    }
  }

  // b/68150753
  public void testRenameLibraryAndAddActivity() throws Exception {
    loadProject(PROJECT_WITH_APP_AND_LIB_DEPENDENCY);

    Project project = myAndroidFacet.getModule().getProject();
    Module libModule = getModule("lib");
    AndroidFacet libAndroidFacet = AndroidFacet.getInstance(libModule);

    File libModuleDir = AndroidRootUtil.findModuleRootFolderPath(libModule);
    File origLibBuildFile = new File(libModuleDir, FN_BUILD_GRADLE);
    File settingsGradle = new File(project.getBasePath(), FN_SETTINGS_GRADLE);

    // Rename the lib file name, by updating 'settings.gradle' and renaming the file itself.
    Files.append(System.lineSeparator() + "project(':lib').buildFileName = 'mylibrary.gradle'", settingsGradle, StandardCharsets.UTF_8);
    assertThat(origLibBuildFile.exists()).isTrue();
    assertThat(origLibBuildFile.renameTo(new File(libModuleDir, "mylibrary.gradle"))).isTrue();
    requestSyncAndWait();

    // Create a Wizard and add an Activity to the lib module
    NamedModuleTemplate template = GradleAndroidModuleTemplate.createDefaultModuleTemplate(project, "");
    RenderTemplateModel render = RenderTemplateModel.fromFacet(
      libAndroidFacet, "com.example", template, "command", new ProjectSyncInvoker.DefaultProjectSyncInvoker(), true,
      NEW_MODULE
    );
    List<Template> templates = TemplateResolver.Companion.getAllTemplates();
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    Template emptyActivityTemplate = templates.stream().filter(t -> "Empty Activity".equals(t.getName())).findFirst().get();
    // Template is set to "No Activity" by default. Wizard step will be not displayed in this case, so we use empty activity instead.
    render.setNewTemplate(emptyActivityTemplate);

    List<NamedModuleTemplate> moduleTemplates = AndroidPackageUtils.getModuleTemplates(libAndroidFacet, null);
    assertThat(moduleTemplates).isNotEmpty();

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    wizardBuilder.addStep(new ConfigureTemplateParametersStep(render, "Add new Activity Test", moduleTemplates));
    ModelWizard modelWizard = wizardBuilder.build();
    Disposer.register(project, modelWizard);
    myInvokeStrategy.updateAllSteps();

    modelWizard.goForward();
    myInvokeStrategy.updateAllSteps();

    requestSyncAndWait();

    // Adding a new Activity should add its dependencies to the renamed build file (not re-create the original one)
    assertThat(origLibBuildFile.exists()).isFalse();
  }
}
