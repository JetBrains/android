/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.wizard;

import com.android.assetstudiolib.GraphicGenerator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ColorPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import static com.android.tools.idea.wizard.LauncherIconWizardState.*;

/**
 * LauncherIconStep is a page in the New Project wizard that lets the user optionally create a customized launcher icon for the project
 * being created.
 */
public class LauncherIconStep extends TemplateWizardStep {
  private static final Logger LOG = Logger.getInstance("#" + LauncherIconStep.class.getName());
  private static final int CLIPART_ICON_SIZE = 32;
  private static final int CLIPART_DIALOG_BORDER = 10;
  private static final int DIALOG_HEADER = 20;

  private LauncherIconWizardState myWizardState;
  private JPanel myPanel;
  private JRadioButton myImageRadioButton;
  private JRadioButton myClipartRadioButton;
  private JRadioButton myTextRadioButton;
  private JRadioButton myCropRadioButton;
  private JRadioButton myCenterRadioButton;
  private JRadioButton myCircleRadioButton;
  private JRadioButton mySquareRadioButton;
  private JRadioButton myNoneRadioButton;
  private JButton myChooseClipart;
  private JLabel myError;
  private JLabel myDescription;
  private JCheckBox myTrimBlankSpace;
  private JTextField myText;
  private JComboBox myFontFamily;
  private TextFieldWithBrowseButton myImageFile;
  private ColorPanel myBackgroundColor;
  private ColorPanel myForegroundColor;
  private ImageComponent myMDpiPreview;
  private ImageComponent myHDpiPreview;
  private ImageComponent myXHDpiPreview;
  private JSlider myPaddingSlider;
  private ImageComponent myXXHdpiPreview;
  private JLabel myImageFileLabel;
  private JLabel myTextLabel;
  private JLabel myFontFamilyLabel;
  private JLabel myChooseClipartLabel;
  private JLabel myBackgroundColorLabel;
  private JLabel myForegroundColorLabel;

