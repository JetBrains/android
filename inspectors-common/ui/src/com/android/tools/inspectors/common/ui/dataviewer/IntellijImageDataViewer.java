/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.inspectors.common.ui.dataviewer;

import com.android.tools.adtui.stdui.ResizableImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntellijImageDataViewer implements ImageDataViewer {
  @NotNull
  private final BufferedImage myImage;
  @NotNull
  private final JComponent myComponent;

  /**
   * Return an image-viewing data viewer, unless the passed in {@code content} bytes are invalid or
   * represent an unknown image type, in which case, {@code null} is returned.
   */
  @Nullable
  public static IntellijImageDataViewer createImageViewer(@NotNull byte[] content) {
    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
      BufferedImage image = ImageIO.read(inputStream);
      if (image == null) {
        return null;
      }

      return new IntellijImageDataViewer(image);
    }
    catch (IOException e) {
      return null;
    }
  }

  private IntellijImageDataViewer(@NotNull BufferedImage image) {
    myImage = image;
    myComponent = new ResizableImage(image);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @NotNull
  @Override
  public BufferedImage getImage() {
    return myImage;
  }
}