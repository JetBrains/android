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
package com.android.tools.idea.rendering;

import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.io.TestFileUtils;
import org.jetbrains.android.AndroidTestCase;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.truth.Truth.assertThat;

public class GutterIconFactoryTest extends AndroidTestCase {

  private Path mySampleXmlPath;

  private static final int XML_MAX_WIDTH = 50, XML_MAX_HEIGHT = 60;
  private static final String XML_CONTENTS_FORMAT = "<vector android:height=\"%2$ddp\""
                                                    + " android:width=\"%1$ddp\""
                                                    + " xmlns:android=\"http://schemas.android.com/apk/res/android\"> "
                                                    + "</vector>";

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySampleXmlPath = FileSystems.getDefault().getPath(myModule.getProject().getBasePath(),
                                                       "app", "src", "main", "res", "drawable", "GutterIconTest_sample.xml");
  }

  public void testCreateIcon_XmlScalingMeetsSizeConstraints() throws Exception {
    for (int i = -1; i <= 1; i++) {
      for (int j = -1; j <= 1; j++) {
        int width = XML_MAX_WIDTH + i;
        int height = XML_MAX_HEIGHT + j;

        TestFileUtils.writeFileAndRefreshVfs(mySampleXmlPath, String.format(XML_CONTENTS_FORMAT, width, height));

        Icon icon = GutterIconFactory.createIcon(mySampleXmlPath.toString(), null, XML_MAX_WIDTH, XML_MAX_HEIGHT);

        assertThat(icon).isNotNull();
        assertThat(icon.getIconWidth()).isAtMost(XML_MAX_WIDTH);
        assertThat(icon.getIconHeight()).isAtMost(XML_MAX_HEIGHT);
      }
    }
  }

  public void testCreateIcon_BitmapBigEnough() throws Exception {
    String path = Paths.get(getTestDataPath(), "render/imageutils/actual.png").toString();
    BufferedImage input = ImageIO.read(new File(path));
    // Sanity check.
    assertThat(input.getHeight()).isGreaterThan(GutterIconCache.MAX_HEIGHT);
    assertThat(input.getWidth()).isGreaterThan(GutterIconCache.MAX_WIDTH);

    Icon icon = GutterIconFactory.createIcon(path, null, GutterIconCache.MAX_WIDTH, GutterIconCache.MAX_HEIGHT);
    assertThat(icon).isNotNull();
    assertThat(icon.getIconWidth()).isAtMost(GutterIconCache.MAX_WIDTH);
    assertThat(icon.getIconHeight()).isAtMost(GutterIconCache.MAX_HEIGHT);
  }

  public void testCreateIcon_BitmapSmallAlready() throws Exception {
    String path = Paths.get(getTestDataPath(), "annotator/ic_tick_thumbnail.png").toString();
    BufferedImage input = ImageIO.read(new File(path));
    // Sanity check.
    assertThat(input.getHeight()).isAtMost(GutterIconCache.MAX_HEIGHT);
    assertThat(input.getWidth()).isAtMost(GutterIconCache.MAX_WIDTH);

    Icon icon = GutterIconFactory.createIcon(path, null, GutterIconCache.MAX_WIDTH, GutterIconCache.MAX_HEIGHT);
    assertThat(icon).isNotNull();
    BufferedImage output = TestRenderingUtils.getImageFromIcon(icon);

    // Input and output should be identical.
    ImageDiffUtil.assertImageSimilar(getName(), input, output, 0);
  }
}
