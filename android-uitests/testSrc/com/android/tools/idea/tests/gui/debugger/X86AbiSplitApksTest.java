/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.emulator.DeleteAvdsRule;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class X86AbiSplitApksTest extends DebuggerTestBase {

  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();

  private final EmulatorTestRule emulator = new EmulatorTestRule(false);
  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(new DeleteAvdsRule())
    .around(emulator);

  /**
   * Verifies ABI split apks are generated as per the target emulator/device during a native
   * debug session.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 6b2878da-4464-4c32-be85-dd20a2f1bff2
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI.
   *   2. Enable split by adding the following to app/build.gradle: android.splits.abi.enable true.
   *   3. Start a native debugging session in Android Studio (deploy in emulator X86).
   *   4. Now hit the stop button.
   *   4. Go the folder ~<project folder="">/app/build/outputs/apk and check
   *      the apk generated (Verify 1, 2).
   *   Verify:
   *   1. APK generated should not be universal (You can verify this by trying to install the apk
   *      in a non X86 emulator or device)
   *   2. APK generated should explicitly for the ABI X86
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.SANITY)
  public void x86AbiSplitApks() throws Exception {
    DebuggerTestUtil.abiSplitApks(guiTest, DebuggerTestUtil.ABI_TYPE_X86);
  }
}
