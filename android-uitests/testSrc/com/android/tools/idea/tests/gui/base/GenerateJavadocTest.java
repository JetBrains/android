/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.base;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.JavadocDialogFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class GenerateJavadocTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verify that JavaDoc can be generated.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: def7117b-080f-45b8-9594-d5f918690c4f
   * <p>
   *   <pre>
   *   Steps:
   *   1. Import a simple app.
   *   2. Click on Tools > Generate JavaDoc
   *   3. Enter an output directory.
   *   4. Deselect “Open generated documentation in browser”.
   *   5. Click OK.
   *   Verify:
   *   JavaDoc files are generated in the selected output directory.
   *   </pre>
   */
  @Test
  public void generateJavadoc() throws Exception {
    guiTest.importSimpleApplication()
      .openFromMenu(JavadocDialogFixture::find, "Tools", "Generate JavaDoc...")
      .enterOutputDirectory(guiTest.getProjectPath("javadoc").getAbsolutePath())
      .deselectOpenInBrowser()
      .clickOk()
      .waitForExecutionToFinish();
    assertAbout(file()).that(guiTest.getProjectPath("javadoc/index.html")).isFile();
  }
}
