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
package com.android.tools.idea.uibuilder.mockup;

import com.android.tools.idea.uibuilder.mockup.colorextractor.ColorExtractor;
import com.android.tools.idea.uibuilder.mockup.colorextractor.ExtractedColor;
import com.android.tools.idea.uibuilder.mockup.tools.ColorExtractorTool;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

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

  private boolean isExtractingColor;
  private Collection<ExtractedColor> myExtractedColors;
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
  private JButton myExtractButton;
  private JButton myExportButton;
  private JProgressBar myProgressBar1;
  private JPanel myToolPanel;

  public MockupEditorPopup(ScreenView screenView, Mockup mockup, NlModel model) {
    myScreenView = screenView;
    myModel = model;
    myMockup = mockup;

    initFileChooserText();
    initFileChooserActionListener();
    initSlider();
    initShowGuidelineCheckBox();
    initCreateSelectedGuidelineButton();
    initEditCroppingButton();
    initMatchWidgetButton();
    initMatchDeviceButton();
    initColorsComponents();
  }

  private void initColorsComponents() {
    myExtractButton.addActionListener(e -> {
      if(myExtractedColors != null) {
        myToolPanel.removeAll();
        myToolPanel.add(new ColorExtractorTool(myExtractedColors).getToolPanel());
        myToolPanel.revalidate();
        return;
      }

      if (!isExtractingColor) {
        ColorExtractor colorExtractor = new ColorExtractor(myMockup);
        isExtractingColor = true;

        colorExtractor.run(new ColorExtractor.ColorExtractorCallback() {
          @Override
          public void result(Collection<ExtractedColor> rgbColors) {
            myProgressBar1.setValue(100);
            myExportButton.setEnabled(true);
            isExtractingColor = false;
            myExtractedColors = rgbColors;
            myToolPanel.add(new ColorExtractorTool(rgbColors).getToolPanel());
            myToolPanel.revalidate();
          }

          @Override
          public void progress(int progress) {
            myProgressBar1.setValue(progress);
          }
        });
      }
    });

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
   *
   * @param designSurface
   * @param nlModel
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

    //JBPopup builder = JBPopupFactory.getInstance()
    //  .createComponentPopupBuilder(mockupEditorPopup.myContentPane, mockupEditorPopup.myContentPane)
    //  .setTitle(TITLE)
    //  .setResizable(true)
    //  .setMovable(true)
    //  .setMinSize(minSize)
    //  .setRequestFocus(true)
    //  .setCancelOnClickOutside(false)
    //  .setLocateWithinScreenBounds(true)
    //  .setShowShadow(true)
    //  .setCancelOnWindowDeactivation(false)
    //  .setCancelButton(CANCEL_BUTTON)
    //  .createPopup();
    //
    //// Center the popup in the design surface
    //RelativePoint point = new RelativePoint(
    //  designSurface,
    //  new Point(
    //    (int)Math.round(designSurface.getWidth() / 2 - minSize.getWidth() / 2),
    //    (int)Math.round(designSurface.getHeight() / 2 - minSize.getHeight() / 2))
    //);
    //builder.show(point);
    //
    //POPUP_INSTANCE = builder;
  }

  private void createUIComponents() {
    myInteractionPanel = new MockupInteractionPanel(myScreenView, myMockup);
  }
}
