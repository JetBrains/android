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
package com.android.tools.idea.tests.gui.framework.fixture.avdmanager;

import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardStepFixture;
import com.intellij.ui.table.TableView;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.exception.ActionFailedException;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static org.fest.swing.data.TableCellInRowByValue.rowWithValue;

public class ChooseSystemImageStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ChooseSystemImageStepFixture, W> {

  protected ChooseSystemImageStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ChooseSystemImageStepFixture.class, wizard, target);
  }

  @NotNull
  public ChooseSystemImageStepFixture<W> selectSystemImage(@NotNull SystemImage image) {
    final TableView systemImageList = robot().finder().findByType(target(), TableView.class, true);
    JTableFixture systemImageListFixture = new JTableFixture(robot(), systemImageList);

    Wait.seconds(5).expecting("The system image list is populated.").until(() -> {
      try {
        systemImageListFixture.cell(rowWithValue(image.getReleaseName(),
                                                 image.getApiLevel(),
                                                 image.getAbiType(),
                                                 image.getTargetName()).column(0)).select();
        return true;
      } catch (ActionFailedException e) {
        return false;
      }
    });
    return this;
  }

  @NotNull
  public ChooseSystemImageStepFixture<W> selectTab(@NotNull final String tabName) {
    Component tabLabel = robot().finder().find(target(), JLabelMatcher.withText(tabName));
    robot().click(tabLabel);

    return this;
  }

  public static class SystemImage {
    private String releaseName;
    private String apiLevel;
    private String abiType;
    private String targetName;

    public SystemImage(@NotNull String releaseName, @NotNull String apiLevel, @NotNull String abiType, @NotNull String targetName) {
      this.releaseName = releaseName;
      this.apiLevel = apiLevel;
      this.abiType = abiType;
      this.targetName = targetName;
    }

    @NotNull
    public String getReleaseName() {
      return releaseName;
    }

    @NotNull
    public String getApiLevel() {
      return apiLevel;
    }

    @NotNull
    public String getAbiType() {
      return abiType;
    }

    @NotNull
    public String getTargetName() {
      return targetName;
    }
  }

}