  public LauncherIconStep(LauncherIconWizardState state, @Nullable Project project, @Nullable Icon sidePanelIcon,
                          UpdateListener updateListener) {
    super(state, project, sidePanelIcon, updateListener);
    myWizardState = state;

    register(ATTR_TEXT, myText);
    register(ATTR_SCALING, myCropRadioButton, Scaling.CROP);
    register(ATTR_SCALING, myCenterRadioButton, Scaling.CENTER);
    register(ATTR_SHAPE, myCircleRadioButton, GraphicGenerator.Shape.CIRCLE);
    register(ATTR_SHAPE, mySquareRadioButton, GraphicGenerator.Shape.SQUARE);
    register(ATTR_SHAPE, myNoneRadioButton, GraphicGenerator.Shape.NONE);
    register(ATTR_PADDING, myPaddingSlider);
    register(ATTR_TRIM, myTrimBlankSpace);
    register(ATTR_FONT, myFontFamily);
    register(ATTR_SOURCE_TYPE, myImageRadioButton, LauncherIconWizardState.SourceType.IMAGE);
    register(ATTR_SOURCE_TYPE, myClipartRadioButton, LauncherIconWizardState.SourceType.CLIPART);
    register(ATTR_SOURCE_TYPE, myTextRadioButton, LauncherIconWizardState.SourceType.TEXT);
    register(ATTR_IMAGE_PATH, myImageFile);
    register(ATTR_FOREGROUND_COLOR, myForegroundColor);
    register(ATTR_BACKGROUND_COLOR, myBackgroundColor);

    myImageFile.addBrowseFolderListener(null, null, null, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    myForegroundColor.setSelectedColor(Color.BLUE);
    myBackgroundColor.setSelectedColor(Color.WHITE);

    for (String font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
      myFontFamily.addItem(new ComboBoxItem(font, font, 1, 1));
      if (font.equals(myWizardState.get(ATTR_FONT))) {
        myFontFamily.setSelectedIndex(myFontFamily.getItemCount() - 1);
      }
    }

    myChooseClipart.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        displayClipartDialog();
      }
    });
  }

  @Override
  public boolean validate() {
    if (!super.validate()) {
      return false;
    }

    myFontFamily.setSelectedItem((String)myWizardState.get(ATTR_FONT));

    if (myImageRadioButton.isSelected()) {
      hide(myChooseClipart, myChooseClipartLabel, myText, myTextLabel, myFontFamily, myFontFamilyLabel, myForegroundColor,
           myForegroundColorLabel);
      show(myImageFile, myImageFileLabel, myBackgroundColor, myBackgroundColorLabel);
    } else if (myClipartRadioButton.isSelected()) {
      hide(myText, myTextLabel, myFontFamily, myFontFamilyLabel, myImageFile, myImageFileLabel);
      show(myChooseClipart, myChooseClipartLabel, myBackgroundColor, myBackgroundColorLabel,myForegroundColor, myForegroundColorLabel);
    } else if (myTextRadioButton.isSelected()) {
      hide(myChooseClipart, myChooseClipartLabel, myImageFile, myImageFileLabel);
      show(myText, myTextLabel, myFontFamily, myFontFamilyLabel, myBackgroundColor, myBackgroundColorLabel, myForegroundColor,
           myForegroundColorLabel);
    }

    try {
      Map<String, Map<String, BufferedImage>> imageMap = myWizardState.generateImages(true);
      final BufferedImage mdpi = getImage(imageMap, "mdpi");
      final BufferedImage hdpi = getImage(imageMap, "hdpi");
      final BufferedImage xhdpi = getImage(imageMap, "xhdpi");
      final BufferedImage xxhdpi = getImage(imageMap, "xxhdpi");

      if (mdpi == null || hdpi == null || xhdpi == null || xxhdpi == null) {
        throw new ImageGeneratorException("Unable to generate launcher icon");
      }

      myMDpiPreview.setIcon(new ImageIcon(mdpi));
      myHDpiPreview.setIcon(new ImageIcon(hdpi));
      myXHDpiPreview.setIcon(new ImageIcon(xhdpi));
      myXXHdpiPreview.setIcon(new ImageIcon(xxhdpi));

      return true;
    }
    catch (ImageGeneratorException e) {
      setErrorHtml(e.getMessage());
      return false;
    }
  }

  /**
   * Displays a modal dialog with one button for each entry in the {@link GraphicGenerator} clipart library. Clicking on a button sets that
   * entry into the {@link ATTR_CLIPART_NAME} parameter.
   */
  private void displayClipartDialog() {
    Window window = SwingUtilities.getWindowAncestor(myPanel);
    final JDialog dialog = new JDialog(window, Dialog.ModalityType.DOCUMENT_MODAL);
    FlowLayout layout = new FlowLayout();
    dialog.getRootPane().setLayout(layout);
    int count = 0;
    for (Iterator<String> iter = GraphicGenerator.getClipartNames(); iter.hasNext(); ) {
      final String name = iter.next();
      try {
        JButton btn = new JButton();

        btn.setIcon(new ImageIcon(GraphicGenerator.getClipartIcon(name)));
        Dimension d = new Dimension(CLIPART_ICON_SIZE, CLIPART_ICON_SIZE);
        btn.setMaximumSize(d);
        btn.setPreferredSize(d);
        btn.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            myWizardState.put(ATTR_CLIPART_NAME, name);
            dialog.setVisible(false);
            update();
          }
          });
        dialog.getRootPane().add(btn);
        count++;
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    int size = (int)(Math.sqrt(count) + 1) * (CLIPART_ICON_SIZE + layout.getHgap()) + CLIPART_DIALOG_BORDER * 2;
    dialog.setSize(size, size + DIALOG_HEADER);
    dialog.setLocationRelativeTo(window);
    dialog.setVisible(true);
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
  public JComponent getPreferredFocusedComponent() {
    return myImageRadioButton;
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
}
