/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.mockup.tools;

import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.MockupFileHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Tools used in the mockup editor
 */
public final class Tools {

  public interface Tool {
  }

  /**
   * Tool handling the the cropping of the mockup
   */
  public static class CropTool extends JPanel implements Tool {

    private final JBLabel myCropLabel = new JBLabel("0,0 0x0", SwingConstants.CENTER);
    private final MockupViewPanel myMockupViewPanel;
    boolean myActive;

    public CropTool(Mockup mockup, MockupViewPanel mockupViewPanel) {
      super(new BorderLayout());
      setCropLabel(mockup);
      mockup.addMockupModelListener(this::setCropLabel);
      add(myCropLabel, BorderLayout.CENTER);
      add(createCropButton(), BorderLayout.EAST);
      myMockupViewPanel = mockupViewPanel;
      myMockupViewPanel.addSelectionListener(saveSelectionToMockup(mockup));
    }

    @NotNull
    private static MockupViewPanel.SelectionListener saveSelectionToMockup(Mockup mockup) {
      return selection -> {
        mockup.setCropping(selection.x, selection.y, selection.width, selection.height);
        MockupFileHelper.writePositionToXML(mockup);
      };
    }

    /**
     * Display an {@link ActionGroup} with the button to activate the cropping
     *
     * @return
     */
    private JComponent createCropButton() {
      final ActionToolbar actionToolbar = ActionManager.getInstance()
        .createActionToolbar(ActionPlaces.UNKNOWN, new DefaultActionGroup(new ToggleCrop()), true);
      actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
      return actionToolbar.getComponent();
    }

    /**
     * Set the text of the crop label using the value of {@link Mockup#getRealCropping()}
     *
     * @param mockup
     */
    private void setCropLabel(Mockup mockup) {
      final Rectangle cropping = mockup.getRealCropping();
      final String cropString = String.format("%d,%d %dx%d", cropping.x, cropping.y, cropping.width, cropping.height);
      UIUtil.invokeLaterIfNeeded(() -> myCropLabel.setText(cropString));
    }

    /**
     * ToggleButton to toggle the crop mode
     */
    private class ToggleCrop extends ToggleAction {
      public ToggleCrop() {
        getTemplatePresentation().setIcon(AndroidIcons.Mockup.Crop);
      }

      @Override
      public boolean isSelected(AnActionEvent e) {
        return myActive;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myActive = state;
        myMockupViewPanel.setSelectionMode(myActive);
        myMockupViewPanel.setDisplayOnlyCroppedRegion(!myActive);
        myMockupViewPanel.setSelectionToMockupCrop();
      }
    }
  }
}
