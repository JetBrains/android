/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.ui.components.JBList;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class QuickFixForJniTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  private static final String JAVA_FILE = "app/src/main/java/com/example/hellojni/HelloJni.java";

  /**
   * Verifies that Quick fix for adding missing JNI implementation.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 84f148ca-c8a4-4d11-8fa1-10931ed07cfa
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import CLionNdkHelloJni project and wait for sync to finish
   *   2. Open Java file and enter public native void printFromJNI();
   *   3. Inspect code and verify 1
   *   4. Move the cursor on the printFromJNI function name, press Alt + Enter to trigger
   *      a quick fix for the missing JNI implementation error, Verify 2
   *   5. Click on the fix for “Create JNI function for …”, Verify 3
   *   6. Click "jni.cpp". Verify 4
   *   7. Navigate to the external method on the Java/Kotlin side, Verify 5
   *   Verify:
   *   1. The method/function should be highlighted as red due to missing JNI implementation
   *   2. There should be a quick fix for the missing JNI implementation
   *   3. The editor should show a popup asking which native source file to place the JNI stub
   *   4. The editor should navigate to the C/C++ file; a skeleton implementation for
   *      your native method should be added at the bottom of the C/C++ file
   *   5. The method should no longer be red
   *   </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void quickFix() throws Exception {
    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("debugger/CLionNdkHelloJni");
    guiTest.waitForBackgroundTasks();

    // Open Java file and check errors.
    EditorFixture editor = ideFrame.getEditor().open(JAVA_FILE);

    editor.moveBetween("public native String  stringFromJNI();", "")
      .enterText("\npublic native void printFromJNI();");

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.waitAndInvokeMenuPath("Code", "Inspect Code...");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    InspectCodeDialogFixture inspectCodeDialog = InspectCodeDialogFixture.find(ideFrame);
    inspectCodeDialog.clickAnalyze();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame.requestFocusIfLost();

    List<String> errors = editor.getHighlights(HighlightSeverity.ERROR);
    assertThat(errors).hasSize(1);

    // Trigger Quick fix.
    editor.select("void printFromJNI()")
      .invokeAction(EditorFixture.EditorAction.SHOW_INTENTION_ACTIONS);

    JBList quickFixPopup = GuiTests.waitUntilShowingAndEnabled(guiTest.robot(),
                                                               null, new GenericTypeMatcher<JBList>(JBList.class) {
        @Override
        protected boolean isMatching(@NotNull JBList list) {
          return list.getClass().getName().equals("com.intellij.ui.popup.list.ListPopupImpl$MyList");
        }
      });

    clickOnItemInPopup(quickFixPopup, "Create JNI function for printFromJNI");

    // Create fixture for the second popup
    JBList nativeSourcePopup = GuiTests.waitUntilShowingAndEnabled(guiTest.robot(),
                                                                   null, new GenericTypeMatcher<JBList>(JBList.class) {
        @Override
        protected boolean isMatching(@NotNull JBList list) {
          return list.getClass().getName().equals(JBList.class.getName());
        }
      });
    JListFixture nativeSourcePopupFixture = new JListFixture(guiTest.robot(), nativeSourcePopup);

    // Verify the content of the popup menu. It should contain both native source files in this project and
    // place the file that has existing JNI definitions (hello-jni.c) on top.
    nativeSourcePopupFixture.requireItemCount(2);
    assertThat(nativeSourcePopupFixture.item(0).value()).isEqualTo("hello-jni.c");
    assertThat(nativeSourcePopupFixture.item(1).value()).isEqualTo("jni.cpp");
    nativeSourcePopupFixture.item(1).click();

    // Check skeleton added to C/C++ file.
    Wait.seconds(10).expecting("Native file is opened for navigating to definition")
      .until(() -> "jni.cpp".equals(ideFrame.getEditor().getCurrentFileName()));

    String currentLine = ideFrame.getEditor().getCurrentLine();
    assertThat(currentLine.contains("// TODO: implement printFromJNI()")).isTrue();

    // Check no red in Java file.
    editor = ideFrame.getEditor().open(JAVA_FILE);
    errors = editor.getHighlights(HighlightSeverity.ERROR);
    assertThat(errors).hasSize(0);
  }

  private void clickOnItemInPopup(JBList quickFixPopup, String popupToBeSelected) {
    JListFixture quickFixPopupFixture = new JListFixture(guiTest.robot(), quickFixPopup);
    //Get the index of item in the JBList model:
    int indexOfExpectedString = Arrays.stream(quickFixPopupFixture.contents()).toList().indexOf(popupToBeSelected);
    if (indexOfExpectedString == -1) {
      Assert.fail("Expected pop-up '" + popupToBeSelected + "' is not displayed"); //Fail the test if item not found in the pop-up
    }
    //Navigate to the item in the pup-up using keyboard
    for(int i = 0; i < indexOfExpectedString; i++) {
      guiTest.robot().pressAndReleaseKey(KeyEvent.VK_DOWN);
    }
    quickFixPopupFixture.requireSelectedItems(popupToBeSelected); //Verify the item before clicking on it
    quickFixPopupFixture.clickItem(popupToBeSelected);
  }
}
