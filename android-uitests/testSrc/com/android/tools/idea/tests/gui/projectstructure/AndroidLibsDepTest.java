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
package com.android.tools.idea.tests.gui.projectstructure;

import static com.android.tools.idea.tests.gui.projectstructure.DependenciesTestUtil.APP_NAME;
import static com.android.tools.idea.tests.gui.projectstructure.DependenciesTestUtil.MIN_SDK_API;
import static com.android.tools.idea.wizard.template.Language.Java;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AndroidLibsDepTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  private static final String LIB_NAME_1 = "modulea";
  private static final String LIB_NAME_2 = "moduleb";

  /**
   * Verifies that transitive dependencies with Android Libraries are resolved in a gradle file.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 6179f958-82e1-4605-ac3a-eaaec2e01dbe
   * <p>
   *   <pre>
   *   Test Steps
   *   1. Create a new Android Studio project.
   *   2. Go to File Menu > New Module > Android Library, create new Android library module.
   *   3. Right click on the app module > Module settings under dependencies, add module dependency
   *      (default: implementation) to library create in step 2. Create a Java class in this
   *      new Android library module.
   *   4. Go to File Menu > New Module > Android Library, create another new Android library module.
   *   5. Right click on the module (created in step 2) > Module settings under dependencies, add
   *      module dependency (select API scope type) to library create in step 4.
   *      Create a Java class in module created in step 4.
   *   6. Try accessing Library2 classes in Library1 (verify 1).
   *   7. Try accessing both Library1 and Library2 classes in app module of your project(verify 2).
   *   Verification
   *   1. Library 2 classes should be accessible to Library 1 and should get resolved successfully.
   *   2. Library 2 and Library 1 classes should be accessible to app module and should get
   *      resolved successfully.
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void transitiveDependenciesWithMultiAndroidLibraries() {
    IdeFrameFixture ideFrame = DependenciesTestUtil.createNewProject(guiTest, APP_NAME, MIN_SDK_API, Java);

    DependenciesTestUtil.createAndroidLibrary(ideFrame, LIB_NAME_1);
    DependenciesTestUtil.addModuleDependencyUnderAnother(guiTest, ideFrame, LIB_NAME_1, "app", "implementation");
    DependenciesTestUtil.createJavaClassInModule(ideFrame, LIB_NAME_1, DependenciesTestUtil.CLASS_NAME_1);

    createAndroidLibrary(ideFrame, LIB_NAME_2);
    DependenciesTestUtil.addModuleDependencyUnderAnother(guiTest, ideFrame, LIB_NAME_2, LIB_NAME_1, "api");
    DependenciesTestUtil.createJavaClassInModule(ideFrame, LIB_NAME_2, DependenciesTestUtil.CLASS_NAME_2);

    DependenciesTestUtil.accessLibraryClassAndVerify(ideFrame, LIB_NAME_1, LIB_NAME_2);
  }

  static private void createAndroidLibrary(@NotNull IdeFrameFixture ideFrame,
                                    @NotNull String moduleName) {
    ideFrame.openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module\u2026")
      .clickNextToAndroidLibrary()
      .enterModuleName(moduleName)
      .wizard()
      .clickFinishAndWaitForSyncToFinish();
  }
}
