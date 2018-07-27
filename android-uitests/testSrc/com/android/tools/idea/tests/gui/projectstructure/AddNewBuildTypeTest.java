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
package com.android.tools.idea.tests.gui.projectstructure;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class AddNewBuildTypeTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  @Before
  public void setUp() {
    StudioFlags.NEW_PSD_ENABLED.override(false);
  }

  @After
  public void tearDown() {
    StudioFlags.NEW_PSD_ENABLED.clearOverride();
  }

  /**
   * Verifies addition of new build types
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 532c9d6c-18eb-49ea-99b0-be64dbecd5e1
   * <pre>
   *   Test Steps:
   *   1. Open the project structure dialog
   *   2. Select a module
   *   3. Click the Build Types tab
   *   4. Create new Build Type and name it newBuildType
   *   5. Set properties debuggable and version Name Suffix to valid values
   *   Verification:
   *   1. Open the build.gradle file for that module and verify
   *   entries for build types to contain new build type added.
   *   2. Verify the properties in the file match the values
   *   set in the project structure flavor dialog
   * </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void addNewBuildType() throws Exception {
    String gradleFileContents = guiTest.importSimpleLocalApplication()
                                       .openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
                                       .selectConfigurable("app")
                                       .selectBuildTypesTab()
                                       .setName("newBuildType")
                                       .setDebuggable("true")
                                       .setVersionNameSuffix("suffix")
                                       .clickOk()
                                       .getEditor()
                                       .open("/app/build.gradle")
                                       .getCurrentFileContents();
    assertThat(gradleFileContents)
      .containsMatch("newBuildType \\{\\n[\\s]*debuggable true\\n[\\s]*versionNameSuffix 'suffix'\\n[\\s]*\\}");
  }
}
