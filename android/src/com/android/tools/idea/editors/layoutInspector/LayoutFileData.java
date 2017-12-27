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
package com.android.tools.idea.editors.layoutInspector;

import com.android.layoutinspector.LayoutInspectorCaptureOptions;
import com.android.layoutinspector.model.ViewNode;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

public class LayoutFileData {
  @Nullable public final BufferedImage myBufferedImage;
  @Nullable public final ViewNode myNode;

  public LayoutFileData(@NotNull VirtualFile file) throws IOException {
    byte[] previewBytes;

    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(file.contentsToByteArray()));

    try {
      // Parse options
      LayoutInspectorCaptureOptions options = new LayoutInspectorCaptureOptions();
      options.parse(input.readUTF());

      // Parse view node
      byte[] nodeBytes = new byte[input.readInt()];
      input.readFully(nodeBytes);
      myNode = ViewNode.parseFlatString(nodeBytes);
      if (myNode == null) {
        throw new IOException("Error parsing view node");
      }

      // Preview image
      previewBytes = new byte[input.readInt()];
      input.readFully(previewBytes);
    }
    finally {
      input.close();
    }

    myBufferedImage = ImageIO.read(new ByteArrayInputStream(previewBytes));
  }
}
