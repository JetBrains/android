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
package com.android.tools.idea.tests.gui.kotlin;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ConfigureKotlinDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorNotificationPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import kotlin.KotlinVersion;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AddKotlinTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(8, TimeUnit.MINUTES);

  private static final String PROJECT_DIR_NAME = "LinkProjectWithKotlin";
  private static final String PACKAGE_NAME = "com.android.linkprojectwithkotlin";

  /**
   * Verifies user can link project with Kotlin.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 30f26a59-108e-49cc-bec0-586f518ea3cb
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import LinkProjectWithKotlin project, which doesn't support Kotlin
   *      and wait for project sync to finish.
   *   2. Select Project view and expand directory to Java package and click on it.
   *   3. From menu, click on "File->New->Kotlin File/Class".
   *   4. In "New Kotlin File/Class" dialog, enter the name of class
   *      and choose "Class" from the dropdown list in Kind category, and click on OK.
   *   5. Click on the configure pop up on the top right corner or bottom right corner.
   *   6. Select all modules containing Kotlin files option from "Configure kotlin pop up".
   *   7. Continue this with File,interface,enum class and verify 1 & 2
   *   Verify:
   *   1. Observe the code in Kotlin language.
   *   2. Build and deploy on the emulator.
   *   </pre>
   * <p>
   */
  @Test
  @RunIn(TestGroup.SANITY_BAZEL)
  public void addKotlinClass() throws Exception {
    try {
      guiTest.importProjectAndWaitForProjectSyncToFinish(PROJECT_DIR_NAME);
    } catch (WaitTimedOutError timeout) {
      // Timed out while waiting for project to sync and index. However, we are not concerned
      // about how long the test requires to sync and index here. The timeout is there just to
      // handle infinite loops, which we already handle with GuiTestRule's timeout. Honestly,
      // this timeout can be expanded. This call acts as an expansion of the timeout we can't
      // modify in GradleGuiTestProjectSystem
      GuiTests.waitForBackgroundTasks(guiTest.robot(), Wait.seconds(TimeUnit.MINUTES.toSeconds(2)));
    }

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ProjectViewFixture.PaneFixture projectPane = ideFrameFixture.getProjectView().selectProjectPane();

    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
                                                    ideFrameFixture,
                                                    PROJECT_DIR_NAME,
                                                    PACKAGE_NAME,
                                                    ProjectWithKotlinTestUtil.CLASS_NAME,
                                                    ProjectWithKotlinTestUtil.TYPE_CLASS);
    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
                                                    ideFrameFixture,
                                                    PROJECT_DIR_NAME,
                                                    PACKAGE_NAME,
                                                    ProjectWithKotlinTestUtil.FILE_NAME,
                                                    ProjectWithKotlinTestUtil.TYPE_FILE);
    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
                                                    ideFrameFixture,
                                                    PROJECT_DIR_NAME,
                                                    PACKAGE_NAME,
                                                    ProjectWithKotlinTestUtil.INTERFACE_NAME,
                                                    ProjectWithKotlinTestUtil.TYPE_INTERFACE);
    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
                                                    ideFrameFixture,
                                                    PROJECT_DIR_NAME,
                                                    PACKAGE_NAME,
                                                    ProjectWithKotlinTestUtil.ENUM_NAME,
                                                    ProjectWithKotlinTestUtil.TYPE_ENUMCLASS);
    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
                                                    ideFrameFixture,
                                                    PROJECT_DIR_NAME,
                                                    PACKAGE_NAME,
                                                    ProjectWithKotlinTestUtil.OBJECT_NAME,
                                                    ProjectWithKotlinTestUtil.TYPE_OBJECT);

    EditorNotificationPanelFixture editorNotificationPanelFixture =
      ideFrameFixture.getEditor().awaitNotification("Kotlin not configured");
    editorNotificationPanelFixture.performActionWithoutWaitingForDisappearance("Configure");

    // As default, "All modules containing Kotlin files" option is selected for now.
    ConfigureKotlinDialogFixture cfgKotlin = ConfigureKotlinDialogFixture.find(ideFrameFixture.robot());
    // OK button can take a while to be enabled. We just wait for the button to be available, and
    // then we explicitly wait a long time for it to be enabled. This lets us have a less
    // strict wait for the button to be clickable.
    JButton okButton = GuiTests.waitUntilShowing(
      ideFrameFixture.robot(),
      cfgKotlin.target(),
      Matchers.byText(JButton.class, "OK")
    );

    JButtonFixture okButtonFixture = new JButtonFixture(
      ideFrameFixture.robot(),
      okButton
    );
    Wait.seconds(TimeUnit.MINUTES.toSeconds(5))
      .expecting("OK button to be enabled")
      .until(() -> okButtonFixture.isEnabled());

    okButtonFixture.click();

    // TODO: the following is a hack. See http://b/79752752 for removal of the hack
    // The Kotlin plugin version chosen is done with a network request. This does not work
    // in an environment where network access is unavailable. We need to handle setting
    // the Kotlin plugin version ourselves temporarily.
    Wait.seconds(15)
      .expecting("Gradle Kotlin plugin version to be set")
      .until(() ->
        ideFrameFixture.getEditor().open("build.gradle").getCurrentFileContents().contains("kotlin_version")
      );

    String buildGradleContents = ideFrameFixture.getEditor()
      .open("build.gradle")
      .getCurrentFileContents();

    KotlinVersion kotlinVersion = KotlinPluginLayout.getInstance().getStandaloneCompilerVersion().getKotlinVersion();
    String newBuildGradleContents = buildGradleContents.replaceAll(
      "kotlin_version.*=.*",
      "kotlin_version = '" + kotlinVersion + '\'')
      .replaceAll(
        "mavenCentral\\(\\)",
        ""
      );

    OutputStream buildGradleOutput = ideFrameFixture.getEditor()
      .open("build.gradle")
      .getCurrentFile()
      .getOutputStream(null);
    Ref<IOException> ioErrors = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try (
          Writer buildGradleWriter = new OutputStreamWriter(buildGradleOutput, StandardCharsets.UTF_8)
        ) {
          buildGradleWriter.write(newBuildGradleContents);
        } catch (IOException writeError) {
          ioErrors.set(writeError);
        }
      });
    });
    IOException ioError = ioErrors.get();
    if (ioError != null) {
      throw new Exception("Unable to modify build.gradle file", ioError);
    }
    guiTest.waitForBackgroundTasks();
    // TODO End hack

    ideFrameFixture.requestProjectSyncAndWaitForSyncToFinish();

    assertThat(ideFrameFixture.invokeProjectMake(Wait.seconds(180)).isBuildSuccessful()).isTrue();
  }
}
