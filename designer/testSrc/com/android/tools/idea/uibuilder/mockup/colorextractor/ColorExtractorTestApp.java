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

import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.uibuilder.LayoutTestCase.getTestDataPath;

public class ColorExtractorTestApp implements ColorExtractor.ColorExtractorCallback {

  private static final String MOCKUP_BIG = "/mockup/mockup.psd";
  private static final String INBOX = "/mockup/inbox.png";
  private static final String GRAY = "/mockup/gray.png";

  private static final String TEST_DIR = getTestDataPath();
  private static final String FILE = INBOX;
  public static final String KMEANS = "Kmeans";
  public static final String DBSCAN = "DBSCAN";
  private BufferedImage myImage;
  private File myFile;

  private JComboBox myClusterer;
  private JTextField myArg1;
  private JTextField myArg2;
  private JTextField myArg3;
  private JTextField myArg4;
  private TextFieldWithBrowseButton myFileChooser;
  private JPanel myColorPanel;
  private JPanel myImagePanel;
  private JPanel myContentPane;
  private JProgressBar myProgressBar;
  private JButton myRunButton;

  public ColorExtractorTestApp() {

    myFileChooser.addActionListener(e -> {
      JFileChooser chooser = new JFileChooser();
      FileNameExtensionFilter filter = new FileNameExtensionFilter(
        "JPG & GIF Images", "jpg", "gif", "png");
      chooser.setFileFilter(filter);
      int returnVal = chooser.showOpenDialog(myContentPane);
      if(returnVal == JFileChooser.APPROVE_OPTION) {
                           myFile = chooser.getSelectedFile();
      }

      if (myFile != null) {
        myFileChooser.setText(myFile.getPath());
      }
    });

    myRunButton.addActionListener(e -> {
      try {
        myImage = ImageIO.read(myFile);
        ColorExtractor colorExtractor;

        if (myClusterer.getSelectedItem().equals(DBSCAN)) {
          float eps = Float.parseFloat(myArg1.getText());
          int minClusterSize = Integer.parseInt(myArg2.getText());
          colorExtractor = new DBSCANColorExtractor(myImage, eps, minClusterSize);
        }
        else {
          int k = Integer.parseInt(myArg1.getText());
          colorExtractor = new KMeansColorExtractor(myImage, k);
        }
        colorExtractor.run(this);
      }
      catch (Exception e1) {
        e1.printStackTrace();
      }
    });
  }

  private void createUIComponents() {
    myImagePanel = new Output();
    myColorPanel = new ColorOutput();
    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(new String[]{ KMEANS, DBSCAN});
    myClusterer = new JComboBox(model);
  }

  @Override
  public void result(Collection<ExtractedColor> rgbColors) {
    ColorOutput colorPanel = (ColorOutput)myColorPanel;
    colorPanel.colors.clear();
    for (ExtractedColor rgbColor : rgbColors) {
      colorPanel.colors.add(rgbColor.getColor());
    }

    Output imagePanel = (Output)myImagePanel;
    imagePanel.image = ImageUtils.createScaledImage(myImage, DoublesColorExtractor.MAX_IMAGE_SIZE);
    imagePanel.gif = ImageUtils.posterize(imagePanel.image, rgbColors);
    myColorPanel.repaint();
    myImagePanel.repaint();
  }

  @Override
  public void progress(int progress) {
    myProgressBar.setValue(progress);
  }

  private static class ColorOutput extends JPanel {
    private static final int SIZE = 60;
    List<Integer> colors = new ArrayList<>();


    /**
     * Paint a square for each color with its value written in it.
     *
     * @param g      The graphic context
     * @param colors The list of rgb color to draw
     * @return the last x coordinate of the last drawn color
     */
    private void drawColors(Graphics g, List<Integer> colors) {
      int x = 0;
      int y = 0;
      if (this.colors != null && !colors.isEmpty()) {
        for (int i = 0; i < this.colors.size(); i++) {
          if (y + SIZE > getHeight()) {
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
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      drawColors(g, colors);
    }
  }

  /**
   * Panel to display the result
   */
  private static class Output extends JPanel {

    private static final Color DRAWN_PIXEL_COLOR = new Color(255, 0, 0, 127);

    private BufferedImage image;
    private BufferedImage gif;
    private List<Integer> drawnPixel = new ArrayList<>();
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
          g.drawImage(myPixelImage, 0, 0, null);
        }
      }

      g.setColor(Color.WHITE);
      if (gif != null) {
        g.drawImage(gif, myPixelImage.getWidth() + 5, 0, null);
      }
    }

    private BufferedImage getParsedPixelImage() {
      myPixelImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D imageGraphics = myPixelImage.createGraphics();
      imageGraphics.setColor(DRAWN_PIXEL_COLOR);
      int[] rgb = myPixelImage.getRGB(0, 0, myPixelImage.getWidth(), myPixelImage.getHeight(), null, 0, myPixelImage.getWidth());
      for (int i = 0; i < drawnPixel.size(); i++) {
        rgb[drawnPixel.get(i)] = 0x88FF0000;
      }
      myPixelImage.setRGB(0, 0, myPixelImage.getWidth(), myPixelImage.getHeight(), rgb, 0, myPixelImage.getWidth());
      return myPixelImage;
    }
  }

  public static void main(String[] arg) {
    ColorExtractorTestApp app = new ColorExtractorTestApp();
    JFrame jFrame = new JFrame("Color Extractor");
    jFrame.setPreferredSize(new Dimension(600, 600));
    jFrame.setContentPane(app.myContentPane);
    jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    jFrame.setAlwaysOnTop(true);
    jFrame.pack();
    jFrame.setLocationRelativeTo(null);
    jFrame.setVisible(true);

    java.awt.EventQueue.invokeLater(() -> {
      jFrame.toFront();
      jFrame.repaint();
    });
  }
}