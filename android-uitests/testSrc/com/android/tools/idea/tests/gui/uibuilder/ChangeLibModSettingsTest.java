/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.uibuilder;

import static com.android.tools.idea.tests.gui.framework.fixture.newpsd.ModulesPerspectiveConfigurableFixtureKt.selectModulesConfigurable;
import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.core.MouseButton.RIGHT_BUTTON;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.ModulePropertiesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.ModulesPerspectiveConfigurableFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.ProjectStructureDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ChangeLibModSettingsTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  /**
   * Verify module properties can be modified.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 42c3202c-f2aa-4080-b94a-1a17d56d8fe2
   * <p>
   *   <pre>
   *   Steps:
   *   1. Create a new project.
   *   2. Create a new library module and an application module.
   *   3. Right click on the library module and select Change Module Settings.
   *   4. Make a few changes to the properties, like build tools version.
   *   5. Repeat with application module
   *   Verify:
   *   1. Module setting is updated.
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void changeLibraryModuleSettings() {
    new NewProjectDescriptor("MyTestApp").withMinSdk(24).create(guiTest)
      .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module\u2026")
      .clickNextToAndroidLibrary()
      .enterModuleName("library_module")
      .wizard()
      .clickFinishAndWaitForSyncToFinish(Wait.seconds(30));

    guiTest.waitForBackgroundTasks();

    guiTest.ideFrame().focus();
    guiTest.ideFrame().getProjectView()
      .selectProjectPane()
      .clickPath(RIGHT_BUTTON, "MyTestApp", "library_module");

    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();
    guiTest.robot().findActivePopupMenu();

    guiTest.ideFrame().invokeMenuPath("Open Module Settings");

    ProjectStructureDialogFixture dialogFixture = ProjectStructureDialogFixture.Companion.find(guiTest.ideFrame());

    ModulesPerspectiveConfigurableFixture modulesConfigurable = selectModulesConfigurable(dialogFixture);
    ModulePropertiesFixture propertiesTab = modulesConfigurable.selectPropertiesTab();
    propertiesTab.compileSdkVersion().enterText("28");
    propertiesTab.sourceCompatibility().selectItem("1.7 (Java 7)");
    propertiesTab.targetCompatibility().selectItem("1.7 (Java 7)");
    dialogFixture.clickOk();

    String gradleFileContents = guiTest.ideFrame()
      .getEditor()
      .open("/library_module/build.gradle.kts")
      .getCurrentFileContents();

    assertThat(gradleFileContents).contains("compileSdk = 28");
    // TODO(b/136748446): Review and re-enable if necessary.
    //assertThat(gradleFileContents).contains("aaptOptions {\n        ignoreAssetsPattern 'TestIgnoreAssetsPattern'\n    }");
    //assertThat(gradleFileContents).contains("dexOptions {\n        incremental false\n    }");
    assertThat(gradleFileContents).contains(
      "compileOptions {\n        sourceCompatibility = JavaVersion.VERSION_1_7\n        targetCompatibility = JavaVersion.VERSION_1_7\n    }");
  }
}
