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
package com.android.tools.idea.tests.gui.framework.fixture.assetstudio;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AssetStudioWizardFixture extends AbstractWizardFixture<AssetStudioWizardFixture> {

  private AssetStudioWizardFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog target) {
    super(AssetStudioWizardFixture.class, ideFrameFixture.robot(), target);
  }

  @NotNull
  public static AssetStudioWizardFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog.class, "Asset Studio"));
    return new AssetStudioWizardFixture(ideFrameFixture, dialog);
  }

  @NotNull
  public NewVectorAssetStepFixture getVectorAssetStep() {
    JRootPane rootPane = findStepWithTitle("Configure Vector Asset");
    return new NewVectorAssetStepFixture(robot(), rootPane);
  }

  public NewImageAssetStepFixture getImageAssetStep() {
    JRootPane rootPane = findStepWithTitle("Configure Image Asset");
    return new NewImageAssetStepFixture(robot(), rootPane);
  }
}
