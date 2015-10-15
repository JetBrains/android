/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.gradle.project.ChooseGradleHomeDialog;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.ChooseGradleHomeDialogFixture;
import com.intellij.openapi.application.ApplicationManager;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;

import static com.android.SdkConstants.GRADLE_MINIMUM_VERSION;
import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertNotNull;

/**
 * UI Test for {@link com.android.tools.idea.gradle.project.ChooseGradleHomeDialog}.
 */
@BelongsToTestGroups({PROJECT_SUPPORT})
public class ChooseGradleHomeDialogTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testValidationWithInvalidMinimumGradleVersion() {
    File unsupportedGradleHome = getUnsupportedGradleHome();
    if (unsupportedGradleHome == null) {
      skip("testValidationWithInvalidMinimumGradleVersion");
      return;
    }

    ChooseGradleHomeDialogFixture dialog = launchChooseGradleHomeDialog();
    dialog.chooseGradleHome(unsupportedGradleHome)
          .clickOk()
          .requireValidationError("Gradle " + GRADLE_MINIMUM_VERSION + " or newer is required")
          .close();
  }

  @Test
  public void testValidateWithValidMinimumGradleVersion() {
    File gradleHomePath = getGradleHomePath();
    if (gradleHomePath == null) {
      skip("testValidateWithValidMinimumGradleVersion");
      return;
    }

    ChooseGradleHomeDialogFixture dialog = launchChooseGradleHomeDialog();
    dialog.chooseGradleHome(gradleHomePath)
          .clickOk()
          .requireNotShowing();  // if it is not showing on the screen, it means that there were no validation errors.
  }

  @NotNull
  private ChooseGradleHomeDialogFixture launchChooseGradleHomeDialog() {
    final ChooseGradleHomeDialog dialog = execute(new GuiQuery<ChooseGradleHomeDialog>() {
      @Override
      protected ChooseGradleHomeDialog executeInEDT() throws Throwable {
        return new ChooseGradleHomeDialog(GRADLE_MINIMUM_VERSION);
      }
    });
    assertNotNull(dialog);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        dialog.setModal(false);
        dialog.show();
      }
    });

    return ChooseGradleHomeDialogFixture.find(myRobot);
  }
}
