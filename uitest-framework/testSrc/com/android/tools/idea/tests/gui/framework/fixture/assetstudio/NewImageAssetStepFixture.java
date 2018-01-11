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

import com.android.tools.adtui.ImageComponent;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.ui.ColorPicker;
import com.intellij.ui.components.JBLabel;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JRadioButtonFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NewImageAssetStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<NewImageAssetStepFixture, W> {

  protected NewImageAssetStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(NewImageAssetStepFixture.class, wizard, target);
  }

  @NotNull
  public NewImageAssetStepFixture<W> selectIconType(@NotNull String iconType) {
    JComboBox comp = robot().finder().findByLabel(target(), "Icon Type:", JComboBox.class, true);
    JComboBoxFixture combo = new JComboBoxFixture(robot(), comp);
    combo.selectItem(iconType);
    return this;
  }

  private List<JPanel> getPreviewPanels() {
    return Lists.newArrayList(robot().finder().findAll(Matchers.byName(JPanel.class, "PreviewIconsPanel").andIsShowing()));
  }

  public int getPreviewPanelCount() {
    return getPreviewPanels().size();
  }

  public List<String> getPreviewPanelIconNames(int index) {
    JPanel panel = getPreviewPanels().get(index);
    Wait.seconds(1).expecting("Icon preview showing").until(
      () -> !robot().finder().findAll(panel, Matchers.byName(JPanel.class, "IconPanel").andIsShowing()).isEmpty());
    List<JPanel> iconPanels = Lists.newArrayList(robot().finder().findAll(panel, Matchers.byName(JPanel.class, "IconPanel").andIsShowing()));
    List<String> names = new ArrayList<>();
    for (JPanel iconPanel : iconPanels) {
      JBLabel label = robot().finder().findByType(iconPanel, JBLabel.class);
      ImageComponent image = robot().finder().findByType(iconPanel, ImageComponent.class);
      if (image.getImage() != null && image.getImage().getWidth() > 1 && image.getImage().getHeight() > 1) {
        names.add(label.getText());
      }
    }
    return names;
  }

  @NotNull
  public NewImageAssetStepFixture<W> selectClipArt() {
    JRadioButton button = GuiTests.waitUntilShowingAndEnabled(robot(), target(), Matchers.byText(JRadioButton.class, "Clip Art"));
    if (!button.isSelected())
      new JRadioButtonFixture(robot(), button).select();
    return this;
  }

  @NotNull
  public NewImageAssetStepFixture<W> setForeground(@NotNull Color color) {
    robot().click(robot().finder().findByLabel("Foreground:"));
    ColorPicker colorPicker = GuiTests.waitUntilShowing(robot(), Matchers.byType(ColorPicker.class));
    Collection<JTextField> all = robot().finder().findAll(colorPicker, Matchers.byType(JTextField.class));
    new JTextComponentFixture(robot(), Iterables.get(all, 0)).setText(Integer.toString(color.getRed()));
    new JTextComponentFixture(robot(), Iterables.get(all, 1)).setText(Integer.toString(color.getGreen()));
    new JTextComponentFixture(robot(), Iterables.get(all, 2)).setText(Integer.toString(color.getBlue()));
    JButton button = GuiTests.waitUntilShowing(robot(), Matchers.byText(JButton.class, "Choose").andIsEnabled());
    robot().click(button);
    return this;
  }
}
