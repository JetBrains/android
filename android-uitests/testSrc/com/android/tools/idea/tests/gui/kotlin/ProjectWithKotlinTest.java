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

import com.android.tools.idea.tests.gui.emulator.DeleteAvdsRule;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.tests.gui.kotlin.ProjectWithKotlinTestUtil.createKotlinFileAndClassAndVerify;

@RunWith(GuiTestRemoteRunner.class)
public class ProjectWithKotlinTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  private final EmulatorTestRule emulator = new EmulatorTestRule();
  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(new DeleteAvdsRule())
    .around(emulator);

  private static final String KOTLIN_SUPPORT_PROJECT_DIR_NAME = "KotlinSupportProject";
  private static final String KOTLIN_SUPPORT_PACKAGE_NAME = "com.android.kotlinsupportproject";

  /**
   * Verifies user can link project with Kotlin.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 64230371-979d-4a17-86f4-7aa1213b93f6
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import KotlinSupportProject project, which does support Kotlin
   *      and wait for project sync to finish.
   *   2. Select Project view and expand directory to Java package and click on it.
   *   3. From menu, click on "File->New->Kotlin File/Class".
   *   4. In "New Kotlin File/Class" dialog, enter the name of class
   *      and choose "Class" from the dropdown list in Kind category, and click on OK.
   *   5. Continue this with File,interface,enum class and verify 1 & 2
   *   Verify:
   *   1. Observe the code in Kotlin language.
   *   2. Build and deploy on the emulator.
   *   </pre>
   * <p>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // b/114304149, fast
  public void createKotlinFileAndClassInKotlinSupportProject() throws Exception {
    createKotlinFileAndClassAndVerify(KOTLIN_SUPPORT_PROJECT_DIR_NAME, KOTLIN_SUPPORT_PACKAGE_NAME, true, guiTest, emulator);
  }
}
