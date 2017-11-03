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

import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardStepFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.adtui.ImageComponent;
import com.google.common.collect.Lists;
import com.intellij.ui.components.JBLabel;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class NewImageAssetStepFixture extends AbstractWizardStepFixture<NewImageAssetStepFixture> {

  protected NewImageAssetStepFixture(@NotNull Robot robot,
                                     @NotNull JRootPane target) {
    super(NewImageAssetStepFixture.class, robot, target);
  }

  public void selectIconType(@NotNull String iconType) {
    JComboBox comp = robot().finder().findByLabel(target(), "Icon Type:", JComboBox.class, true);
    JComboBoxFixture combo = new JComboBoxFixture(robot(), comp);
    combo.selectItem(iconType);
  }

  private List<JPanel> getPreviewPanels() {
    return Lists.newArrayList(robot().finder().findAll(Matchers.byName(JPanel.class, "PreviewIconsPanel").andIsShowing()));
  }

  public int getPreviewPanelCount() {
    return getPreviewPanels().size();
  }

  public List<String> getPreviewPanelIconNames(int index) {
    JPanel panel = getPreviewPanels().get(index);
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
}
