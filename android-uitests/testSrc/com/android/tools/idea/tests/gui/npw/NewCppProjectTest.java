/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.npw;

import com.android.tools.idea.npw.cpp.CppStandardType;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class NewCppProjectTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Creates a new project with c++, and checks that if we run Analyze > Inspect Code, there are no warnings.
   * This checks that our (default) project templates are warnings-clean.
   * The test then proceeds to make a couple of edits and checks that these do not generate additional warnings either.
   */
  @Test
  public void noWarningsInNewProjectWithCpp() throws Exception {
    NewCppProjectTestUtil.createCppProject(CppStandardType.DEFAULT, guiTest);

    String inspectionResults = guiTest.ideFrame()
      .openFromMenu(InspectCodeDialogFixture::find, "Analyze", "Inspect Code...")
      .clickOk()
      .getResults();

    // TODO Disable spell checking before running inspection, then compare for equality like noWarningsInNewProjects does.
    // These two strings are categories that are very common when there will be some problem compiling.
    assertThat(inspectionResults).doesNotContain("Declaration order");
    assertThat(inspectionResults).doesNotContain("Unused code");

    // This string happens when the JniMissingFunctionInspection unexpectedly fires.
    assertThat(inspectionResults).doesNotContain("Cannot resolve corresponding JNI");
  }
}
