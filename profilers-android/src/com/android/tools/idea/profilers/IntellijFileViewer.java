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

import com.android.tools.profilers.common.FileViewer;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
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

    @NotNull private final BufferedImage myImage;
    @Nullable private Dimension myLastSize;

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

      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          resize();
        }
      });
    }

    private void resize() {
      Dimension d = calculateScaledSize();

      if (d.width == 0 || d.height == 0) {
        setIcon(null);
        myLastSize = null;
      }
      else if (myLastSize == null || !areSimilarSizes(myLastSize, d)) {
        Image image = d.getWidth() == myImage.getWidth() ? myImage : myImage.getScaledInstance(d.width, d.height, Image.SCALE_SMOOTH);
        setIcon(new ImageIcon(image));
        myLastSize = d;
      }
    }

    @NotNull
    private Dimension calculateScaledSize() {
      if (getWidth() == 0 || getHeight() == 0) {
        return new Dimension();
      }

      float sourceRatio = (float)myImage.getWidth() / myImage.getHeight();
      int finalWidth = getWidth();
      int finalHeight = (int) (finalWidth / sourceRatio);

      // Don't allow the final size to be larger than the original image, in order to prevent small
      // images from stretching into a blurry mess.
      int maxWidth = Math.min(getWidth(), myImage.getWidth());
      int maxHeight = Math.min(getHeight(), myImage.getHeight());

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
  }
}
