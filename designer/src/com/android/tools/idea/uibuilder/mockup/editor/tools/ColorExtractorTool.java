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
import com.android.tools.idea.uibuilder.mockup.colorextractor.ColorExtractor;
import com.android.tools.idea.uibuilder.mockup.colorextractor.DBSCANColorExtractor;
import com.android.tools.idea.uibuilder.mockup.colorextractor.ExtractedColor;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Tool for the mockup editor displaying the control to extract and save the color
 * from the mockup
 */
@SuppressWarnings("UndesirableClassUsage")
public class ColorExtractorTool implements MockupTool, ColorPanel.ColorHoveredListener {

  private final Mockup myMockup;
  private JButton myExtractButton;
  private JButton myExportButton;
  private JProgressBar myProgressBar;
  private JPanel myOutput;
  private JPanel myTools;
  private Collection<ExtractedColor> myExtractedColors;
  private boolean myIsExtractingColor;
  private final JPanel myColors;
  private final MainPanel myMainPanel;
  private Map<Integer, BufferedImage> myImageCache;
  private BufferedImage myImage;
  private BufferedImage mySelectedColorImage;

  public ColorExtractorTool(Mockup mockup) {
    myMockup = mockup;
    myImageCache = new HashMap<>();
    myMainPanel = new MainPanel();

    myMockup.addMockupListener((mockup1, changedFlags) -> myImage = mockup1.getImage());

    myColors = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 5, true, false));
    JComponent scrollPane = new JBScrollPane(myColors,
                                             ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                             ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myOutput.add(scrollPane);
    initColorsComponents();
    myImage = myMockup.getImage();
  }

  private void initColorsComponents() {
    myExtractButton.addActionListener(e -> {
      if (myExtractedColors != null) {
        myColors.removeAll();
      }

      if (!myIsExtractingColor) {
        ColorExtractor colorExtractor = new DBSCANColorExtractor(myMockup);
        myIsExtractingColor = true;

        colorExtractor.run(new ColorExtractor.ColorExtractorCallback() {
          @Override
          public void result(Collection<ExtractedColor> rgbColors) {
            myProgressBar.setValue(100);
            myExportButton.setEnabled(true);
            myIsExtractingColor = false;
            myExtractedColors = rgbColors;

            for (ExtractedColor color : rgbColors) {
              ColorPanel colorPanel = new ColorPanel(color);
              colorPanel.addHoveredListener(ColorExtractorTool.this);
              JPanel component = colorPanel.getComponent();
              component.setMaximumSize(component.getPreferredSize());
              myColors.add(component, 0);
            }
            myColors.revalidate();
          }

          @Override
          public void progress(int progress) {
            myProgressBar.setValue(progress);
          }
        });
      }
    });
  }

  @Override
  public JComponent getToolPanel() {
    return myTools;
  }

  @Override
  public JComponent getMainPanel() {
    return myMainPanel;
  }

  @Override
  public String getTitle() {
    return "Color Extractor";
  }

  @Override
  public void entered(ExtractedColor color) {
    if (myExtractedColors != null) {
      mySelectedColorImage = mask(myImage, color);
      myMainPanel.repaint();
    }
  }

  @Override
  public void exited() {
    mySelectedColorImage = null;
    myMainPanel.repaint();
  }

  private BufferedImage mask(BufferedImage image, ExtractedColor hoveredColor) {
    if (myImageCache.containsKey(hoveredColor.getColor())) {
      return myImageCache.get(hoveredColor.getColor());
    }

    BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    int width = image.getWidth();
    int height = image.getHeight();
    int[] imagePixels = image.getRGB(0, 0, width, height, null, 0, width);

    for (int i = 0; i < imagePixels.length; i++) {
      int color = imagePixels[i];
      if (!hoveredColor.getNeighborColor().contains(color)) {
        // Display a semi-transparent pixel colored in the selected color
        imagePixels[i] = average(hoveredColor.getColor(), color) & 0x00FFFFFF | 100 << 24; // Apply alpha
      }
    }
    copy.setRGB(0, 0, width, height, imagePixels, 0, width);

    myImageCache.put(hoveredColor.getColor(), copy);
    return copy;
  }

  public static int average(int a, int b) {

    float outA = ((b >> 24) & 0xff) / 255.0f;
    float outR = ((b >> 16) & 0xff) / 255.0f;
    float outG = ((b >> 8) & 0xff) / 255.0f;
    float outB = (b & 0xff) / 255.0f;

    float colorA = ((a >> 24) & 0xff) / 255.0f;
    float colorR = ((a >> 16) & 0xff) / 255.0f;
    float colorG = ((a >> 8) & 0xff) / 255.0f;
    float colorB = (a & 0xff) / 255.0f;

    outA += colorA;
    outR += colorR;
    outG += colorG;
    outB += colorB;

    outA /= 2;
    outR /= 2;
    outG /= 2;
    outB /= 2;

    outA *= 255.0f;
    outR = (float)Math.pow(outR, 1.0 / 2.2) * 255.0f;
    outG = (float)Math.pow(outG, 1.0 / 2.2) * 255.0f;
    outB = (float)Math.pow(outB, 1.0 / 2.2) * 255.0f;

    return ((int)outA) << 24 | ((int)outR) << 16 | ((int)outG) << 8 | (int)outB;
  }

  class MainPanel extends JPanel {

    public static final int CHESS_BOARD_SQUARE = 10;
    private AffineTransform myTransform = new AffineTransform();

    @Override
    public void paint(Graphics g) {
      Graphics2D g2D = (Graphics2D)g.create();
      float scale = getHeight() / (float)myImage.getHeight();
      myTransform.setToIdentity();
      myTransform.scale(scale, scale);
      myTransform.translate(getWidth() / 2.0 - (myImage.getWidth() * scale) / 2.0, 0);

      g2D.setTransform(myTransform);
      //drawChessboard(g2D);
      g2D.fillRect(0,0,myImage.getWidth(), myImage.getHeight());
      g2D.drawImage(mySelectedColorImage != null ? mySelectedColorImage : myImage, 0, 0, null);
      g2D.dispose();
    }

    private void drawChessboard(Graphics2D g2D) {
      for (int x = 0; x < myImage.getWidth(); x += CHESS_BOARD_SQUARE) {
        for (int y = 0; y < myImage.getHeight(); y += CHESS_BOARD_SQUARE) {
          int xModulo = x % (CHESS_BOARD_SQUARE * 2);
          int yModulo = y % (CHESS_BOARD_SQUARE * 2);
          if (xModulo == 0 && yModulo == 0
              || xModulo != 0 && yModulo != 0) {
            g2D.setColor(JBColor.BLACK);
          }
          else {
            g2D.setColor(Gray._50);
          }
          g2D.fillRect(x, y, CHESS_BOARD_SQUARE, CHESS_BOARD_SQUARE);
        }
      }
    }
  }
}
