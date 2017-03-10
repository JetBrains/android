/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.profilers;

import com.android.tools.profilers.stacktrace.FileViewer;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.function.BiConsumer;

public class IntellijFileViewer implements FileViewer {
  private static final Map<String, FileType> FILE_TYPE_MAP = new ImmutableMap.Builder<String, FileType>()
    .put(".html", StdFileTypes.HTML)
    .put(".xml", StdFileTypes.XML)
    .put(".json", FileTypeManager.getInstance().getStdFileType("JSON"))
    .build();
  private static final ImmutableSet<String> IMAGE_EXTENSIONS = ImmutableSet.of(".bmp", ".gif", ".jpeg", ".jpg", ".png");

  @NotNull
  private final JComponent myComponent;
  @Nullable
  private final Dimension myDimension;

  public IntellijFileViewer(@NotNull File file) {
    String fileName = file.getName();
    int dot = fileName.lastIndexOf(".");
    String extension = dot >= 0 && dot < fileName.length() ? fileName.substring(dot) : "";

    BufferedImage image = null;
    if (IMAGE_EXTENSIONS.contains(extension)) {
      try {
        image = ImageIO.read(file);
      } catch (IOException ignore) {}
    }
    if (image != null) {
      myComponent = new ResizableImage(image);
      myDimension = new Dimension(image.getWidth(), image.getHeight());
      return;
    }

    String content = null;
    if (file.exists()) {
      try {
        content = new String(Files.readAllBytes(file.toPath()));
      }
      catch (IOException ignored) {}
    }
    if (content == null) {
      myComponent = new JLabel("No preview available", SwingConstants.CENTER);
      myComponent.setFont(myComponent.getFont().deriveFont(14.f));
      myDimension = null;
      return;
    }

    EditorFactory editorFactory = EditorFactory.getInstance();
    Document document = editorFactory.createDocument(content.toCharArray());
    document.setReadOnly(true);
    EditorEx editor = (EditorEx)editorFactory.createViewer(document);
    editor.setCaretVisible(false);
    editor.getSettings().setLineNumbersShown(false);
    editor.getSettings().setLineMarkerAreaShown(false);
    editor.getSettings().setFoldingOutlineShown(false);
    if (FILE_TYPE_MAP.containsKey(extension)) {
      editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(null, FILE_TYPE_MAP.get(extension)));
    }
    myComponent = editor.getComponent();
    myDimension = null;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Nullable
  @Override
  public Dimension getDimension() {
    return myDimension;
  }

  /**
   * This is an image which can be resized but maintains its aspect ratio.
   */
  public static class ResizableImage extends JLabel {

    private static final int[] LEFT_SHADOW_ALPHAS = {25, 15, 5};
    private static final int[] RIGHT_SHADOW_ALPHAS = {40, 35, 25, 15, 5};
    private static final JBColor CHECKERBOARD_COLOR_MAIN = new JBColor(new Color(201, 201, 201), new Color(51, 54, 55));
    private static final JBColor CHECKERBOARD_COLOR_ALT = new JBColor(new Color(236, 236, 236), new Color(60, 63, 65));


    @NotNull private final BufferedImage myImage;
    @NotNull private Dimension myLastSize;
    @NotNull private Rectangle myViewRectangle;

    /**
     * Check if two dimension objects are basically the same size, plus or minus a pixel. This
     * works around the fact that calculating the rescaled size of an image occasionally produces
     * off-by-one rounding errors, letting us avoid triggering an expensive image regeneration for
     * such a small change.
     */
    private static boolean areSimilarSizes(@NotNull Dimension d1, @NotNull Dimension d2) {
      return Math.abs(d2.width - d1.width) <= 1 && Math.abs(d2.height - d1.height) <= 1;
    }

