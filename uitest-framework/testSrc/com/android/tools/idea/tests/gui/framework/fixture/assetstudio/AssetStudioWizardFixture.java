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

import com.android.tools.idea.npw.assetstudio.ui.VectorIconButton;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardFixture;
import com.google.common.collect.Iterables;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JSliderFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

import static com.google.common.truth.Truth.assertThat;

public class AssetStudioWizardFixture extends AbstractWizardFixture<AssetStudioWizardFixture> {

  private AssetStudioWizardFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog target) {
    super(AssetStudioWizardFixture.class, ideFrameFixture.robot(), target);
  }

  @NotNull
  public static AssetStudioWizardFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog.class, "Asset Studio"));
    return new AssetStudioWizardFixture(ideFrameFixture, dialog);
  }

  public NewImageAssetStepFixture getImageAssetStep() {
    JRootPane rootPane = findStepWithTitle("Configure Image Asset");
    return new NewImageAssetStepFixture(robot(), rootPane);
  }

  public IconPickerDialogFixture chooseIcon(@NotNull IdeFrameFixture ideFrameFixture) {
    VectorIconButton vectorIconButton = ideFrameFixture.robot().finder().findByType(VectorIconButton.class);
    new JButtonFixture(ideFrameFixture.robot(), vectorIconButton).click();
    return IconPickerDialogFixture.find(ideFrameFixture.robot());
  }

  @NotNull
  public AssetStudioWizardFixture enableAutoMirror() {
    String title = "Enable auto mirroring for RTL layout";
    JCheckBox box = GuiTests.waitUntilShowing(robot(), target(), Matchers.byText(JCheckBox.class, title));
    robot().click(box);
    Wait.seconds(1).expecting("button " + title + " to be enabled").until(() -> box.isEnabled());
    return this;
  }

  @NotNull
  public AssetStudioWizardFixture enableOverrideDefaultSize() {
    String title = "Override";
    JCheckBox box = GuiTests.waitUntilShowing(robot(), target(), Matchers.byText(JCheckBox.class, title));
    robot().click(box);
    Wait.seconds(1).expecting("button " + title + " to be enabled").until(() -> box.isEnabled());
    return this;
  }

  @NotNull
  public AssetStudioWizardFixture setSize(int width, int height) {
    Collection<JFormattedTextField> all =
      robot().finder().findAll(target(), Matchers.byType(JFormattedTextField.class).andIsShowing().andIsEnabled());
    assertThat(all).hasSize(2);
    new JTextComponentFixture(robot(), Iterables.get(all, 0)).setText(Integer.toString(width));
    new JTextComponentFixture(robot(), Iterables.get(all, 1)).setText(Integer.toString(height));
    return this;
  }

  @NotNull
  public AssetStudioWizardFixture setOpacity(int ratio) {
    JSlider slider = GuiTests.waitUntilShowingAndEnabled(robot(), target(), Matchers.byType(JSlider.class));
    new JSliderFixture(robot(), slider).slideTo(ratio);
    return this;
  }
}
