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

import com.android.tools.idea.ui.resourcechooser.ColorPicker;
import com.android.tools.idea.uibuilder.mockup.colorextractor.ExtractedColor;
import com.android.tools.idea.util.ListenerCollection;
import com.intellij.ui.ColorPickerListener;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;


@SuppressWarnings("UseJBColor")
public class ColorPanel {
  public static final JBColor BACKGROUND = new JBColor(JBColor.background().darker(), JBColor.background().brighter());
  private final ExtractedColor myExtractedColor;
  private JTextField myColorCode;
  private JTextField myColorName;
  private JButton mySaveButton;
  private JButton mySetAsBackgroundButton;
  private JPanel myColorFrame;
  private JPanel myContentPane;
  private final ListenerCollection<ColorHoveredListener> myListeners = ListenerCollection.createWithDirectExecutor();

  public ColorPanel(ExtractedColor extractedColor) {
    Color color = new Color(extractedColor.getColor());
    initColorFrame(color);
    myExtractedColor = extractedColor;
    myColorCode.setText(getColorHexString(extractedColor.getColor()));
    myColorName.setText(findColorName(extractedColor.getColor()));
    initSaveButton();
    initBackgroundButton();
    myContentPane.setBackground(BACKGROUND);
  }

  private String getColorHexString(int rgb) {
    return String.format(Locale.US, "#%05X", rgb);
  }

  private void initColorFrame(Color color) {
    myColorFrame.setBackground(color);
    myColorFrame.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
        ColorPicker.showDialog(myContentPane, "Edit color",color, true,
                               new ColorPickerListener[] {new ColorPickerListener() {
                                 @Override
                                 public void colorChanged(Color color) {

                                 }

                                 @Override
                                 public void closed(@Nullable Color color) {
                                   if (color != null) {
                                     myColorName.setText(getColorHexString(color.getRGB()));
                                   }
                                 }
                               }}, true);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        myListeners.forEach(l -> l.entered(myExtractedColor));
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myListeners.forEach(ColorHoveredListener::exited);
      }
    });
  }

  private void initBackgroundButton() {
    mySetAsBackgroundButton.setOpaque(false);

  }

  private void initSaveButton() {
    mySaveButton.setOpaque(false);
  }

  private String findColorName(int color) {
    return "Color name"; // TODO find name in a dictionary
  }

  public JPanel getComponent() {
    return myContentPane;
  }

  public void addHoveredListener(ColorHoveredListener listener) {
    myListeners.add(listener);
  }

  interface ColorHoveredListener {
    void entered(ExtractedColor color);
    void exited();
  }
}
