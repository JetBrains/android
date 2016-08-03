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
package com.android.tools.idea.uibuilder.mockup.old;

import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.MockupFileHelper;
import com.android.tools.idea.uibuilder.mockup.MockupInteractionPanel;
import com.android.tools.idea.uibuilder.mockup.editor.tools.ColorExtractorTool;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Build and display the Mockup Editor dialog
 */
public class MockupEditorPopup {
  public static final String TITLE = "Mockup Editor";
  private static final double RELATIVE_SIZE_TO_SOURCE = 0.90;
  private static final IconButton CANCEL_BUTTON = new IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered);
  private static JBPopup POPUP_INSTANCE = null;
  private final ScreenView myScreenView;
  private final Mockup myMockup;

  NlModel myModel;

  // Form generated components (Do not removed if referenced in the form)
  private TextFieldWithBrowseButton myFileChooser;
  private MockupInteractionPanel myInteractionPanel;
  private JSlider mySlider;
  private JPanel myContentPane;
  private JCheckBox myShowGuideline;
  private JButton myExportGuidelineButton;
  private JButton myEditCroppingButton;
  private JButton myMatchWidgetButton;
  private JButton myMatchDeviceButton;
  private JPanel myToolPanel;
  private JButton mySwitchNext;
  private JPanel myMainPanel;

  public MockupEditorPopup(ScreenView screenView, Mockup mockup, NlModel model) {
    myScreenView = screenView;
    myModel = model;
    myMockup = mockup;

    createUIComponents();
    initFileChooserText();
    initFileChooserActionListener();
    initSlider();
    initShowGuidelineCheckBox();
    initCreateSelectedGuidelineButton();
    initEditCroppingButton();
    initMatchWidgetButton();
    initMatchDeviceButton();

    myMainPanel.add(myInteractionPanel);
    myToolPanel.setLayout(new BorderLayout());
    ColorExtractorTool colorExtractorTool = new ColorExtractorTool(mockup);
    myMainPanel.add(colorExtractorTool.getMainPanel());
    myToolPanel.add(colorExtractorTool.getToolPanel());
    mySwitchNext.addActionListener(e -> ((CardLayout) myMainPanel.getLayout()).next(myMainPanel));
  }

  private void initMatchDeviceButton() {
    if (!myMockup.getComponent().isRoot()) {
      myMatchDeviceButton.getParent().remove(myMatchDeviceButton);
    }
    else {
      myMatchDeviceButton.addActionListener(e -> {
        myMockup.clearCrop();
        MockupFileHelper.writePositionToXML(myMockup);
      });
    }
  }

  private void initMatchWidgetButton() {
    myMatchWidgetButton.addActionListener(e -> {
      myMockup.setDefaultCrop();
      MockupFileHelper.writePositionToXML(myMockup);
    });
  }

  private void initEditCroppingButton() {
    myEditCroppingButton.addActionListener(e -> {
      myInteractionPanel.setEditCropping(!myInteractionPanel.isEditCropping());
      myEditCroppingButton.setText(myInteractionPanel.isEditCropping() ? "End edition" : "Edit cropping");
      myShowGuideline.setSelected(false);
      myShowGuideline.setEnabled(!myInteractionPanel.isEditCropping());
      myInteractionPanel.setShowGuideline(false);
      myExportGuidelineButton.setEnabled(!myInteractionPanel.isEditCropping());
    });
  }

  private void initCreateSelectedGuidelineButton() {
    myExportGuidelineButton.addActionListener(e -> myInteractionPanel.exportSelectedGuidelines());
  }

  private void initShowGuidelineCheckBox() {
    myShowGuideline.addChangeListener(e -> myInteractionPanel.setShowGuideline(((JCheckBox)e.getSource()).isSelected()));
    myInteractionPanel.setShowGuideline(myShowGuideline.isSelected());
  }

  private void initSlider() {
    mySlider.setValue(Math.round(myMockup.getAlpha() * mySlider.getMaximum()));
    mySlider.addMouseListener(new MouseAdapter() {

      @Override
      public void mouseReleased(MouseEvent e) {
        MockupFileHelper.writeOpacityToXML(mySlider.getValue() / (float)mySlider.getMaximum(), myMockup.getComponent());
      }

    });
    mySlider.addChangeListener(e -> {
      final JSlider source = (JSlider)e.getSource();
      myMockup.setAlpha(source.getValue() / (float)source.getMaximum());
      myContentPane.repaint();
    });
  }

  private void initFileChooserActionListener() {
    myFileChooser.addActionListener(e -> {
      if (myMockup == null) {
        return;
      }
      final FileChooserDescriptor descriptor = MockupFileHelper.getFileChooserDescriptor();
      VirtualFile selectedFile = myMockup.getVirtualFile();

      FileChooser.chooseFile(descriptor, null, myContentPane, selectedFile, (virtualFile) -> {
        MockupFileHelper.writeFileNameToXML(virtualFile, myMockup.getComponent());
        myFileChooser.setText(virtualFile.getName());
      });
    });
  }

  private void initFileChooserText() {
    if (myMockup != null) {
      final VirtualFile virtualFile = myMockup.getVirtualFile();
      if (virtualFile != null) {
        myFileChooser.setText(virtualFile.getName());
      }
    }
  }

  /**
   * Create a popup showing the tools to edit the mockup of the selected component
   */
  public static void create(ScreenView screenView, @Nullable NlComponent component) {
    // Close any pop-up already opened
    if (POPUP_INSTANCE != null) {
      POPUP_INSTANCE.cancel();
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
    final MockupEditorPopup mockupEditorPopup = new MockupEditorPopup(screenView, mockup, component.getModel());
    Component rootPane = SwingUtilities.getRoot(designSurface);
    final Dimension minSize = new Dimension((int)Math.round(rootPane.getWidth() * RELATIVE_SIZE_TO_SOURCE),
                                            (int)Math.round(rootPane.getHeight() * RELATIVE_SIZE_TO_SOURCE));

    FrameWrapper jFrame = new FrameWrapper(designSurface.getProject());
    jFrame.setTitle(TITLE);
    jFrame.setComponent(mockupEditorPopup.myContentPane);
    jFrame.setSize(minSize);

    Point point = new Point(
      (int)Math.round(rootPane.getX() + (rootPane.getWidth()) / 2 - minSize.getWidth() / 2),
      (int)Math.round(rootPane.getY() + (rootPane.getHeight()) / 2 - minSize.getHeight() / 2));

    jFrame.setLocation(point);
    jFrame.getFrame().setSize(minSize);
    jFrame.show();
  }

  private void createUIComponents() {
    myInteractionPanel = new MockupInteractionPanel(myScreenView, myMockup);
  }
}
