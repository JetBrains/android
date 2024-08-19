/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.annotator;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.google.common.truth.Truth;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class DaggerSymbolResolutionTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  private static final String FILE_PATH = "app/src/main/java/com/example/dagger2app/intro/";

  private static final String DAGGER_COMPONENT_CLASS = "VehicleComponent.java";
  private static final String DAGGER_COMPONENT_CLASS_KOTLIN = "VehicleComponent.kt";

  private static final String DAGGER_MODULE_CLASS = "VehiclesModule.java";
  private static final String DAGGER_MODULE_CLASS_KOTLIN = "VehiclesModule.kt";

  private static final String TEST_FILE_PATH = "app/src/test/java/com/example/dagger2app/";

  private static final String DAGGER_TEST_FILE = "DaggarUnitTest.java";
  private static final String DAGGER_TEST_FILE_KOTLIN = "DaggarTestUnit.kt";

  /**
   * To verify Sample Dagger code symbols resolve and runs
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 316a54ab-72b9-4619-90f9-02a35b499900
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Download Project
   *      Test imports a project with the dagger components
   *   2. Update grade plugin if needed
   *   3. After successful gradle sync Expand com.example.dagger2app.intro
   *      package > Open all class files (Verify 1)
   *   4. Expand com.example.dagger2app(test) package > Open DaggerTestUnit.java
   *   5. Run DaggerTestUnit class (Verify 2)
   *   Verify:
   *   1. Make sure symbols for dagger classes are resolved
   *   2. Make sure test passes
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  //@Test
  public void daggerSymbolResolutionTest() throws Exception{
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("Dagger2App");

    // Rebuild project to generate Dagger classes.
    Truth.assertThat(ideFrame.invokeRebuildProject().isBuildSuccessful()).isTrue();
    // Resync the project with the newly created Dagger classes.
    ideFrame.requestProjectSyncAndWaitForSyncToFinish();

    EditorFixture editorFixture = ideFrame.getEditor().open(FILE_PATH + DAGGER_COMPONENT_CLASS);
    Wait.seconds(10).expecting(DAGGER_COMPONENT_CLASS + " file is opened.")
      .until(() -> DAGGER_COMPONENT_CLASS.equals(ideFrame.getEditor().getCurrentFileName()));
    editorFixture.waitUntilErrorAnalysisFinishes();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(editorFixture.getHighlights(HighlightSeverity.WARNING).size())
      .isEqualTo(0);
    assertThat(editorFixture.getHighlights(HighlightSeverity.ERROR).size())
      .isEqualTo(0);

    guiTest.waitForAllBackgroundTasksToBeCompleted();
    editorFixture = ideFrame.getEditor().open(FILE_PATH + DAGGER_MODULE_CLASS);
    Wait.seconds(10).expecting(DAGGER_MODULE_CLASS + " file is opened.")
      .until(() -> DAGGER_MODULE_CLASS.equals(ideFrame.getEditor().getCurrentFileName()));
    editorFixture.waitUntilErrorAnalysisFinishes();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(editorFixture.getHighlights(HighlightSeverity.WARNING).size())
      .isEqualTo(0);
    assertThat(editorFixture.getHighlights(HighlightSeverity.ERROR).size())
      .isEqualTo(0);

    guiTest.waitForAllBackgroundTasksToBeCompleted();
    editorFixture = ideFrame.getEditor().open(TEST_FILE_PATH + DAGGER_TEST_FILE);
    Wait.seconds(10).expecting(DAGGER_TEST_FILE + " file is opened.")
      .until(() -> DAGGER_TEST_FILE.equals(ideFrame.getEditor().getCurrentFileName()));
    editorFixture.waitUntilErrorAnalysisFinishes();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(editorFixture.getHighlights(HighlightSeverity.WARNING).size())
      .isEqualTo(0);
    assertThat(editorFixture.getHighlights(HighlightSeverity.ERROR).size())
      .isEqualTo(0);
  }

  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void daggerSymbolResolutionTestKotlin() throws Exception{
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("Dagger2AppKotlinLib");

    // Rebuild project to generate Dagger classes.
    BuildStatus buildStatus = ideFrame.invokeRebuildProject();
    Truth.assertThat(buildStatus.isBuildSuccessful()).isTrue();
    // Resync the project with the newly created Dagger classes.
    ideFrame.requestProjectSyncAndWaitForSyncToFinish();

    checkFile(ideFrame, FILE_PATH, DAGGER_COMPONENT_CLASS_KOTLIN, 1); // Warning: Function "init" is never used
    checkFile(ideFrame, FILE_PATH, DAGGER_MODULE_CLASS_KOTLIN, 0);
    checkFile(ideFrame, TEST_FILE_PATH, DAGGER_TEST_FILE_KOTLIN, 0);

    String kotlinFiles = "app/src/main/java/com/example/dagger2app/kotlinfiles/";
    // Warnings:
    // Function "init" is never used
    // Function "logger" is never used
    checkFile(ideFrame, kotlinFiles, "SourceComponent.kt", 2);
    checkFile(ideFrame, kotlinFiles, "SourceLogger.kt", 0);
    String libFiles = "kotlin_lib/src/main/java/com/example/kotlin_lib/";
    // Warnings:
    // Function "init" is never used
    // Function "logger" is never used
    checkFile(ideFrame, libFiles, "LibraryComponent.kt", 2);
    checkFile(ideFrame, libFiles, "Logger.kt", 0);
  }

  private void checkFile(IdeFrameFixture ideFrame, String path, String name, int expectedWarnings) {
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    EditorFixture editorFixture = ideFrame.getEditor().open(path + name);
    Wait.seconds(10).expecting(name + " file is opened.")
      .until(() -> name.equals(ideFrame.getEditor().getCurrentFileName()));
    editorFixture.waitUntilErrorAnalysisFinishes();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(editorFixture.getHighlights(HighlightSeverity.WARNING).size())
      .isEqualTo(expectedWarnings);
    assertThat(editorFixture.getHighlights(HighlightSeverity.ERROR).size())
      .isEqualTo(0);
  }
}
