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
package com.android.tools.idea.tests.gui.cpp;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.debugger.DebuggerTestBase;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ShortcutNavigationTest extends DebuggerTestBase {

  @Rule public GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  /**
   * Verifies that JNI functions can be navigated to from the java definition.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 441ebb05-8c20-4c87-9e08-f531be8d3183
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import NdkHelloJni and wait for sync to finish.
   *   2. Left click on the 'stringFromJNI' part of public native String stringFromJNI().
   *   3. Press ctrl + b (windows and linux) or cmd + b (mac) (Verify)..
   *   Verify:
   *   1. Verify that the editor jumped to "hello-jni.c" with the cursor at
   *      "Java_com_example_hellojni_HelloJni_stringFromJNI" (the native implementation).
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testShortcutNavigateFromJavaDefinitionToJniFunction() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("debugger/NdkHelloJni");
    ideFrame.waitUntilProgressBarNotDisplayed();
    EditorFixture editor = ideFrame.getEditor().open("app/src/main/java/com/example/hellojni/HelloJni.java");
    editor.waitForFileToActivate();
    editor.select("String  stringFromJNI()")
      .invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION);

    Wait.seconds(30).expecting("Native file is opened for navigating to definition")
      .until(() -> "hello-jni.c".equals(ideFrame.getEditor().getCurrentFileName()));
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    String currentLine = editor.getCurrentLine();
    assertThat(currentLine).isEqualTo("Java_com_example_hellojni_HelloJni_stringFromJNI( JNIEnv* env,\n");
  }
}
