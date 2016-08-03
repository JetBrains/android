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

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.MockupFileHelper;
import com.android.tools.idea.uibuilder.mockup.editor.MockupViewPanel;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

/**
 * Tool Allowing the extraction of widget or layout from the current selection
 */
public class ExtractWidgetTool extends JPanel implements MockupEditor.Tool {

  private final MockupViewPanel myMockupViewPanel;
  private final Mockup myMockup;
  private final ScreenView myScreenView;
  private Rectangle mySelection;
  private MySelectionListener mySelectionListener;

  public ExtractWidgetTool(Mockup mockup, ScreenView screenView, MockupViewPanel mockupViewPanel) {
    super();
    myMockup = mockup;
    myMockupViewPanel = mockupViewPanel;
    myScreenView = screenView;
    mySelectionListener = new MySelectionListener();
    add(createActionButtons());
  }

  // TODO make it look nice
  @Override
  protected void paintComponent(Graphics g) {
  }

  /**
   * Display the buttons of this tool inside the {@link MockupViewPanel} next to selection
   * @param selection the current selection in {@link MockupViewPanel}
   */
  private void displayTooltipActions(Rectangle selection) {
    mySelection = selection;
    myMockupViewPanel.removeAll();
    if (!selection.isEmpty()) {
      myMockupViewPanel.add(this);
      myMockupViewPanel.doLayout();
    }
  }

  /**
   * hide the buttons of this tool inside the {@link MockupViewPanel} next to selection
   */
  private void hideTooltipActions() {
    myMockupViewPanel.remove(this);
  }

  /**
   * Create a new widget of of the size and location of selection
   * @param selection the selection in {@link MockupViewPanel}
   */
  private void createWidget(Rectangle selection) {
    final NlComponent parent = myMockup.getComponent();
    final Rectangle parentCropping = myMockup.getRealCropping();
    final NlModel model = parent.getModel();
    final Path xmlFilePath = MockupFileHelper.getXMLFilePath(myScreenView.getModel().getProject(), myMockup.getFilePath());
    final String stringPath = xmlFilePath != null ? xmlFilePath.toString() : "";

    final WriteCommandAction action = new WriteCommandAction(model.getProject(), "Create widget", model.getFile()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        // For now only create a view at the correct coordinate
        final NlComponent component = model.createComponent(myScreenView, SdkConstants.VIEW, parent, null, InsertType.CREATE);
        component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
                               String.format("%ddp", Coordinates.pxToDp(myScreenView, selection.x)));
        component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
                               String.format("%ddp", Coordinates.pxToDp(myScreenView, selection.y)));
        component.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH,
                               String.format("%ddp", Coordinates.pxToDp(myScreenView, selection.width)));
        component.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT,
                               String.format("%ddp", Coordinates.pxToDp(myScreenView, selection.height)));


        component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP, stringPath);

        // Add the selected part of the mockup as the new mockup of this component
        final Mockup newMockup = Mockup.create(component);
        newMockup.setCropping(parentCropping.x + selection.x, parentCropping.y + selection.y, selection.width, selection.height);
        component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP_POSITION, MockupFileHelper.getPositionString(newMockup));
      }
    };
    action.execute();
  }

  private JComponent createActionButtons() {
    final DefaultActionGroup group = new DefaultActionGroup(new NewWidgetAction(), new NewLayoutAction());
    final ActionToolbar actionToolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.UNKNOWN, group, false);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);

    actionToolbar.setTargetComponent(this);
    return actionToolbar.getComponent();
  }

  @Override
  public void enable(MockupViewPanel mockupViewPanel) {
    mockupViewPanel.addSelectionListener(mySelectionListener);
    mockupViewPanel.resetState();
  }

  @Override
  public void disable(MockupViewPanel mockupViewPanel) {
    mockupViewPanel.removeSelectionListener(mySelectionListener);
    hideTooltipActions();
  }

  /**
   * Action to create the new widget
   */
  class NewWidgetAction extends AnAction {

    public static final String TITLE = "Create new widget from selection";

    public NewWidgetAction() {
      super(TITLE, TITLE, AndroidIcons.Mockup.CreateWidget);
    }


    @Override
    public void actionPerformed(AnActionEvent e) {
      createWidget(mySelection);
    }
  }


  class NewLayoutAction extends AnAction {

    public static final String TITLE = "Create new layout from selection";

    public NewLayoutAction() {
      super(TITLE, TITLE, AndroidIcons.Mockup.CreateLayout);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {

    }
  }

  private class MySelectionListener implements MockupViewPanel.SelectionListener {
    @Override
    public void selectionStarted(MockupViewPanel mockupViewPanel, int x, int y) {
      hideTooltipActions();
    }

    @Override
    public void selectionEnded(MockupViewPanel mockupViewPanel, Rectangle selection) {
      displayTooltipActions(selection);
    }
  }
}
