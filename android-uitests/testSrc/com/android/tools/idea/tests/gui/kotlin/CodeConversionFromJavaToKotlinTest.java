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

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import org.fest.swing.fixture.DialogFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CodeConversionFromJavaToKotlinTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  private final static String START_LINE = "// --- START COPY HERE ---";
  private final static String END_LINE = "// --- END COPY HERE ---";
  private final static String JAVA_CODE =
    String.format("(\n\n%s(?:.*\n)*%s)", START_LINE, END_LINE);
  private final static String JAVA_METHOD =
    "\n" +
    "// --- START COPY HERE ---\n" +
    "public int dummyFun(int a, int b) {\n" +
    "   int p = a - b;\n" +
    "   return p;\n" +
    "}\n" +
    "// --- END COPY HERE ---";
  private final static String KOTLIN_FUN =
    "    fun dummyFun(a: Int, b: Int): Int {\n" +
    "        return a - b\n" +
    "    }";
  private final static String FILE_NAME = "app/src/main/java/com/android/javatokotlincode/MainActivity.kt";

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
   *   2. Open Java file, select and copy the predefined Java placeholder function in Java Activity.
   *   NOTE: Don't need above step with predefined Java placeholder function
   *   3. Open Kotlin file, and paste the predefined Java placeholder function.
   *   NOTE: Predefined Java placeholder function is used in kotlin file as after clicking on menu "Edit > Paste > Paste"
   *   menuItem.requestFocus() in MenuFixture.java is failing with NPE as there are multiple Paste available.
   *   Verify:
   *   1. A confirmation dialog should show to convert Java code to kotlin
   *   2. Click on Yes.
   *   3. Java code should convert to Kotlin code.
   *   </pre>
   * <p>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void testConvertJavaCodeToKotlinInEditor() throws Exception {
    IdeFrameFixture ideFrameFixture =
      guiTest.importProjectAndWaitForProjectSyncToFinish("JavaToKotlinCode");

    /*
    ideFrameFixture.getEditor()
      .open("app/src/main/java/com/android/javatokotlincode/Main2Activity.java",
            EditorFixture.Tab.EDITOR)
      .select(JAVA_CODE);
    ideFrameFixture.invokeMenuPath("Edit", "Copy");
    */

    EditorFixture kotlinEditor = ideFrameFixture.getEditor()
      .open(FILE_NAME)
      .moveBetween("setContentView(R.layout.activity_main)\n    }", "");
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
    kotlinEditor.pasteText(JAVA_METHOD);

    DialogFixture convertCodeFromJavaDialog = findDialog(withTitle("Convert Code From Java"))
      .withTimeout(SECONDS.toMillis(90)).using(guiTest.robot());
    convertCodeFromJavaDialog.button(withText("Yes")).click();

    guiTest.robot().waitForIdle();

    String fileContent = ideFrameFixture.getEditor()
      .closeFile(FILE_NAME)
      .open(FILE_NAME)
      .getCurrentFileContents();

    assertThat(fileContent).contains(KOTLIN_FUN);
  }
}
