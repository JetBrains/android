/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.subset.ModulesToImportDialog;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ModulesToImportDialogFixture;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.util.io.FileUtil.createTempFile;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static java.util.UUID.randomUUID;

/**
 * Tests for {@link ModulesToImportDialog}.
 */
@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class ModulesToImportDialogTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private List<DataNode<ModuleData>> myModules;

  @Before
  public void setUpModules() {
    myModules = Lists.newArrayList();
    DataNode<ModuleData> projectModule = createModule("project", false);
    myModules.add(projectModule);

    // Only these 2 modules are Gradle projects.
    DataNode<ModuleData> appModule = createModule("app", true);
    myModules.add(appModule);
    DataNode<ModuleData> libModule = createModule("lib", true);
    myModules.add(libModule);
  }

  @NotNull
  private static DataNode<ModuleData> createModule(@NotNull String name, boolean isGradleProject) {
    String path = "~/project/" + name;
    ModuleData data = new ModuleData(name, GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), name, path, path);
    DataNode<ModuleData> module = new DataNode<>(MODULE, data, null);
    if (isGradleProject) {
      List<String> taskNames = Collections.emptyList();
      module.createChild(GRADLE_MODULE_MODEL, new GradleModuleModel("app", taskNames, ":" + name, null, null));
    }
    return module;
  }

  @Test
  public void testModuleSelection() throws IOException {
    ModulesToImportDialogFixture dialog = launchDialog();

    // Verify that only modules that are Gradle projects are in the list.
    assertThat(dialog.getModuleList()).containsExactly("app", "lib");

    assertThat(dialog.getSelectedModuleList()).containsExactly("app", "lib");

    dialog.setSelected("lib", false);
    assertThat(dialog.getSelectedModuleList()).containsExactly("app");

    File tempFile = createTempFile(randomUUID().toString(), ".xml", true);
    VirtualFile targetFile = findFileByIoFile(tempFile, true);
    dialog.saveSelectionToFile(targetFile);

    dialog.loadSelectionFromFile(targetFile);

    assertThat(dialog.getSelectedModuleList()).containsExactly("app");
    dialog.clickCancel();
  }

  @Test
  public void testQuickSearch() {
    ModulesToImportDialogFixture dialog = launchDialog();
    dialog.doQuickSearch("lib")
      .getModuleTable()
      .requireSelectedRows(1);
    dialog.clickCancel();
  }

  @NotNull
  public ModulesToImportDialogFixture launchDialog() {
    ModulesToImportDialog dialog = GuiQuery.getNonNull(() -> new ModulesToImportDialog(myModules, null));

    ApplicationManager.getApplication().invokeLater(
      () -> {
        dialog.setModal(false);
        dialog.show();
      });

    return ModulesToImportDialogFixture.find(guiTest.robot());
  }
}
