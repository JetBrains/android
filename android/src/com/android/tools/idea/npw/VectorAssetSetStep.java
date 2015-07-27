/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.resources.Density;
import com.android.tools.idea.ui.VectorImageComponent;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.NumberFormatter;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;

import static com.android.tools.idea.npw.AssetStudioAssetGenerator.*;

/**
 * Similar to RasterAssetSetStep, this is particular for vector drawable generation.
 */
public class VectorAssetSetStep extends CommonAssetSetStep {
  private static final Logger LOG = Logger.getInstance(VectorAssetSetStep.class);
  public static final String ANDROID_DEFAULT_SIZE = "24";
  public static final int MAX_VECTOR_DRAWABLE_SIZE = 4096;
  public static final int MIN_VECTOR_DRAWABLE_SIZE = 1;

  private JPanel myPanel;
  private JLabel myError;
  private JLabel myDescription;

  private VectorImageComponent myImagePreview;

  private TextFieldWithBrowseButton myImageFile;
  private JLabel myImageFileLabel;
  private JLabel myResourceNameLabel;
  private JTextField myResourceNameField;

  private JPanel myErrorPanel;
  private JLabel myConvertError;
  private HyperlinkLabel myMoreErrors;
  private MoreErrorHyperlinkAdapter myMoreErrorHyperlinkAdapter = new MoreErrorHyperlinkAdapter();
  private JButton myIconPickerButton;
  private JLabel myIconLabel;
  private JPanel myIconPickerPanel;
  private JRadioButton myLocalSVGFilesRadioButton;
  private JRadioButton myMaterialIconsRadioButton;
  private JPanel myImageFileBrowserPanel;
  private JTextField myWidthTextField;
  private JTextField myHeightTextField;
  private JCheckBox myEnableAutoMirroredCheckBox;
  private JPanel myPreviewPanel;
  private JPanel myFilePanel;
  private JPanel myPropertyPanel;
  private JSlider myOpacitySlider;
  private JCheckBox myUseManualSizeCheckBox;
  private JPanel myResizePanel;
  private JPanel mySliderPanel;
  private JLabel myOpacityLabel;
  private JLabel mySizeLabel;
  private JLabel myDpXLabel;
  private JLabel myDpLabel;

