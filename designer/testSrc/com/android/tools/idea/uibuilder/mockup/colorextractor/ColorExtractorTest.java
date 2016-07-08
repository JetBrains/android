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
package com.android.tools.idea.uibuilder.mockup.colorextractor;

import com.android.tools.idea.uibuilder.mockup.MockupBaseTest;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class ColorExtractorTest extends MockupBaseTest {

  //private static final String TEST_DIR = getTestDataPath();
  private static final String TEST_DIR = "/Users/caen/testcolor/";
  private static final String FILE = "inbox.png";
  private static Output output;

  private static class Output extends JPanel {
    private static final int SIZE = 60;
    private static final Color DRAWN_PIXEL_COLOR = new Color(255, 0, 0, 127);
    private List<Integer> colors = new ArrayList<>();
    private BufferedImage image;
    private BufferedImage gif;
    private List<int[]> drawnPixel = new ArrayList<>();
    private int myProgress;

    public Output() {
      super(new GridBagLayout());
      setBackground(Color.BLACK);
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (image != null) {
        g.drawImage(image, 0, 0, null);
      }

      if (gif != null) {
        g.drawImage(gif, (image != null ? image.getWidth() : 0) + 1, 0, null);
      }
      g.fillRect(0, getHeight() - SIZE, SIZE, SIZE);
      g.setColor(Color.WHITE);
      g.drawString(myProgress + "%", 0, getHeight() - SIZE);

      if (drawnPixel != null) {
        for (int i = 0; i < drawnPixel.size(); i++) {
          g.setColor(DRAWN_PIXEL_COLOR);
          int[] pixel = drawnPixel.get(i);
          g.fillRect(pixel[0], pixel[1], 1, 1);
        }
      }

      int x, y;
      x = y = 0;
      if (colors != null && !colors.isEmpty()) {
        for (int i = 0; i < colors.size(); i++) {
          if (y + SIZE > image.getHeight()) {
            y = 0;
            x += SIZE;
          }
          Color c = new Color(colors.get(i));
          g.setColor(c);
          int offset = image.getWidth() + 10;
          g.fillRect(offset + x, y, SIZE, SIZE);
          g.setColor(Color.CYAN);
          g.drawString(Integer.toHexString(colors.get(i)), offset + x, y + SIZE / 2);
          y += SIZE;
        }
      }
    }

    public void setProgress(int progress) {
      myProgress = progress;
      repaint();
    }
  }

  public static void main(String[] arg) {
    JFrame jFrame = new JFrame("Color Extractor");

    jFrame.setPreferredSize(new Dimension(1000, 1000));
    output = new Output();

    jFrame.setContentPane(output);
    jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    jFrame.pack();
    jFrame.setVisible(true);
    try {
      BufferedImage image = ImageIO.read(new File(TEST_DIR, FILE));
      output.image = image;
      ColorExtractor colorExtractor = new ColorExtractor(image);
      colorExtractor.run(new ColorExtractor.ColorExtractorCallback() {
        @Override
        public void result(Collection<ExtractedColor> rgbColors) {
          for (ExtractedColor rgbColor : rgbColors) {
            output.colors.add(rgbColor.getColor());
          }
          output.repaint();
        }

        @Override
        public void progress(int progress) {
          output.setProgress(progress);
        }
      });
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}