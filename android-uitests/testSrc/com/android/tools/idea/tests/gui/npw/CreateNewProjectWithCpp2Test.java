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
package com.android.tools.idea.tests.gui.npw;

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.tools.idea.tests.gui.npw.NewCppProjectTestUtil.createNewProjectWithCpp;

@RunWith(GuiTestRunner.class)
public class CreateNewProjectWithCpp2Test {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule(false);

  /**
   * Verify creating a new project from default template.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: ede43cef-f4a1-484b-9b1b-58000c4ba17c
   * <p>
   *   <pre>
   *   Steps:
   *   1. Create a new project; check the box "Include C++ Support"
   *   2. Click next until you get to the window for "Customize C++ Support", check Exceptions support
   *   3. Click Finish
   *   4. Run
   *
   *   On (4) verify that  android.defaultConfig.cmake.cppFlags has "-fexceptions"
   *   </pre>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void createNewProjectWithCpp2() throws Exception {
    createNewProjectWithCpp(true, false, guiTest);
  }
}
