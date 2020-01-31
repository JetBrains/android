/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class DualDebuggerInNdkProjectTest {
  private final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);
  private final AvdTestRule avdRule = AvdTestRule.Companion.buildAvdTestRule(() ->
    new AvdSpec.Builder()
  );
  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(avdRule)
    .around(guiTest);

  @Before
  public void setupSpecialSdk() {
    DebuggerTestUtil.setupSpecialSdk(avdRule);
  }

  @Before
  public void symlinkLldb() throws IOException {
    DebuggerTestUtil.symlinkLldb();
  }

  /**
   * Verifies that Dual-debugger works fine with NDK build project.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: c7e70d91-af4a-4357-a8df-0e9e46b3484d
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import NdkHelloJni and wait for sync to finish.
   *   2. Create an AVD with x86 API 24 or above.
   *   3. Select Dual debugger on Edit Configurations dialog.
   *   4. Set breakpoints in both Java and C++ codes.
   *   5. Debug on the avd created above.
   *   6. When the C++ breakpoint is hit, verify variables and resume.
   *   7. When the Java breakpoint is hit, verify variables.
   *   8. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void testDualDebugger() throws Exception {
    DebuggerTestUtil.processToTest(guiTest, avdRule, DebuggerTestUtil.DUAL);
  }

}
