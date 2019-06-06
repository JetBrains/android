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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(GuiTestRemoteRunner.class)
public class JavaLibsDepTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  private static final String JAVA_MODULE_1 = "lib"; // default name
  private static final String JAVA_MODULE_2 = "lib2"; // default name

  @Before
  public void setUp() {
    StudioFlags.NEW_PSD_ENABLED.override(false);
  }

  @After
  public void tearDown() {
    StudioFlags.NEW_PSD_ENABLED.clearOverride();
  }

  /**
   * Verifies that transitive dependencies with Java Libraries are resolved in a gradle file.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 5f5581fe-b02f-4775-aa76-f592e011080d
   * <p>
   *   <pre>
   *   Test Steps
   *   1. Create a new Android Studio project.
   *   2. Go to File Menu > New Module > Java Library, create new Java library module.
   *   3. Right click on the app module > Module settings under dependencies, add module dependency
   *      (default: implementation) to library create in step 2. Create a Java class in this
   *      new Android library module.
   *   4. Go to File Menu > New Module > Java Library, create another new Android library module.
   *   5. Right click on the module (created in step 2) > Module settings under dependencies, add
   *      module dependency (default: implementation) to library create in step 4.
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
  public void multiJavaLibraries() {
    IdeFrameFixture ideFrame = DependenciesTestUtil.createNewProject(guiTest, DependenciesTestUtil.APP_NAME, DependenciesTestUtil.MIN_SDK, DependenciesTestUtil.LANGUAGE_JAVA);

    DependenciesTestUtil.createJavaModule(ideFrame); // default name: lib
    DependenciesTestUtil.addModuleDependencyUnderAnother(ideFrame, JAVA_MODULE_1, "app", "API");
    DependenciesTestUtil.createJavaClassInModule(ideFrame, JAVA_MODULE_1, DependenciesTestUtil.CLASS_NAME_1);

    DependenciesTestUtil.createJavaModule(ideFrame); // default name: lib2
    DependenciesTestUtil.addModuleDependencyUnderAnother(ideFrame, JAVA_MODULE_2, JAVA_MODULE_1, "API");
    DependenciesTestUtil.createJavaClassInModule(ideFrame, JAVA_MODULE_2, DependenciesTestUtil.CLASS_NAME_2);

    DependenciesTestUtil.accessLibraryClassAndVerify(ideFrame, JAVA_MODULE_1, JAVA_MODULE_2);
  }

}
