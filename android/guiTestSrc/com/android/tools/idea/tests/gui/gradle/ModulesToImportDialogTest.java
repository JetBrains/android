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

import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.project.ModulesToImportDialog;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.CheckBoxListFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FileChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeaDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeaDialogFixture.DialogAndWrapper;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckBoxList;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.DialogFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.AndroidProjectKeys.IDE_GRADLE_PROJECT;
import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture.findByText;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.util.io.FileUtil.createTempFile;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for {@link ModulesToImportDialog}.
 */
public class ModulesToImportDialogTest extends GuiTestCase {
  private List<DataNode<ModuleData>> myModules;
  private DataNode<ModuleData> myProjectModule;
  private DataNode<ModuleData> myAppModule;

  @Before
  public void setUpModules() {
    myModules = Lists.newArrayList();
    myProjectModule = createModule("project", false);
    myModules.add(myProjectModule);

    // Only these 2 modules are Gradle projects.
    myAppModule = createModule("app", true);
    myModules.add(myAppModule);
    myModules.add(createModule("lib", true));
  }

  @NotNull
  private static DataNode<ModuleData> createModule(@NotNull String name, boolean isGradleProject) {
    String path = "~/project/" + name;
    ModuleData data = new ModuleData(name, GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), name, path, path);
    DataNode<ModuleData> module = new DataNode<ModuleData>(MODULE, data, null);
    if (isGradleProject) {
      List<String> taskNames = Collections.emptyList();
      module.createChild(IDE_GRADLE_PROJECT, new IdeaGradleProject("app", taskNames, ":" + name, null));
    }
    return module;
  }

  @Test @IdeGuiTest
  public void testModuleSelection() throws IOException {
    DialogAndWrapper<ModulesToImportDialog> dialogAndWrapper = launchDialog();
    JDialog dialog = dialogAndWrapper.dialog;

    //noinspection unchecked
    CheckBoxList<DataNode<ModuleData>> list = myRobot.finder().findByType(dialog, CheckBoxList.class, true);
    CheckBoxListFixture moduleList = new CheckBoxListFixture<DataNode<ModuleData>>(myRobot, list);

    // Verify that only modules that are Gradle projects are in the list.
    assertThat(moduleList.contents()).containsOnly("app", "lib");

    // Verify that all elements are checked.
    assertThat(moduleList.getCheckedItems()).contains("app", "lib");

    // Verify that only returns the elements that are checked.
    moduleList.setItemChecked("lib", false);
    Collection<DataNode<ModuleData>> selectedModules = dialogAndWrapper.wrapper.getSelectedModules();
    assertThat(selectedModules).containsOnly(myProjectModule, myAppModule);

    // Save selection to disk
    File tempFile = createTempFile("selection", ".xml", true);
    VirtualFile targetFile = findFileByIoFile(tempFile, true);
    assertNotNull(targetFile);

    findByText("Save Selection As", myRobot, dialog).click();
    FileChooserDialogFixture fileChooser = FileChooserDialogFixture.findDialog(myRobot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(JDialog dialog) {
        return dialog.isShowing() && "Save Module Selection".equals(dialog.getTitle());
      }
    });
    fileChooser.select(targetFile).clickOk();

    DialogFixture confirmDialog = findDialog(withTitle("Confirm Save as")).withTimeout(SHORT_TIMEOUT.duration()).using(myRobot);
    confirmDialog.button(withText("Yes")).click();

    // Load selection from disk
    findByText("Select All", myRobot, dialog).click();
    findByText("Load Selection from File", myRobot, dialog).click();
    fileChooser = FileChooserDialogFixture.findDialog(myRobot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(JDialog dialog) {
        return dialog.isShowing() && "Load Module Selection".equals(dialog.getTitle());
      }
    });
    fileChooser.select(targetFile).clickOk();

    selectedModules = dialogAndWrapper.wrapper.getSelectedModules();
    assertThat(selectedModules).containsOnly(myProjectModule, myAppModule);
  }

  public DialogAndWrapper<ModulesToImportDialog> launchDialog() {
    final ModulesToImportDialog dialog = GuiActionRunner.execute(new GuiQuery<ModulesToImportDialog>() {
      @Override
      protected ModulesToImportDialog executeInEDT() throws Throwable {
        return new ModulesToImportDialog(myModules, null);
      }
    });

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        dialog.setModal(false);
        dialog.show();
      }
    });

    return IdeaDialogFixture.find(myRobot, ModulesToImportDialog.class, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(JDialog dialog) {
        return "Select Modules to Include".equals(dialog.getTitle()) && dialog.isShowing();
      }
    });
  }
}
