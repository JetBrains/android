/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.GifWriter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GifWriterTest{
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  public static BufferedImage getImage(int x, int y, int w, int h) {
    BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 100, 100);
    g.setColor(Color.BLUE);
    g.fillRect(x, y, w, h);

    return img;
  }

  public static boolean binaryComp(File file, InputStream stream) throws IOException {
    InputStream is = new FileInputStream(file);

    byte[] data1 = new byte[3200];
    byte[] data2 = new byte[3200];
    while (true) {
      int ret1 = is.read(data1);
      int ret2 = stream.read(data2);
      if (ret1 < 0 || ret2 < 0) {
        return true;
      }
      for (int i = 0; i < ret1; i++) {
        if (data1[i] != data2[i]) {
          return false;
        }
      }
    }
  }

  @Test
  public void testWriteGif() throws IOException {
    BufferedImage[] img = new BufferedImage[10];
    File tmpFile = temporaryFolder.newFile("out.gif");
    GifWriter writer = new GifWriter(tmpFile, 16, true, "test");
    for (int i = 0; i < img.length; i++) {
      writer.addImage(getImage(i * 9 , i * 9, 20, 20));
    }
    writer.close();

    InputStream stream = GifWriterTest.class.getResourceAsStream("test.gif");
    assertTrue(binaryComp(tmpFile, stream));
  }
}