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
import com.android.tools.idea.uibuilder.mockup.editor.MockupEditor;
import com.android.tools.idea.uibuilder.mockup.editor.MockupViewPanel;
import com.android.tools.idea.uibuilder.mockup.editor.SelectionLayer;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.intellij.openapi.actionSystem.*;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Tool handling the the cropping of the mockup
 */
public class CropTool extends JPanel implements MockupEditor.Tool {

  private final MockupEditor myMockupEditor;
  @Nullable private Mockup myMockup;
  private final MockupViewPanel.SelectionListener mySelectionListener;
  private boolean myActive;
  private MockupViewPanel myMockupViewPanel;
  private MatchComponentRatio myMatchComponentRatioAction;


  public CropTool(MockupEditor mockupEditor) {
    super(new FlowLayout(FlowLayout.RIGHT, 3, 1));
    myMockupEditor = mockupEditor;
    myMockupViewPanel = mockupEditor.getMockupViewPanel();
    myMockupEditor.addListener(this::update);
    add(createCropButton());
    update(mockupEditor.getMockup());
    mySelectionListener = new MySelectionListener();
  }

  private void update(@Nullable Mockup mockup) {
    myMockup = mockup;
    setCropLabel(mockup);
  }

  private static void saveSelectionToMockup(@NotNull Rectangle selection, @NotNull Mockup mockup) {
    mockup.setCropping(selection.x, selection.y, selection.width, selection.height);
    MockupFileHelper.writePositionToXML(mockup);
  }

  /**
   * Create an {@link ActionGroup} with the button to activate the cropping
   *
   * @return the component of the newly created ActionToolbar
   */
  @NotNull
  private JComponent createCropButton() {
    myMatchComponentRatioAction = new MatchComponentRatio();
    final DefaultActionGroup group = new DefaultActionGroup(new ToggleCrop(), myMatchComponentRatioAction);
    final ActionToolbar actionToolbar = ActionManager.getInstance()
      .createActionToolbar("AndroidDesignerCropTool", group, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    return actionToolbar.getComponent();
  }

  /**
   * Set the text of the crop label using the value of {@link Mockup#getComputedCropping()}
   *
   * @param mockup
   */
  private void setCropLabel(@Nullable Mockup mockup) {
    if (mockup != null) {
      UIUtil.invokeLaterIfNeeded(() -> myMockupEditor.setSelectionText(mockup.getComputedCropping()));
    }
  }

  @Override
  public void enable(@NotNull MockupEditor mockupEditor) {
    myActive = true;
    myMockupViewPanel.addSelectionListener(mySelectionListener);
    myMockupViewPanel.setDisplayOnlyCroppedRegion(false);
    myMockupViewPanel.setSelectionMode(true);
    myMockupViewPanel.setSelectionToMockupCrop();
    setCropActionsEnabled(false);
    update(mockupEditor.getMockup());
    mockupEditor.addBottomControls(createApplyButton());
  }

  @NotNull
  private JComponent createApplyButton() {
    JPanel panel = new JPanel();
    JButton apply = new JButton("Finish Crop");
    apply.addActionListener(e -> myMockupEditor.disableTool(this));
    panel.add(new JLabel("<html>Select area to fit inside the selected component</html>"));
    panel.add(apply);
    return panel;
  }

  @Override
  public void disable(@NotNull MockupEditor mockupEditor) {
    myActive = false;
    setCropActionsEnabled(false);
    myMockupViewPanel.removeSelectionListener(mySelectionListener);
    myMockupViewPanel.resetState();
  }

  /**
   * Set if the action in myBottomActionGroup are enabled or not
   */
  private void setCropActionsEnabled(boolean enabled) {
    myMatchComponentRatioAction.getTemplatePresentation().setEnabled(enabled);
    myMatchComponentRatioAction.setSelected(null, false);
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
      //Enable or disable itself in the mockup editor
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

    public static final String TITLE = "Match component ratio";

    public MatchComponentRatio() {
      super(TITLE, TITLE, AndroidIcons.Mockup.MatchWidget);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myActive && myMockup != null);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myActive && myMockupViewPanel != null && myMockupViewPanel.getSelectionLayer().isFixedRatio();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (myMockup == null) {
        return;
      }
      NlComponent component = myMockup.getComponent();
      SelectionLayer selectionLayer = myMockupViewPanel.getSelectionLayer();
      selectionLayer.setFixedRatio(state);
      if (state) {
        // Set the aspect ratio of the current selection to the same as the component
        selectionLayer.setAspectRatio(NlComponentHelperKt.getW(component), NlComponentHelperKt.getH(component));
        saveSelectionToMockup(selectionLayer.getSelection(), myMockup);
      }
    }
  }

  private class MySelectionListener implements MockupViewPanel.SelectionListener {
    @Override
    public void selectionStarted(MockupViewPanel mockupViewPanel, int x, int y) {
    }

    @Override
    public void selectionEnded(MockupViewPanel mockupViewPanel, Rectangle selection) {
      if (myMockup != null) {
        saveSelectionToMockup(selection, myMockup);
      }
    }
  }
}
