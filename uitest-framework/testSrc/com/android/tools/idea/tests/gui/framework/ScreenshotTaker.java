package com.android.tools.idea.tests.gui.framework;

import static java.lang.String.format;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.image.ImageFileExtensions.PNG;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.image.ImageException;
import org.fest.swing.image.ImageFileWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScreenshotTaker {
  private final Robot awtRobot;
  private final org.fest.swing.core.Robot festRobot;
  private final ImageFileWriter writer;

  /**
   * Creates a new {@link org.fest.swing.image.ScreenshotTaker}.
   *
   * @throws ImageException if an AWT Robot (the responsible for taking screenshots) cannot be instantiated.
   */
  public ScreenshotTaker(@NotNull org.fest.swing.core.Robot festRobot) {
    this.festRobot = festRobot;
    this.writer = new ImageFileWriter();
    try {
      this.awtRobot = new Robot();
    } catch (AWTException e) {
      throw new ImageException("Unable to create AWT Robot", e);
    }
  }

  /**
   * Takes a screenshot of the desktop and saves it as a PNG file.
   *
   * @param imageFilePath the path of the file to save the screenshot to.
   * @throws NullPointerException if the given file path is {@code null}.
   * @throws IllegalArgumentException if the given file path is empty.
   * @throws IllegalArgumentException if the given file path does not end with ".png".
   * @throws IllegalArgumentException if the given file path belongs to a non-empty directory.
   * @throws RuntimeException if an I/O error prevents the image from being saved as a file.
   */
  public void saveDesktopAsPng(String imageFilePath) {
    BufferedImage image = takeDesktopScreenshot();
    indicatePointerLocation(image);
    saveImage(image, imageFilePath);
  }

  private static void indicatePointerLocation(BufferedImage image) {
    Point mouse = MouseInfo.getPointerInfo().getLocation();
    Graphics g = image.getGraphics();
    g.setColor(Color.RED);
    // drawLine may not work well on Mac OS X retina.
    g.fillRect(mouse.x - 10, mouse.y, 20, 1);
    g.fillRect(mouse.x, mouse.y - 10, 1, 20);
    g.dispose();
  }

  /**
   * Takes a screenshot of the desktop.
   *
   * @return the screenshot of the desktop.
   * @throws SecurityException if {@code readDisplayPixels} permission is not granted.
   */
  private BufferedImage takeDesktopScreenshot() {
    festRobot.waitForIdle();
    return execute(new GuiQuery<BufferedImage>() {
      @Override
      protected @Nullable BufferedImage executeInEDT() {
        Rectangle r = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        return awtRobot.createScreenCapture(r);
      }
    });
  }

  /**
   * Saves the given image as a PNG file.
   *
   * @param image the image to save.
   * @param filePath the path of the file to save the image to.
   * @throws NullPointerException if the given file path is {@code null}.
   * @throws IllegalArgumentException if the given file path is empty.
   * @throws IllegalArgumentException if the given file path does not end with ".png".
   * @throws IllegalArgumentException if the given file path belongs to a non-empty directory.
   * @throws RuntimeException if an I/O error prevents the image from being saved as a file.
   */
  private void saveImage(@NotNull BufferedImage image, @NotNull String filePath) {
    if (!filePath.endsWith(PNG)) {
      throw new IllegalArgumentException(format("The file in path '%s' should have extension 'png'", filePath));
    }
    try {
      writer.writeAsPng(image, filePath);
    } catch (IOException e) {
      throw new RuntimeException(format("Unable to save image as '%s'", filePath), e);
    }
  }
}
