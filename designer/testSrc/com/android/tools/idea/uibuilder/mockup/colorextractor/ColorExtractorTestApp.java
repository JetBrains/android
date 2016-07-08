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

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import static com.android.tools.idea.uibuilder.LayoutTestCase.getTestDataPath;

public class ColorExtractorTestApp {

  private static final String MOCKUP_BIG = "/mockup/mockup.psd";
  private static final String INBOX = "/mockup/inbox.png";
  private static final String GRAY = "/mockup/gray.png";

  private static final String TEST_DIR = getTestDataPath();
  private static final String FILE = INBOX;

  private static Output output;

  /**
   * Panel to display the result
   */
  private static class Output extends JPanel {
    private static final int SIZE = 60;
    private static final Color DRAWN_PIXEL_COLOR = new Color(255, 0, 0, 127);
    private List<Integer> colors = new ArrayList<>();
    private BufferedImage image;
    private BufferedImage gif;
    private List<Integer>   drawnPixel = new ArrayList<>();
    private int myProgress;
    private BufferedImage myPixelImage;

    public Output() {
      super(new GridBagLayout());
      setBackground(Color.BLACK);
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (image != null) {
        if (drawnPixel != null) {
          myPixelImage = getParsedPixelImage();
        }
        g.drawImage(image, 0, 0, null);
        if (myPixelImage != null) {
          g.drawImage(myPixelImage,0,0,null);
        }
      }

      g.fillRect(0, getHeight() - SIZE, SIZE, SIZE);
      g.setColor(Color.WHITE);
      g.drawString(myProgress + "%", 0, getHeight() - SIZE);

      int x = drawColors(g, colors);
      if (gif != null) {
        g.drawImage(gif, x + SIZE, 0, null);
      }
    }

    /**
     * Paint a square for each color with its value written in it.
     * @param g The graphic context
     * @param colors The list of rgb color to draw
     * @return the last x coordinate of the last drawn color
     */
    private int drawColors(Graphics g, List<Integer> colors) {
      int x = image.getWidth();
      int y = 0;
      if (this.colors != null && !colors.isEmpty()) {
        for (int i = 0; i < this.colors.size(); i++) {
          if (y + SIZE > image.getHeight()) {
            y = 0;
            x += SIZE;
          }
          Color c = new Color(this.colors.get(i));
          g.setColor(c);
          g.fillRect(x, y, SIZE, SIZE);
          g.setColor(Color.CYAN);
          g.drawString(Integer.toHexString(this.colors.get(i)), x, y + SIZE / 2);
          y += SIZE;
        }
      }
      return x;
    }

    private BufferedImage getParsedPixelImage() {
      myPixelImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D imageGraphics = myPixelImage.createGraphics();
      imageGraphics.setColor(DRAWN_PIXEL_COLOR);
      int[] rgb = myPixelImage.getRGB(0, 0, myPixelImage.getWidth(), myPixelImage.getHeight(), null, 0, myPixelImage.getWidth());
      for (int i = 0; i < drawnPixel.size(); i++) {
        rgb[drawnPixel.get(i)] = 0x88FF0000;
      }
      myPixelImage.setRGB(0,0, myPixelImage.getWidth(), myPixelImage.getHeight(), rgb, 0, myPixelImage.getWidth());
      return myPixelImage;
    }

    public void setProgress(int progress) {
      myProgress = progress;
      repaint();
    }
  }

  public static void main(String[] arg) {
    JFrame jFrame = new JFrame("Color Extractor");

    jFrame.setPreferredSize(new Dimension(600, 600));
    output = new Output();

    jFrame.setContentPane(output);
    jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    jFrame.pack();
    jFrame.setVisible(true);

    try {
      BufferedImage image = ImageIO.read(new File(TEST_DIR, FILE));
      output.image = ColorExtractor.createScaledImage(image, ColorExtractor.MAX_IMAGE_SIZE);

      ColorExtractor colorExtractor = new ColorExtractor(output.image);
      ColorExtractor.getLABPixels(output.image, new HashMap<>(), new HashMap<>(), output.drawnPixel);
      colorExtractor.run(new ColorExtractor.ColorExtractorCallback() {
        @Override
        public void result(Collection<ExtractedColor> rgbColors) {
          for (ExtractedColor rgbColor : rgbColors) {
            output.colors.add(rgbColor.getColor());
          }

          // Display posterized version of the image
          output.gif = ColorExtractor.posterize(output.image, rgbColors);
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