    public ResizableImage(@NotNull BufferedImage image) {
      super("", CENTER);
      myImage = image;
      myLastSize = new Dimension();
      myViewRectangle = new Rectangle();

      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          resize();
        }
      });
    }

    private boolean hasIcon() {
      Icon icon = getIcon();
      return icon != null && icon.getIconWidth() > 0 && icon.getIconHeight() > 0;
    }

    private void resize() {
      myViewRectangle = getViewRectangle();
      Dimension iconSize = calculateScaledSize();

      if (iconSize.width == 0 || iconSize.height == 0) {
        setIcon(null);
        myLastSize = new Dimension();
      }
      else if (!areSimilarSizes(myLastSize, iconSize)) {
        Image image = iconSize.getWidth() == myImage.getWidth()
                      ? myImage
                      : myImage.getScaledInstance(iconSize.width, iconSize.height, Image.SCALE_SMOOTH);
        setIcon(new ImageIcon(image));
        myLastSize = iconSize;
      }
    }

    @NotNull
    private Dimension calculateScaledSize() {
      int width = getWidth() - RIGHT_SHADOW_ALPHAS.length * 2;
      int height = getHeight() - RIGHT_SHADOW_ALPHAS.length * 2;
      if (width <= 0 || height <= 0) {
        return new Dimension();
      }

      float sourceRatio = (float)myImage.getWidth() / myImage.getHeight();
      int finalWidth = width;
      int finalHeight = (int) (finalWidth / sourceRatio);

      // Don't allow the final size to be larger than the original image, in order to prevent small
      // images from stretching into a blurry mess.
      int maxWidth = Math.min(width, myImage.getWidth());
      int maxHeight = Math.min(height, myImage.getHeight());

      if (finalWidth > maxWidth) {
        float scale = (float)maxWidth / finalWidth;
        finalWidth *= scale;
        finalHeight *= scale;
      }
      if (finalHeight > maxHeight) {
        float scale = (float)maxHeight / finalHeight;
        finalWidth *= scale;
        finalHeight *= scale;
      }

      return new Dimension(finalWidth, finalHeight);
    }

    @Override
    protected void paintComponent(Graphics g) {
      paintShadow(g);
      paintCheckerboard(g);
      super.paintComponent(g);
    }

    private void paintCheckerboard(Graphics g) {
      Rectangle rectangle = hasIcon() ? getIconRectangle() : myViewRectangle;
      int squareSize = 15;
      BiConsumer<Integer, Integer> fillRect = (x, y) -> {
        g.fillRect(rectangle.x + x * squareSize, rectangle.y + y * squareSize,
                   Math.min(squareSize, rectangle.width - x * squareSize),
                   Math.min(squareSize, rectangle.height - y * squareSize));
      };
      int squareX = (int)Math.ceil(rectangle.width / squareSize);
      int squareY = (int)Math.ceil(rectangle.height / squareSize);
      for (int x = 0; x < squareX; x++) {
        g.setColor(CHECKERBOARD_COLOR_MAIN);
        for (int y = x % 2; y < squareY; y += 2) {
          fillRect.accept(x, y);
        }
        g.setColor(CHECKERBOARD_COLOR_ALT);
        for (int y = 1 - x % 2; y < squareY; y += 2) {
          fillRect.accept(x, y);
        }
      }
    }

    private void paintShadow(Graphics g) {
      Rectangle rectangle = hasIcon() ? getIconRectangle() : myViewRectangle;
      if (rectangle.width == 0 || rectangle.height == 0) {
        return;
      }
      // Avoid too much shadow on the left bottom.
      int offset = 2;
      rectangle.width -= offset;
      rectangle.height -= offset;
      for (int i = 0; i < LEFT_SHADOW_ALPHAS.length; i++) {
        g.setColor(new Color(0, 0, 0, LEFT_SHADOW_ALPHAS[i]));
        g.drawRoundRect(rectangle.x - i, rectangle.y - i, rectangle.width + i * 2, rectangle.height + i * 2, i * 2 + 2, i * 2 + 2);
      }
      rectangle.x += offset;
      rectangle.y += offset;
      for (int i = 0; i < RIGHT_SHADOW_ALPHAS.length; i++) {
        g.setColor(new Color(0, 0, 0, RIGHT_SHADOW_ALPHAS[i]));
        g.drawRoundRect(rectangle.x - i, rectangle.y - i, rectangle.width + i * 2, rectangle.height + i * 2, i * 2 + 2, i * 2 + 2);
      }
    }

    @NotNull
    private Rectangle getViewRectangle() {
      Insets insets = getInsets(null);
      Rectangle viewR = new Rectangle();
      viewR.x = insets.left;
      viewR.y = insets.top;
      viewR.width = getWidth() - (insets.left + insets.right);
      viewR.height = getHeight() - (insets.top + insets.bottom);
      return viewR;
    }

    @NotNull
    private Rectangle getIconRectangle() {
      if (getIcon() == null || getIcon().getIconWidth() == 0 || getIcon().getIconHeight() == 0) {
        return new Rectangle();
      }
      Rectangle iconR = new Rectangle();
      SwingUtilities.layoutCompoundLabel(this, getFontMetrics(new Font(null, Font.PLAIN, 10)), getText(), getIcon(), getVerticalAlignment(),
                                         getHorizontalAlignment(), getVerticalTextPosition(), getHorizontalTextPosition(), getViewRectangle(), iconR,
                                         new Rectangle(), getIconTextGap());
      return iconR;
    }
  }
}
