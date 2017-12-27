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
package com.android.tools.idea.tests.gui.emulator;

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.DeployTargetPickerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.LayeredIcon;
import org.fest.reflect.exception.ReflectionError;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;
import javax.swing.*;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.reflect.core.Reflection.method;
import static org.fest.reflect.core.Reflection.field;

@RunWith(GuiTestRunner.class)
public class DeviceChooserTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private final static String APP_NAME = "app";
  private final static String TAB = "x86 Images";
  private final static String RELEASE_NAME = "Nougat";
  private final static String API_LEVEL = "24";
  private final static String ABI_TYPE = "x86";
  private final static String TARGET_NAME = "Android 7.0";
  private final static String SECOND_AVD_NAME = "second device under test";
  private final static String GET_ICON_METHOD_NAME = "getIcon";
  private static final Pattern RUN_OUTPUT = Pattern.compile(".*Connected to process.*",
                                                            Pattern.DOTALL);

  /**
   * To verify the new Device chooser UI works as expected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 5cb54993-8f45-4632-8a9b-ebfc51be6667
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Import an existing project
   *   3. Create 2 AVDs
   *   4. Click on Run (Verify 1)
   *   5. From the device chooser window, select multiple AVD's and click OK (Verify 2)
   *   6. Check the Run button icon on the run tool window (Verify 3)
   *   7. Click on the "Stop" button (from main toolbar or the run tool window) (Verify 4)
   *   Verify:
   *   1. The "Select deployment target" window is displayed with the list of all available AVDs
   *      (an option to create new emulator devices).
   *   2. Multiple AVDs and devices can be selected for run and the run action will apply on all
   *      of the selections.
   *   3. The run button should have a live indicator (a dot below the green triangle) indicating
   *      the app is live.
   *   4. The app should be killed (after the app dies on the device, the stop button on Run Tool
   *      window should become disabled)
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testDeviceChooser() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("InstrumentationTest");
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    emulator.createAVD(
        guiTest.ideFrame().invokeAvdManager(),
        TAB,
        new ChooseSystemImageStepFixture.SystemImage(RELEASE_NAME, API_LEVEL, ABI_TYPE, TARGET_NAME),
        SECOND_AVD_NAME);

    DeployTargetPickerDialogFixture deployTargetPickerDialog = ideFrame.runApp(APP_NAME);
    deployTargetPickerDialog.getCreateNewVirtualDeviceButton();
    deployTargetPickerDialog.selectDevice(emulator.getDefaultAvdName());
    deployTargetPickerDialog.selectDevice(SECOND_AVD_NAME);
    deployTargetPickerDialog.selectDevice(emulator.getDefaultAvdName());
    deployTargetPickerDialog.clickOk();

    ideFrame.getRunToolWindow().findContent(APP_NAME)
        .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);

    // The run button should have a live indicator (a dot below the green triangle) indicating
    // the app is live. Here we check the icon is LayeredIcon type and it has 2 icons there:
    // one is the green triangle icon, another is the dot near it.
    ActionButtonFixture runButtonFixture = ideFrame.findRunApplicationButton();
    Ref<LayeredIcon> runButtonIcon = new Ref<>();
    try {
      LayeredIcon layeredIcon = method(GET_ICON_METHOD_NAME)
          .withReturnType(LayeredIcon.class)
          .in(runButtonFixture.target())
          .invoke();
      runButtonIcon.set(layeredIcon);
    } catch (ReflectionError e) {
      System.err.println("ReflectionError: " + e.getMessage());
      throw e;
    }
    Icon[] icons = runButtonIcon.get().getAllLayers();
    assertThat(icons.length == 2).isTrue();

    // Stop app and check the app is stopped and there is only one icon, the green triangle icon.
    ideFrame.stopApp();
    Wait.seconds(10).expecting("Run button icon is not null").until(() -> {
      ActionButtonFixture runButton = ideFrame.findRunApplicationButton();
      Icon icon = method(GET_ICON_METHOD_NAME)
          .withReturnType(ScalableIcon.class)
          .in(runButton.target())
          .invoke();
      return icon != null;
    });

    ActionButtonFixture runButton = ideFrame.findRunApplicationButton();
    Icon icon = method(GET_ICON_METHOD_NAME)
        .withReturnType(ScalableIcon.class)
        .in(runButton.target())
        .invoke();
    assertThat(icon).isNotNull();

    // Check the Stop button on Run Tool Window should be disabled.
    GenericTypeMatcher<ActionButton> matcher = new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton component) {
        if (!component.isShowing()) {
          return false;
        }

        Ref<String> myPlace = new Ref<>();
        try {
          String place = field("myPlace")
            .ofType(String.class)
            .in(component)
            .get();
          myPlace.set(place);
        } catch (ReflectionError e) {
          System.err.println("ReflectionError: " + e.getMessage());
          throw e;
        }

        if (!myPlace.get().equals("RunnerToolbar")) {
          return false;
        }

        AnAction action = component.getAction();
        if (action != null) {
          String id = ActionManager.getInstance().getId(action);
          return "Stop".equals(id);
        }
        return false;
      }
    };

    Wait.seconds(20).expecting("The Stop button on Run Tool Window should be disabled.").until(() -> {
      ActionButton found = guiTest.robot().finder().find(ideFrame.target(), matcher);
      if (found.isEnabled()) {
        return false;
      } else {
        return true;
      }
    });
  }
}
