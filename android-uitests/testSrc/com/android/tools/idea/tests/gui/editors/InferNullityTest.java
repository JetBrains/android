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
package com.android.tools.idea.tests.gui.editors;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.tests.util.WizardUtils;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import javax.swing.JDialog;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;

@RunWith(GuiTestRemoteRunner.class)
public class InferNullityTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  protected static final String EMPTY_VIEWS_ACTIVITY_TEMPLATE = "Empty Views Activity";
  private static String ANALYZE = "Analyze";

  @Before
  public void setUp() throws Exception {
    WizardUtils.createNewProject(guiTest, EMPTY_VIEWS_ACTIVITY_TEMPLATE); // Default projects are created with androidx dependencies
    guiTest.robot().waitForIdle();
  }

  /**
   * Verifies inferring nullity of calling methods and variables that can/cannot return null.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 230f462e-21b1-435a-99be-29db96b5e1ad
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create Empty Views Activity project and add the sample methods to MainActivity class
   *   2. Click on Analyze > Infer Nullity.
   *   3. Select Annotations scope as "Whole Project" and click OK.
   *   4. Click OK when prompted to add the support-annotations dependency to the project.
   *   Expectations:
   *   1. "compile 'com.intellij:annotations:x.0' " dependency is added to the project.
   *   2. Android @Nullable and @NonNull annotations are added in detected locations in the code.
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.FAT_BAZEL)
  public void inferNullity() throws Exception {
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();

    guiTest.waitForBackgroundTasks();

    editor.open("/app/src/main/java/com/google/myapplication/MainActivity.java")
      .moveBetween(" }", "")
      .enterText("\npublic Color myMethod() {\nColor color = null;\nreturn color;\n}\n\npublic Color myMethod1() {\nColor color = new Color();\nreturn color;\n}\n")
      .select("()public class MainActivity")
      .enterText("import android.graphics.Color;\n\n");

    guiTest.waitForBackgroundTasks();

    ideFrame.invokeMenuPath("Code", "Analyze Code", "Infer Nullity...");

    DialogFixture specifyScopeDialog = findDialog(withTitle("Specify Infer Nullity Scope"))
      .withTimeout(SECONDS.toMillis(30)).using(guiTest.robot());
    specifyScopeDialog.button(withText(ANALYZE)).click();
    guiTest.waitForBackgroundTasks();

    DialogFixture addDependecyDialog = findDialog(withTitle("Infer Nullity Annotations"))
      .withTimeout(SECONDS.toMillis(30)).using(guiTest.robot());
    addDependecyDialog.button(withText("OK")).click();
    guiTest.waitForBackgroundTasks();

    editor.open("app/build.gradle.kts");
    String buildGradleContents = editor.getCurrentFileContents();
    assertThat(buildGradleContents).contains("androidx.annotation:annotation");

    editor.open("/app/src/main/java/com/google/myapplication/MainActivity.java");
    String codeContents = editor.getCurrentFileContents();
    assertThat(codeContents).contains("@androidx.annotation.Nullable");
    assertThat(codeContents).contains("@androidx.annotation.NonNull");
  }
}
