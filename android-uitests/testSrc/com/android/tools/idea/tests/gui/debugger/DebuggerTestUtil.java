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

import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.emulator.EmulatorGenerator;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditConfigurationsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class DebuggerTestUtil {

  public static final String AUTO = "Auto";
  public static final String DUAL = "Dual";
  public static final String NATIVE = "Native";
  public static final String JAVA = "Java";
  public static final String JAVA_DEBUGGER_CONF_NAME = "app-java";

  public final static String ABI_TYPE_X86 = "x86";
  public final static String ABI_TYPE_X86_64 = "x86_64";

  private final static int GRADLE_SYNC_TIMEOUT = 60;

  public static void setDebuggerType(@NotNull IdeFrameFixture ideFrameFixture,
                       @NotNull String type) {
    ideFrameFixture.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(ideFrameFixture.robot())
      .selectDebuggerType(type)
      .clickOk();
  }

  public static DebugToolWindowFixture debugAppAndWaitForSessionToStart(@NotNull IdeFrameFixture ideFrameFixture,
                                                                        @NotNull GuiTestRule guiTest,
                                                                        @NotNull String configName,
                                                                        @NotNull String avdName) {
    return debugAppAndWaitForSessionToStart(ideFrameFixture, guiTest, configName, avdName, Wait.seconds(90));
  }

  public static DebugToolWindowFixture debugAppAndWaitForSessionToStart(@NotNull IdeFrameFixture ideFrameFixture,
                                                                        @NotNull GuiTestRule guiTest,
                                                                        @NotNull String configName,
                                                                        @NotNull String avdName,
                                                                        @NotNull Wait wait) {
    ideFrameFixture.debugApp(configName)
      .selectDevice(avdName)
      .clickOk();

    // Wait for background tasks to finish before requesting Debug Tool Window. Otherwise Debug Tool Window won't activate.
    GuiTests.waitForBackgroundTasks(guiTest.robot(), wait);

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(ideFrameFixture);

    // Wait for "Debugger attached to process.*" to be printed on the app-native debug console.
    ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(configName);
    Pattern DEBUGGER_ATTACHED_PATTERN = Pattern.compile(".*Debugger attached to process.*", Pattern.DOTALL);
    contentFixture.waitForOutput(new PatternTextMatcher(DEBUGGER_ATTACHED_PATTERN), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);

    return debugToolWindowFixture;
  }

  public static void abiSplitApks(@NotNull GuiTestRule guiTest,
                                  @NotNull String abiType) throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProject("BasicCmakeAppForUI");
    ideFrame.waitForGradleProjectSyncToFinish(Wait.seconds(GRADLE_SYNC_TIMEOUT));

    DebuggerTestUtil.setDebuggerType(ideFrame, DebuggerTestUtil.NATIVE);

    ideFrame.getEditor()
            .open("app/build.gradle", EditorFixture.Tab.EDITOR)
            .moveBetween("apply plugin: 'com.android.application'", "")
            .enterText("\n\nandroid.splits.abi.enable true")
            .invokeAction(EditorFixture.EditorAction.SAVE);

    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish(Wait.seconds(GRADLE_SYNC_TIMEOUT));

    openAndToggleBreakPoints(ideFrame,
                             "app/src/main/jni/native-lib.c",
                             "return (*env)->NewStringUTF(env, message);");

    String expectedApkName = "";
    String avdName = "";
    if (abiType.equals(ABI_TYPE_X86)) {
      expectedApkName = "app-x86-debug.apk";
    } else if (abiType.equals(ABI_TYPE_X86_64)) {
      expectedApkName = "app-x86_64-debug.apk";
    } else {
      throw new RuntimeException("Not supported ABI type provided: " + abiType);
    }

    ChooseSystemImageStepFixture.SystemImage systemImageSpec = new ChooseSystemImageStepFixture.SystemImage(
      "Nougat",
      "24",
      abiType,
      "Android 7.0 (Google APIs)"
    );
    avdName = EmulatorGenerator.ensureAvdIsCreated(
      ideFrame.invokeAvdManager(),
      new AvdSpec.Builder()
        .setSystemImageGroup(AvdSpec.SystemImageGroups.X86)
        .setSystemImageSpec(systemImageSpec)
        .build()
    );

    DebuggerTestUtil.debugAppAndWaitForSessionToStart(ideFrame, guiTest, "app", avdName, Wait.seconds(180));

    ideFrame.stopApp();
    ProjectViewFixture.PaneFixture projectPane = ideFrame.getProjectView().selectProjectPane();

    final String apkNameRef = expectedApkName;
    Wait.seconds(30).expecting("The apk file is generated.").until(() -> {
      try {
        projectPane.clickPath("BasicCmakeAppForUI",
                              "app",
                              "build",
                              "intermediates",
                              "instant-run-apk",
                              "debug",
                              apkNameRef);
        return true;
      } catch (LocationUnavailableException e) {
        return false;
      }
    });
  }

  private static void openAndToggleBreakPoints(IdeFrameFixture ideFrame, String fileName, String... lines) {
    EditorFixture editor = ideFrame.getEditor().open(fileName);
    for (String line : lines) {
      editor.moveBetween("", line);
      editor.invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT);
    }
    editor.close();
  }
}