  private AbstractAction myEnterKeyButtonAction = new AbstractAction() {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (e.getSource() instanceof JButton) {
        JButton button = (JButton)e.getSource();
        button.doClick();
      }
    }
  };

  @SuppressWarnings("UseJBColor") // Colors are used for the graphics generator, not the plugin UI
  public VectorAssetSetStep(TemplateWizardState state,
                            @Nullable Project project,
                            @Nullable Module module,
                            @Nullable Icon sidePanelIcon,
                            UpdateListener updateListener,
                            @Nullable VirtualFile invocationTarget) {
    super(state, project, module, sidePanelIcon, updateListener, invocationTarget);

    myImageFile.addBrowseFolderListener(null, null, null, FileChooserDescriptorFactory.createSingleFileDescriptor("svg"));

    myTemplateState.put(ATTR_ASSET_TYPE, AssetType.ACTIONBAR.name());
    // TODO: hook up notification type here!
    mySelectedAssetType = AssetType.ACTIONBAR;
    register(ATTR_ASSET_NAME, myResourceNameField);

    myMoreErrors.addHyperlinkListener(myMoreErrorHyperlinkAdapter);
    myErrorPanel.setVisible(false);
    register(ATTR_SOURCE_TYPE, myMaterialIconsRadioButton, AssetStudioAssetGenerator.SourceType.VECTORDRAWABLE);
    register(ATTR_SOURCE_TYPE, myLocalSVGFilesRadioButton, AssetStudioAssetGenerator.SourceType.SVG);

    myEnableAutoMirroredCheckBox.setSelected(false);

    myWidthTextField.setText(ANDROID_DEFAULT_SIZE);
    myHeightTextField.setText(ANDROID_DEFAULT_SIZE);
    myWidthTextField.setEnabled(false);
    myHeightTextField.setEnabled(false);

    register(ATTR_VECTOR_DRAWBLE_WIDTH, myWidthTextField);
    register(ATTR_VECTOR_DRAWBLE_HEIGHT, myHeightTextField);
    register(ATTR_VECTOR_DRAWBLE_OPACTITY, myOpacitySlider);
    register(ATTR_VECTOR_DRAWBLE_AUTO_MIRRORED, myEnableAutoMirroredCheckBox);

    // Support both mouse and enter key.
    myIconPickerButton.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "DoClick");
    myIconPickerButton.getActionMap().put("DoClick", myEnterKeyButtonAction);
    myIconPickerButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        displayVectorIconDialog();
      }
    });

    myTemplateState.put(ATTR_ORIGINAL_WIDTH, 0);
    myTemplateState.put(ATTR_ORIGINAL_HEIGHT, 0);

    // Use item listener instead of action listener to make sure this is
    // triggered by setSelect() call, too.
    myUseManualSizeCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent event) {
        // When the override is checked, we use the original size such that the
        // user can get the original aspect ratio if they want.
        // Otherwise, we use the default vector size.
        String finalWidthString = ANDROID_DEFAULT_SIZE;
        String finalHeightString = ANDROID_DEFAULT_SIZE;
        if (event.getStateChange() == ItemEvent.SELECTED) {
          int originalWidth = myTemplateState.getInt(ATTR_ORIGINAL_WIDTH);
          int originalHeight = myTemplateState.getInt(ATTR_ORIGINAL_HEIGHT);
          if (originalWidth > 0 && originalHeight > 0) {
            finalWidthString = String.valueOf(originalWidth);
            finalHeightString = String.valueOf(originalHeight);
          }
          myWidthTextField.setEnabled(true);
          myHeightTextField.setEnabled(true);
        } else {
          myWidthTextField.setEnabled(false);
          myHeightTextField.setEnabled(false);
        }
        myWidthTextField.setText(finalWidthString);
        myHeightTextField.setText(finalHeightString);
      }
    });
  }

  public class MoreErrorHyperlinkAdapter extends HyperlinkAdapter {
    private String mErrorLog;

    public void setErrorMessage(String error) {
      mErrorLog = error;
    }

    @Override
    protected void hyperlinkActivated(HyperlinkEvent e) {
      // create a JTextArea to contain the error message.
      JTextArea textArea = new JTextArea(25, 80);
      textArea.setText(mErrorLog);
      textArea.setEditable(false);
      textArea.setCaretPosition(0);

      // wrap a scrollpane around the text
      JScrollPane scrollPane = new JScrollPane(textArea);

      // display them in a message dialog
      JOptionPane.showMessageDialog(myPanel, scrollPane);
    }
  }

  /**
   * @return true if the path for the current source type (vector drawable or SVG) is
   *              empty or null.
   */
  private boolean isVectorPathEmpty() {
    SourceType sourceType = (SourceType) myTemplateState.get(ATTR_SOURCE_TYPE);
    boolean isPathEmpty = false;
    if (sourceType == SourceType.SVG) {
      if (myTemplateState.hasAttr(ATTR_IMAGE_PATH)) {
        String path = myTemplateState.getString(ATTR_IMAGE_PATH);
        if (path == null || path.isEmpty()) {
          isPathEmpty = true;
        }
      } else {
        isPathEmpty = true;
      }
    } else {
      if (myTemplateState.hasAttr(ATTR_VECTOR_LIB_ICON_PATH)) {
        String path = myTemplateState.getString(ATTR_VECTOR_LIB_ICON_PATH);
        if (path == null || path.isEmpty()) {
          isPathEmpty = true;
        }
      } else {
        isPathEmpty = true;
      }
    }
    return isPathEmpty;
  }

  @Override
  public void deriveValues() {
    super.deriveValues();
    if (!myTemplateState.myModified.contains(ATTR_ASSET_NAME)) {
      updateDerivedValue(ATTR_ASSET_NAME, myResourceNameField, new Callable<String>() {
        @Override
        public String call() throws Exception {
          return computeResourceName();
        }
      });
    }

    // If the path for the vector asset is empty, then reset and disable the controls
    // in the properties panel.
    boolean isPathEmpty = isVectorPathEmpty();
    if (isPathEmpty) {
      togglePropertiesPanel(false);
    } else {
      togglePropertiesPanel(true);
    }

    if (myMaterialIconsRadioButton.isSelected()) {
      show(myIconPickerPanel, myIconLabel);
      hide(myImageFileBrowserPanel, myImageFileLabel);
    }
    else {
      assert myLocalSVGFilesRadioButton.isSelected();
      show(myImageFileBrowserPanel, myImageFileLabel);
      hide(myIconPickerPanel, myIconLabel);
    }
  }

  private void togglePropertiesPanel(boolean enable) {
    if (!enable) {
      // De-select the checkboxs and reset slider before disabling them.
      if (myUseManualSizeCheckBox.isSelected()) {
        myUseManualSizeCheckBox.setSelected(false);
      }
      if (myEnableAutoMirroredCheckBox.isSelected()) {
        myEnableAutoMirroredCheckBox.setSelected(false);
      }
      if (myOpacitySlider.getValue() != 100) {
        myOpacitySlider.setValue(100);
      }
    }
    myUseManualSizeCheckBox.setEnabled(enable);
    myEnableAutoMirroredCheckBox.setEnabled(enable);
    myOpacitySlider.setEnabled(enable);
    myOpacityLabel.setEnabled(enable);
    myDpXLabel.setEnabled(enable);
    myDpLabel.setEnabled(enable);
    mySizeLabel.setEnabled(enable);
  }

  @Override
  protected void updatePreviewImages() {
    if (!(mySelectedAssetType == null || myImageMap == null || myImageMap.size() == 0)) {
      // The error message is generated during the preview generation.
      // Therefore, it is natural to update the error message here.
      final String errorMessage = (String)myTemplateState.get(ATTR_ERROR_LOG);
      if (Strings.isNullOrEmpty(errorMessage)) {
        myErrorPanel.setVisible(false);
        myIsValid = true;
      } else {
        myErrorPanel.setVisible(true);
        myIsValid = setupErrorMessages(errorMessage);
      }

      final BufferedImage previewImage = getImage(myImageMap, Density.ANYDPI.getResourceValue());
      setIconOrClear(myImagePreview, previewImage);
    } else {
      myIsValid = false;
      setIconOrClear(myImagePreview, null);
    }

    myUpdateListener.update();
  }

  /**
   * We will always show the first line of errorMessage. If there are more errors, we will show a
   * underlined text as "More...". When it is clicked, we will show more lines.
   * At the same time, we also parse the errorMessage to decide whether going to the next step.
   * Basically, if the preview image is empty, we disable the next step.
   *
   * @return whether or not the preview image is valid.
   */
  private boolean setupErrorMessages(String errorMessage) {
    boolean isPreviewValid = !errorMessage.startsWith(ERROR_MESSAGE_EMPTY_PREVIEW_IMAGE);
    int firstLineBreak = errorMessage.indexOf("\n");
    boolean moreErrors = firstLineBreak > 0 && firstLineBreak < errorMessage.length() - 1;
    String firstLineError = moreErrors ? errorMessage.substring(0, firstLineBreak) : errorMessage;
    myConvertError.setText(firstLineError);
    if (moreErrors) {
      myMoreErrors.setVisible(true);
      myMoreErrors.setHyperlinkText("More...");
      myMoreErrorHyperlinkAdapter.setErrorMessage(errorMessage);
    }
    else {
      myMoreErrors.setVisible(false);
    }
    return isPreviewValid;
  }

  @Nullable
  private static BufferedImage getImage(@NotNull Map<String, Map<String, BufferedImage>> map, @NotNull String name) {
    final Map<String, BufferedImage> images = map.get(name);
    if (images == null) {
      return null;
    }

    final Collection<BufferedImage> values = images.values();
    return values.isEmpty() ? null : values.iterator().next();
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  protected void initialize() {
    register(ATTR_IMAGE_PATH, myImageFile);
  }

  @NotNull
  @Override
  protected String computeResourceName() {
    String resourceName = null;
    if (resourceName == null) {
      resourceName = String.format("ic_vector_name", "name");
    }

    if (drawableExists(resourceName)) {
      // While uniqueness isn't satisfied, increment number and add to end
      int i = 1;
      while (drawableExists(resourceName + Integer.toString(i))) {
        i++;
      }
      resourceName += Integer.toString(i);
    }

    return resourceName;
  }

  @Override
  protected void generateAssetFiles(File targetResDir) {
    myAssetGenerator.outputXmlToRes(targetResDir);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myIconPickerButton;
  }

  @NotNull
  @Override
  protected JLabel getDescription() {
    return myDescription;
  }

  @NotNull
  @Override
  protected JLabel getError() {
    return myError;
  }

  private void displayVectorIconDialog() {
    DialogBuilder builder = new DialogBuilder(myPanel);
    // TODO: Set up listener for the OK button status change from IconPicker.
    IconPicker ip = new IconPicker(builder);
    builder.setCenterPanel(ip);
    builder.setTitle("Select Icon");
    if (!builder.showAndGet()) {
      return;
    }

    myTemplateState.put(ATTR_VECTOR_LIB_ICON_PATH, ip.getSelectIcon().getURL().getPath());
    update();
  }
}
