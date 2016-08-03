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
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@SuppressWarnings("unused")
public class MockupEditor {

  public static final String TITLE = "Mockup Editor";

  private static final double RELATIVE_SIZE_TO_SOURCE = 0.90;
  private static FrameWrapper ourPopupInstance = null;
  private final Mockup myMockup;
  private final ScreenView myScreenView;

  private JTextField myViewTypeTextField;
  private TextFieldWithBrowseButton myFileChooser;
  private JPanel myCenterPanel;
  private JTextField myTextField1;
  private JPanel myContentPane;
  private JButton myCloseButton;
  private JPanel myCropTool;
  private MockupViewPanel myMockupViewPanel;

  private ExtractWidgetTool myExtractWidgetTool;

  public MockupEditor(ScreenView screenView, Mockup mockup) {
    myMockup = mockup;
    myScreenView = screenView;
    parseMockup(mockup);
    myFileChooser.addActionListener(new FileChooserActionListener());
    myCloseButton.addActionListener(e -> {
      Disposer.dispose(ourPopupInstance);
      ourPopupInstance = null;
    });
  }

  private void createUIComponents() {
    myMockupViewPanel = new MockupViewPanel(myMockup);
    myCenterPanel = myMockupViewPanel;
    myCropTool = new CropTool(myMockup, this);
    myExtractWidgetTool = new ExtractWidgetTool(myMockup, myScreenView, myMockupViewPanel);
    myExtractWidgetTool.enable(myMockupViewPanel);
  }

  @Nullable
  public MockupViewPanel getMockupViewPanel() {
    return myMockupViewPanel;
  }

  /**
   * Disable tool and enable default tool
   *
   * @param tool the tool to disable
   */
  public void disableTool(Tool tool) {
    tool.disable(myMockupViewPanel);
    myExtractWidgetTool.enable(myMockupViewPanel);
  }

  /**
   * Disable default tool and enable tool
   *
   * @param tool the tool to enable
   */
  public void enableTool(Tool tool) {
    myExtractWidgetTool.disable(myMockupViewPanel);
    tool.enable(myMockupViewPanel);
  }

  private void parseMockup(Mockup mockup) {
    final VirtualFile virtualFile = mockup.getVirtualFile();
    if (virtualFile != null) {
      myFileChooser.setText(virtualFile.getPath());
    }
    myViewTypeTextField.setText(mockup.getComponent().getTagName());
  }

  /**
   * Create a popup showing the tools to edit the mockup of the selected component
   */
  public static void create(ScreenView screenView, @Nullable NlComponent component) {
    // Close any pop-up already opened
    if (ourPopupInstance != null) {
      Disposer.dispose(ourPopupInstance);
      ourPopupInstance = null;
    }

    // Do not show the popup if nothing is selected
    if (component == null) {
      return;
    }
    final Mockup mockup = Mockup.create(component, true);
    if (mockup == null) {
      return;
    }

    final DesignSurface designSurface = screenView.getSurface();
    final MockupEditor mockupEditorPopup = new MockupEditor(screenView, mockup);
    Component rootPane = SwingUtilities.getRoot(designSurface);
    final Dimension minSize = new Dimension((int)Math.round(rootPane.getWidth() * RELATIVE_SIZE_TO_SOURCE),
                                            (int)Math.round(rootPane.getHeight() * RELATIVE_SIZE_TO_SOURCE));

    FrameWrapper frame = new FrameWrapper(designSurface.getProject());
    frame.setTitle(TITLE);
    frame.setComponent(mockupEditorPopup.myContentPane);
    frame.setSize(minSize);

    Point point = new Point(
      (int)Math.round(rootPane.getX() + (rootPane.getWidth()) / 2 - minSize.getWidth() / 2),
      (int)Math.round(rootPane.getY() + (rootPane.getHeight()) / 2 - minSize.getHeight() / 2));

    frame.setLocation(point);
    frame.getFrame().setSize(minSize);
    frame.show();
    ourPopupInstance = frame;
  }

  protected JPanel getContentPane() {
    return myContentPane;
  }

  /**
   * Tool used in the mockup editor
   */
  public interface Tool {
    /**
     * The implementing class should set mockupViewPanel to the
     * needed state for itself
     *
     * @param mockupViewPanel The {@link MockupViewPanel} on which the {@link Tool} behave
     */
    void enable(MockupViewPanel mockupViewPanel);

    /**
     * The implementing class should reset mockupViewPanel to the state it was before {@link #enable(MockupViewPanel)}.
     * Can use {@link MockupViewPanel#resetState()}.
     * needed state for itself
     *
     * @param mockupViewPanel The {@link MockupViewPanel} on which the {@link Tool} behave
     */
    void disable(MockupViewPanel mockupViewPanel);
  }

  private class FileChooserActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (myMockup == null) {
        return;
      }
      final FileChooserDescriptor descriptor = MockupFileHelper.getFileChooserDescriptor();
      VirtualFile selectedFile = myMockup.getVirtualFile();

      FileChooser.chooseFile(descriptor, null, myContentPane, selectedFile, (virtualFile) -> {
        MockupFileHelper.writeFileNameToXML(virtualFile, myMockup.getComponent());
        myFileChooser.setText(virtualFile.getName());
      });
    }
  }
}
