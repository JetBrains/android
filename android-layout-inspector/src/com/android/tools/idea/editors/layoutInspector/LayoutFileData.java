/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.editors.layoutInspector;

import com.android.layoutinspector.LayoutInspectorCaptureOptions;
import com.android.layoutinspector.model.ViewNode;
import com.android.utils.FileUtils;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

public class LayoutFileData {
  @Nullable private final BufferedImage myBufferedImage;
  @Nullable private final ViewNode myNode;

  public LayoutFileData(@NotNull File file) throws IOException {
    byte[] previewBytes;
    byte[] bytes = new byte[(int) file.length()];

    try(InputStream stream = new FileInputStream(file)) {
      stream.read(bytes);
    }

    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))){
      // Parse options
      LayoutInspectorCaptureOptions options = new LayoutInspectorCaptureOptions();
      options.parse(input.readUTF());

      // Parse view node
      byte[] nodeBytes = new byte[input.readInt()];
      input.readFully(nodeBytes);
      myNode = ViewNode.parseFlatString(nodeBytes);
      if (getNode() == null) {
        throw new IOException("Error parsing view node");
      }

      // Preview image
      previewBytes = new byte[input.readInt()];
      input.readFully(previewBytes);
    }

    myBufferedImage = ImageIO.read(new ByteArrayInputStream(previewBytes));
  }

  @Nullable
  public BufferedImage getBufferedImage() {
    return myBufferedImage;
  }

  @Nullable
  public ViewNode getNode() {
    return myNode;
  }
}
