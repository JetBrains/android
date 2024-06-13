/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.kotlin;

import com.android.testutils.TestUtils;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class ConversionTestUtil {

  @NotNull
  public static void changeKotlinVersion(@NotNull GuiTestRule guiTest, @NotNull String gradleFile) {
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    // TODO: the following is a hack. See http://b/79752752 for removal of the hack
    // The Kotlin plugin version chosen is done with a network request. This does not work
    // in an environment where network access is unavailable. We need to handle setting
    // the Kotlin plugin version ourselves temporarily.
    Wait.seconds(20)
      .expecting("Gradle project sync in progress...")
      .until(() ->
               ideFrameFixture.getEditor().open(gradleFile).getCurrentFileContents().contains("kotlin")
      );

    ideFrameFixture.getEditor()
      .open(gradleFile)
      .select("(kotlin = \".*\")")
      .pasteText("kotlin = \"" + TestUtils.KOTLIN_VERSION_FOR_TESTS + "\"");

    guiTest.waitForBackgroundTasks();
    // TODO End hack
  }
}
