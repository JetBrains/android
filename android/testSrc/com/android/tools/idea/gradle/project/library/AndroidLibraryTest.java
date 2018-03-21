// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.project.library;

import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate;
import com.android.tools.idea.npw.project.AndroidPackageUtils;
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep;
import com.android.tools.idea.npw.template.RenderTemplateModel;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.observable.BatchInvoker;
import com.android.tools.idea.observable.TestInvokeStrategy;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.google.common.io.Files;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.facet.AndroidFacet;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB;
import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.truth.Truth.assertThat;

public class AndroidLibraryTest extends AndroidGradleTestCase {
  private static TestInvokeStrategy myInvokeStrategy;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myInvokeStrategy = new TestInvokeStrategy();
    BatchInvoker.setOverrideStrategy(myInvokeStrategy);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      super.tearDown();
    }
    finally {
      BatchInvoker.clearOverrideStrategy();
    }
  }

  // b/68150753
  public void testRenameLibraryAndAddActivity() throws Exception {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project project = myAndroidFacet.getModule().getProject();
    Module libModule = getModule("lib");
    AndroidFacet libAndroidFacet = AndroidFacet.getInstance(libModule);

    File libModuleDir = new File(libModule.getModuleFilePath()).getParentFile();
    File origLibBuildFile = new File(libModuleDir, FN_BUILD_GRADLE);
    File settingsGradle = new File(project.getBasePath(), FN_SETTINGS_GRADLE);

    // Rename the lib file name, by updating 'settings.gradle' and renaming the file itself.
    Files.append(System.lineSeparator() + "project(':lib').buildFileName = 'mylibrary.gradle'", settingsGradle, UTF_8);
    assertThat(origLibBuildFile.exists()).isTrue();
    assertThat(origLibBuildFile.renameTo(new File(libModuleDir, "mylibrary.gradle"))).isTrue();
    requestSyncAndWait();

    // Create a Wizard and add an Activity to the lib module
    TemplateHandle myTemplateHandle = new TemplateHandle(TemplateManager.getInstance().getTemplateFile("Activity", "Empty Activity"));
    NamedModuleTemplate template = GradleAndroidModuleTemplate.createDefaultTemplateAt(new File(project.getProjectFilePath()));
    RenderTemplateModel render = new RenderTemplateModel(libModule, myTemplateHandle, "com.example", template, "command");

    List<NamedModuleTemplate> moduleTemplates = AndroidPackageUtils.getModuleTemplates(libAndroidFacet, null);
    assertThat(moduleTemplates).isNotEmpty();

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    wizardBuilder.addStep(new ConfigureTemplateParametersStep(render, "Add new Activity Test", moduleTemplates, libAndroidFacet));
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
