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
package com.android.tools.idea.uibuilder.mockup.editor.tools;

import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.MockupFileHelper;
import com.android.tools.idea.uibuilder.mockup.editor.MockupViewPanel;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Tool handling the the cropping of the mockup
 */
public class CropTool extends JPanel implements MockupEditor.Tool {

  private final JBLabel myCropLabel = new JBLabel("0,0 0x0", SwingConstants.CENTER);
  private final MockupEditor myMockupEditor;
  private Mockup myMockup;
  private final MockupViewPanel.SelectionListener mySelectionListener;
  boolean myActive;
  private MockupViewPanel myMockupViewPanel;
  private DefaultActionGroup myBottomActionGroup;

  public CropTool(Mockup mockup, MockupEditor mockupEditor) {
    super(new BorderLayout());
    myMockupEditor = mockupEditor;
    myMockupEditor.addListener(this::update);
    update(mockup);
    add(myCropLabel, BorderLayout.CENTER);
    add(createCropButton(), BorderLayout.EAST);
    add(createBottomToolBar(), BorderLayout.SOUTH);
    mySelectionListener = new MySelectionListener();
  }

  private void update(@NotNull Mockup mockup) {
    if(myMockup != null) {
      myMockup.removeMockupListener(this::setCropLabel);
    }
    myMockup = mockup;
    myMockup.addMockupListener(this::setCropLabel);
    setCropLabel(mockup);
  }

  private Component createBottomToolBar() {
    myBottomActionGroup = new DefaultActionGroup(new MatchComponentRatio());
    final ActionToolbar actionToolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.UNKNOWN, myBottomActionGroup, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    return actionToolbar.getComponent();
  }

  private void saveSelectionToMockup(Rectangle selection) {
    myMockup.setCropping(selection.x, selection.y, selection.width, selection.height);
    MockupFileHelper.writePositionToXML(myMockup);
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
    final String cropString = String.format("%d,%d %dx%d",
                                            cropping.x, cropping.y, cropping.width, cropping.height);
    UIUtil.invokeLaterIfNeeded(() -> myCropLabel.setText(cropString));
    final MockupViewPanel mockupViewPanel = myMockupEditor.getMockupViewPanel();
    if (mockupViewPanel != null) {
      mockupViewPanel.repaint();
    }
  }

  @Override
  public void enable(MockupViewPanel mockupViewPanel) {
    myActive = true;
    mockupViewPanel.addSelectionListener(mySelectionListener);
    mockupViewPanel.setDisplayOnlyCroppedRegion(false);
    mockupViewPanel.setSelectionMode(true);
    mockupViewPanel.setSelectionToMockupCrop();
    setBottomToolbarEnabled(false);
    myMockupViewPanel = mockupViewPanel;
  }

  @Override
  public void disable(MockupViewPanel mockupViewPanel) {
    myActive = false;
    setBottomToolbarEnabled(false);
    mockupViewPanel.removeSelectionListener(mySelectionListener);
    mockupViewPanel.resetState();
  }

  /**
   * Set if the action in myBottomActionGroup are enabled or not
   */
  private void setBottomToolbarEnabled(boolean enabled) {
    final AnAction[] children = myBottomActionGroup.getChildren(null);
    for (int i = 0; i < children.length; i++) {
      children[i].getTemplatePresentation().setEnabled(enabled);
    }
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
      if (state) {
        myMockupEditor.enableTool(CropTool.this);
      }
      else {
        myMockupEditor.disableTool(CropTool.this);
      }
    }
  }

  /**
   * Set selection to match component Aspect Ratio
   */
  private class MatchComponentRatio extends ToggleAction {

    public MatchComponentRatio() {
      getTemplatePresentation().setIcon(AndroidIcons.Mockup.MatchWidget);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myActive);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myMockupViewPanel != null && myMockupViewPanel.getSelectionLayer().isFixedRatio();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      final NlComponent component = myMockup.getComponent();
      myMockupViewPanel.getSelectionLayer().setFixedRatio(state);
      if (state) {
        // Set the aspect ratio of the current selection to the same as the component
        myMockupViewPanel.getSelectionLayer().setAspectRatio(component.w, component.h);
      }
    }
  }

  private class MySelectionListener implements MockupViewPanel.SelectionListener {
    @Override
    public void selectionStarted(MockupViewPanel mockupViewPanel, int x, int y) {
    }

    @Override
    public void selectionEnded(MockupViewPanel mockupViewPanel, Rectangle selection) {
      CropTool.this.saveSelectionToMockup(selection);
    }
  }
}
