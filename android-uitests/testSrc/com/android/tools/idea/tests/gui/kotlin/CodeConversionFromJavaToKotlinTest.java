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
package com.android.tools.idea.tests.gui.kotlin;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.fest.swing.fixture.DialogFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;

@RunWith(GuiTestRunner.class)
public class CodeConversionFromJavaToKotlinTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private final static String START_LINE = "// --- START COPY HERE ---";
  private final static String END_LINE = "// --- END COPY HERE ---";
  private final static String JAVA_CODE =
    String.format("(\n\n%s(?:.*\n)*%s)", START_LINE, END_LINE);
  private final static String KOTLIN_FUN =
    "    fun dummyFun(a: Int, b: Int): Int {\n" +
    "        val p = a - b\n" +
    "        return p\n" +
    "    }";

  /**
   * Verifies Copy pasting a block of code from java file to kotlin file converts the code to
   * kotlin code.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 70a836da-ed71-4782-a4b8-c0de7a3d2c48
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import JavaToKotlinCode project and wait for project sync to finish.
   *   2. Open Java file, select and copy the predefined Java dummy function in Java Activity.
   *   3. Open Kotlin file, and paste the copied Java dummy function.
   *   Verify:
   *   1. A confirmation dialog should show to convert Java code to kotlin
   *   2. Click on Yes.
   *   3. Java code should convert to Kotlin code.
   *   </pre>
   * <p>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testConvertJavaCodeToKotlinInEditor() throws Exception {
    IdeFrameFixture ideFrameFixture =
      guiTest.importProjectAndWaitForProjectSyncToFinish("JavaToKotlinCode");

    ideFrameFixture.getEditor()
      .open("app/src/main/java/com/android/javatokotlincode/Main2Activity.java",
            EditorFixture.Tab.EDITOR)
      .select(JAVA_CODE);
    ideFrameFixture.invokeMenuPath("Edit", "Copy");

    EditorFixture kotlinEditor = ideFrameFixture.getEditor()
      .open("app/src/main/java/com/android/javatokotlincode/MainActivity.kt",
            EditorFixture.Tab.EDITOR)
      .moveBetween("setContentView(R.layout.activity_main)\n    }", "");
    ideFrameFixture.invokeMenuPath("Edit", "Paste");

    DialogFixture convertCodeFromJavaDialog = findDialog(withTitle("Convert Code From Java"))
      .withTimeout(SECONDS.toMillis(30)).using(guiTest.robot());
    convertCodeFromJavaDialog.button(withText("Yes")).click();

    kotlinEditor.getCurrentFileContents().contains(KOTLIN_FUN);
  }
}
