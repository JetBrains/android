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

package com.android.tools.idea.editors.layeredimage;

import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.PixelProbe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

class Utilities {
  private Utilities() {
  }

  @NotNull
  static Image loadImage(@NotNull VirtualFile file) throws IOException {
    try (InputStream in = file.getInputStream()) {
      return PixelProbe.probe(in);
    }
  }

  @NotNull
  static BufferedImage getDisplayableImage(@NotNull Image image) throws IOException {
    BufferedImage bufferedImage = image.getMergedImage();
    if (bufferedImage == null) {
      throw new IOException("Unable to extract flattened bitmap");
    }
    return bufferedImage;
  }
}
