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
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith (GuiTestRunner.class)
public class CppCompilingTest extends DebuggerTestBase {

  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private final String AUTO = "Auto";
  private final String DEBUGER_CONF_NAME = "app";
  private final String JAVA_DEBUGER_CONF_NAME = "app-java";

  private final String ANDROID_STL = "-DANDROID_STL";
  private final String CPP_STATIC = "c++_static";
  private final String CPP_SHARED = "c++_shared";
  private final String GNUSTL_STATIC = "gnustl_static";
  private final String GNUSTL_SHARED = "gnustl_shared";
  private final String CLANG_TOOLCHAIN = "-DANDROID_TOOLCHAIN=clang";
  private final String GCC_TOOLCHAIN = "-DANDROID_TOOLCHAIN=gcc";

  /**
   * Verifies C++ compilation works normally.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 6320cd51-cbe8-4b25-a94e-24e6dc7d25cb
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI and wait for sync to finish.
   *   2. Create an AVD with x86 API 24 or above.
   *   3. Select Auto debugger on Edit Configurations dialog.
   *   4. Open "build.gradle" in android studio, add "arguments "-DANDROID_STL=cpp_static",
   *      "-DANDROID_TOOLCHAIN=gcc""
   *   5. Set breakpoints in C++ and Java code.
   *   6. Debug on the AVD created above.
   *   7. When the Java breakpoint is hit verify call stack and variables and press F9 to resume
   *   8. When the C++ breakpoint is hit verify call stack and variables and press F9 to resume.
   *   9. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testGccCppStatic() throws Exception {
    processToTestCppCompiling(GCC_TOOLCHAIN, CPP_STATIC);
  }

  /**
   * Verifies C++ compilation works normally.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: cb4bfd6d-135a-4c92-966a-ba4f774f8842
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI and wait for sync to finish.
   *   2. Create an AVD with x86 API 24 or above.
   *   3. Select Auto debugger on Edit Configurations dialog.
   *   4. Open "build.gradle" in android studio, add "arguments "-DANDROID_STL=c++_shared",
   *      "-DANDROID_TOOLCHAIN=gcc""
   *   5. Set breakpoints in C++ and Java code.
   *   6. Debug on the AVD created above.
   *   7. When the Java breakpoint is hit verify call stack and variables and press F9 to resume
   *   8. When the C++ breakpoint is hit verify call stack and variables and press F9 to resume.
   *   9. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testGccCppShared() throws Exception {
    processToTestCppCompiling(GCC_TOOLCHAIN, CPP_SHARED);
  }

  /**
   * Verifies C++ compilation works normally.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: b8f84e9a-274e-4277-b5f9-2c9d0c91fda6
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI and wait for sync to finish.
   *   2. Create an AVD with x86 API 24 or above.
   *   3. Select Auto debugger on Edit Configurations dialog.
   *   4. Open "build.gradle" in android studio, add "arguments "-DANDROID_STL=gnustl_static",
   *      "-DANDROID_TOOLCHAIN=gcc""
   *   5. Set breakpoints in C++ and Java code.
   *   6. Debug on the AVD created above.
   *   7. When the Java breakpoint is hit verify call stack and variables and press F9 to resume
   *   8. When the C++ breakpoint is hit verify call stack and variables and press F9 to resume.
   *   9. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testGccGnustlStatic() throws Exception {
    processToTestCppCompiling(GCC_TOOLCHAIN, GNUSTL_STATIC);
  }

  /**
   * Verifies C++ compilation works normally.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 6c370f2c-e4e1-45dd-a930-9222dfafc6cb
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI and wait for sync to finish.
   *   2. Create an AVD with x86 API 24 or above.
   *   3. Select Auto debugger on Edit Configurations dialog.
   *   4. Open "build.gradle" in android studio, add "arguments "-DANDROID_STL=gnustl_shared",
   *      "-DANDROID_TOOLCHAIN=gcc""
   *   5. Set breakpoints in C++ and Java code.
   *   6. Debug on the AVD created above.
   *   7. When the Java breakpoint is hit verify call stack and variables and press F9 to resume
   *   8. When the C++ breakpoint is hit verify call stack and variables and press F9 to resume.
   *   9. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testGccGnustlShared() throws Exception {
    processToTestCppCompiling(GCC_TOOLCHAIN, GNUSTL_SHARED);
  }

  /**
   * Verifies C++ compilation works normally.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 486bea8f-ee79-47b4-9f41-602341c670d3
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI and wait for sync to finish.
   *   2. Create an AVD with x86 API 24 or above.
   *   3. Select Auto debugger on Edit Configurations dialog.
   *   4. Open "build.gradle" in android studio, add "arguments "-DANDROID_STL=cpp_static",
   *      "-DANDROID_TOOLCHAIN=clang""
   *   5. Set breakpoints in C++ and Java code.
   *   6. Debug on the AVD created above.
   *   7. When the Java breakpoint is hit verify call stack and variables and press F9 to resume
   *   8. When the C++ breakpoint is hit verify call stack and variables and press F9 to resume.
   *   9. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testClangCppStatic() throws Exception {
    processToTestCppCompiling(CLANG_TOOLCHAIN, CPP_STATIC);
  }

  /**
   * Verifies C++ compilation works normally.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 14d92870-c9d6-42e4-9acc-e29e70286bd1
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI and wait for sync to finish.
   *   2. Create an AVD with x86 API 24 or above.
   *   3. Select Auto debugger on Edit Configurations dialog.
   *   4. Open "build.gradle" in android studio, add "arguments "-DANDROID_STL=c++_shared",
   *      "-DANDROID_TOOLCHAIN=clang""
   *   5. Set breakpoints in C++ and Java code.
   *   6. Debug on the AVD created above.
   *   7. When the Java breakpoint is hit verify call stack and variables and press F9 to resume
   *   8. When the C++ breakpoint is hit verify call stack and variables and press F9 to resume.
   *   9. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testClangCppShared() throws Exception {
    processToTestCppCompiling(CLANG_TOOLCHAIN, CPP_SHARED);
  }

  /**
   * Verifies C++ compilation works normally.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: df1ec714-a2fb-477b-b0e9-227bee31c7e5
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI and wait for sync to finish.
   *   2. Create an AVD with x86 API 24 or above.
   *   3. Select Auto debugger on Edit Configurations dialog.
   *   4. Open "build.gradle" in android studio, add "arguments "-DANDROID_STL=gnustl_static",
   *      "-DANDROID_TOOLCHAIN=clang""
   *   5. Set breakpoints in C++ and Java code.
   *   6. Debug on the AVD created above.
   *   7. When the Java breakpoint is hit verify call stack and variables and press F9 to resume
   *   8. When the C++ breakpoint is hit verify call stack and variables and press F9 to resume.
   *   9. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  @Ignore("b/64487038")
  public void testClangGnustlStatic() throws Exception {
    processToTestCppCompiling(CLANG_TOOLCHAIN, GNUSTL_STATIC);
  }

  /**
   * Verifies C++ compilation works normally.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: c5fb21ca-0a8d-41f1-bcc6-44576126cb2c
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI and wait for sync to finish.
   *   2. Create an AVD with x86 API 24 or above.
   *   3. Select Auto debugger on Edit Configurations dialog.
   *   4. Open "build.gradle" in android studio, add "arguments "-DANDROID_STL=gnustl_shared",
   *      "-DANDROID_TOOLCHAIN=clang""
   *   5. Set breakpoints in C++ and Java code.
   *   6. Debug on the AVD created above.
   *   7. When the Java breakpoint is hit verify call stack and variables and press F9 to resume
   *   8. When the C++ breakpoint is hit verify call stack and variables and press F9 to resume.
   *   9. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  @Ignore("b/64487038")
  public void testClangGnustlShared() throws Exception {
    processToTestCppCompiling(CLANG_TOOLCHAIN, GNUSTL_SHARED);
  }

  private void processToTestCppCompiling(@NotNull String toolChain,
                                         @NotNull String dandroidStlType) throws Exception {
    if (!toolChain.equals(CLANG_TOOLCHAIN) && !toolChain.equals(GCC_TOOLCHAIN)) {
      throw new RuntimeException("Not supported Android toolchain provided: " + toolChain);
    }

    String cmakeArgsValue = null;
    if (dandroidStlType.equals(CPP_STATIC) ||
        dandroidStlType.equals(CPP_SHARED) ||
        dandroidStlType.equals(GNUSTL_STATIC) ||
        dandroidStlType.equals(GNUSTL_SHARED)) {
      cmakeArgsValue = ANDROID_STL + "=" + dandroidStlType;
    } else {
      throw new RuntimeException("Not supported Android STL type provided: " + dandroidStlType);
    }

    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("BasicCmakeAppForUI");
    emulator.createDefaultAVD(ideFrame.invokeAvdManager());

    ideFrame.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(guiTest.robot())
      .selectDebuggerType(AUTO)
      .clickOk();

    ideFrame.getEditor().open("app/build.gradle")
      .moveBetween("cppFlags \"\"", "")
      .enterText("\narguments \"" + cmakeArgsValue + "\"" + ", \"" + toolChain + "\"");

    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish(Wait.seconds(30));

    // Setup C++ and Java breakpoints.
    openAndToggleBreakPoints(ideFrame,
                             "app/src/main/jni/native-lib.c",
                             "return (*env)->NewStringUTF(env, message);");
    openAndToggleBreakPoints(ideFrame,
                             "app/src/main/java/com/example/basiccmakeapp/MainActivity.java",
                             "setContentView(tv);");

    ideFrame.debugApp(DEBUG_CONFIG_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(ideFrame);
    waitForSessionStart(debugToolWindowFixture);

    // Setup the expected patterns to match the variable values displayed in Debug windows's
    // 'Variables' tab.
    String[] expectedPatterns = new String[]{
      variableToSearchPattern("sum_of_10_ints", "int", "55"),
      variableToSearchPattern("product_of_10_ints", "int", "3628800"),
      variableToSearchPattern("quotient", "int", "512"),
    };
    checkAppIsPaused(ideFrame, expectedPatterns);
    resume(DEBUGER_CONF_NAME, ideFrame);

    expectedPatterns = new String[]{
      variableToSearchPattern("s",
                              "\"Success. Sum = 55, Product = 3628800, Quotient = 512\""),
    };
    checkAppIsPaused(ideFrame, expectedPatterns);
    assertThat(debugToolWindowFixture.getDebuggerContent(JAVA_DEBUGER_CONF_NAME)).isNotNull();
  }
}
