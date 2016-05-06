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
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class GenerateJavadocTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void generateJavadoc() throws Exception {
    guiTest.importSimpleApplication()
      .openFromMenu(JavadocDialogFixture::find, "Tools", "Generate JavaDoc...")
      .enterOutputDirectory(javadocDirectory())
      .clickOk()
      .waitForExecutionToFinish();
    assertAbout(file()).that(new File(javadocDirectory(), "index.html")).isFile();
  }

  @NotNull
  private String javadocDirectory() {
    return new File(guiTest.getProjectPath(), "javadoc").getAbsolutePath();
  }
}
