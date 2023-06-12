/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.tests.gui.framework.fixture.ConfigureKotlinDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.KotlinIsNotConfiguredDialogFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class ConversionTestUtil {

  @NotNull
  protected static void removeCodeForGradleSyncToPass(@NotNull GuiTestRule guiTest) throws Exception {
    // Gradle sync is failing https://buganizer.corp.google.com/issues/180411529 and because of it this test case is failing
    // TODO: the following is a hack. See http://b/217805224 for removal of the hack

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();
    assertThat(ideFrameFixture.getEditor().open("app/build.gradle").getCurrentFileContents()).contains("compileSdk");


    String buildGradleContents = ideFrameFixture.getEditor()
      .open("app/build.gradle")
      .getCurrentFileContents();

    String newBuildGradleContents = buildGradleContents.replaceAll(
      "mavenCentral\\(\\)",
      ""
    );

    OutputStream buildGradleOutput = ideFrameFixture.getEditor()
      .open("app/build.gradle")
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
  }

  @NotNull
  protected static void convertJavaToKotlin(@NotNull GuiTestRule guiTest) throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture.waitAndInvokeMenuPath("Code", "Convert Java File to Kotlin File");

    //Click 'OK, configure Kotlin in the project' on 'Kotlin is not configured in the project' dialog box
    /*
     * Content of dialog box:  'You will have to configure Kotlin in project before performing a conversion'
     */
    KotlinIsNotConfiguredDialogFixture.find(ideFrameFixture.robot())
      .clickOkAndWaitDialogDisappear();

    //Click 'OK' on 'Configure Kotlin with Android with Gradle' dialog box
    ConfigureKotlinDialogFixture.find(ideFrameFixture.robot())
      .clickOkAndWaitDialogDisappear();

    changeKotlinVersion(guiTest);
  }

  @NotNull
  public static void changeKotlinVersion(@NotNull GuiTestRule guiTest) throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    // TODO: the following is a hack. See http://b/79752752 for removal of the hack
    // The Kotlin plugin version chosen is done with a network request. This does not work
    // in an environment where network access is unavailable. We need to handle setting
    // the Kotlin plugin version ourselves temporarily.
    Wait.seconds(15)
      .expecting("Gradle project sync in progress...")
      .until(() ->
               ideFrameFixture.getEditor().open("build.gradle.kts").getCurrentFileContents().contains("kotlin")
      );

    String buildGradleContents = ideFrameFixture.getEditor()
      .open("build.gradle.kts")
      .getCurrentFileContents();

    //String kotlinVersion = kotlinCompilerVersionShort();
    String newBuildGradleContents = buildGradleContents.replaceAll(
        "id\\(\"org\\.jetbrains\\.kotlin\\.android\"\\) *version *\"\\d+\\.\\d+\\.\\d+\" *apply *false",
        "id(\"org.jetbrains.kotlin.android\") version \"1.7.21\" apply false"
      );

    OutputStream buildGradleOutput = ideFrameFixture.getEditor()
      .open("build.gradle.kts")
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
  }

  protected static void changeKotlinVersionForSimpleApplication(@NotNull GuiTestRule guiTest) throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    // TODO: the following is a hack. See http://b/79752752 for removal of the hack
    // The Kotlin plugin version chosen is done with a network request. This does not work
    // in an environment where network access is unavailable. We need to handle setting
    // the Kotlin plugin version ourselves temporarily.
    Wait.seconds(15)
      .expecting("Gradle project sync in progress...")
      .until(() ->
               ideFrameFixture.getEditor().open("build.gradle").getCurrentFileContents().contains("kotlin")
      );

    String buildGradleContents = ideFrameFixture.getEditor()
      .open("build.gradle")
      .getCurrentFileContents();

    //String kotlinVersion = kotlinCompilerVersionShort();
    String newBuildGradleContents = buildGradleContents.replaceAll(
      "kotlin_version\\s\\=\\s\\'\\d+\\.\\d+\\.\\d+\\'",
      "kotlin_version = '1.6.21'"
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
  }
}
