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
package com.android.tools.idea.tests.gui.debugger;

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.components.JBList;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.reflect.core.Reflection.field;

@RunWith(GuiTestRunner.class)
public class SmartStepIntoTest extends DebuggerTestBase {

  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String JAVA_FILE_NAME = "MainActivity.java";
  private static final String NATIVE_FILE_NAME = "native-lib.c";
  private static final int METHODS_COUNT = 2;
  private static final String NATIVE_METHOD_NAME = "stringFromJNI";
  private static final String JAVA_METHOD_NAME = "setMyString";

  /**
   * Verifies that JNI functions can be navigated to from the java definition.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 441ebb05-8c20-4c87-9e08-f531be8d3183
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SmartStepIntoCmakeApp and wait for sync to finish.
   *   2. Set Debugger-> Debug type to “Dual”.
   *   3. Have breakpoint in the line where we have java and native method call in the same line
   *     (setMyString(stringFromJNI());; in the example).
   *   4. Hit the debug button to deploy it in an emulator or device.
   *   5. Once the breakpoint is hit press Shift F7 or Go to Run -> Smart Step into,
   *      and select the native method (Verify 1, 2).
   *   6. Once stepped into just step over couple of times and resume(Verify 3)
   *   Verify:
   *   1. 1. Should show all the method calls in the line (in the github example, it should be
   *      setText and stringFromJNI method calls, check displayed text for each method)
   *   2. Should be able to step into the native method successfully.
   *   3. Should be able to step over through the native code and then successfully resume the app.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testSmartStepIntoNativeMethodWithDualDebugger() throws Exception {
    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("SmartStepIntoCmakeApp");

    ideFrame.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(guiTest.robot())
      .selectDebuggerType("Dual")
      .clickOk();

    openAndToggleBreakPoints(
      ideFrame,
      "app/src/main/java/com/example/basiccmakeapp/" + JAVA_FILE_NAME,
      "setMyString(stringFromJNI());");

    emulator.createDefaultAVD(ideFrame.invokeAvdManager());

    ideFrame.debugApp(DEBUG_CONFIG_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    Wait.seconds(EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS)
      .expecting("Java file should be opened when breakpoint hit")
      .until(() -> (JAVA_FILE_NAME.equals(ideFrame.getEditor().getCurrentFileName())));

    ideFrame.smartStepInto();

    // Verify that there is a popup list, which has two items; actually they are two methods
    // to step into.
    Ref<JBList> out = new Ref<>();
    Wait.seconds(5).expecting("Popup list to show.").until(() -> {
      Collection<JBList> allFound = ideFrame.robot().finder()
        .findAll(ideFrame.target(), Matchers.byType(JBList.class));
      JBList methodsList = null;
      for (JBList jbList : allFound) {
        if (jbList instanceof DataProvider) {
          methodsList = jbList;
        }
      }

      if (methodsList == null) {
        return false;
      }

      out.set(methodsList);
      return true;
    });
    assertThat(out.get()).isNotNull();

    // Get data model by reflection.
    JBList popupList = out.get();
    ListModel model = field("dataModel").ofType(ListModel.class).in(popupList).get();

    // Verify the methods account.
    int methodsCount = model.getSize();
    assertThat(METHODS_COUNT == methodsCount).isTrue();

    // Verify displaying text for each mehtod.
    Set<String> expectedMethodNames = new HashSet<>(Arrays.asList(NATIVE_METHOD_NAME, JAVA_METHOD_NAME));
    int chosenFunctionIndex = -1;
    for (int i = 0; i < methodsCount; i++) {
      final int t = i;
      String wholeText = (ApplicationManager.getApplication().runReadAction(
        (Computable<String>)() -> ((XSmartStepIntoVariant) model.getElementAt(t)).getText()));
      if (wholeText.contains(NATIVE_METHOD_NAME)) {
        chosenFunctionIndex = i; // Choose the native method
      }

      for (String expectedMethodName: expectedMethodNames) {
        if (wholeText.contains(expectedMethodName)) {
          expectedMethodNames.remove(expectedMethodName);
        }
      }
    }
    assertThat(chosenFunctionIndex).isNotEqualTo(-1);

    assertThat(expectedMethodNames).isEmpty();

    // Select the 1st one, which is the native method.
    new JListFixture(ideFrame.robot(), out.get()).clickItem(chosenFunctionIndex);
    
    Wait.seconds(60).expecting("Native file should be opened.")
      .until(() -> (NATIVE_FILE_NAME.equals(ideFrame.getEditor().getCurrentFileName())));

    ideFrame.stepOver();
    String cCurrentLine = ideFrame.getEditor().getCurrentLine().trim();
    String cExpectedLine = "return (*env)->NewStringUTF(env, \"Smart Step Into\");";
    Wait.seconds(10).expecting("Current line in native code is expected").until(() -> (cCurrentLine.equals(cExpectedLine)));

    ideFrame.resumeProgram();
    Wait.seconds(60).expecting("Java file should be opened.")
      .until(() -> (JAVA_FILE_NAME.equals(ideFrame.getEditor().getCurrentFileName())));
    String javaCurrentLine = ideFrame.getEditor().getCurrentLine().trim();
    String javaExpectedLine = "String myString = s;";
    Wait.seconds(10).expecting("Current line in Java code is expected").until(() -> (javaCurrentLine.equals(javaExpectedLine)));
  }
}
