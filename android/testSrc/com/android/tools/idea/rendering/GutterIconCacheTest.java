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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.android.AndroidResourceRenameResourceProcessor;
import org.jetbrains.android.AndroidTestCase;
import org.junit.Before;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;

import static com.google.common.truth.Truth.assertThat;

public class GutterIconCacheTest extends AndroidTestCase {
  private Path mySampleSvgPath;
  private VirtualFile mySampleSvgFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySampleSvgPath = FileSystems.getDefault().getPath(myModule.getProject().getBasePath(),
                                                       "app", "src", "main", "res", "drawable", "GutterIconCacheTest_sample.xml");

    String contents = "<svg viewBox=\"0 0 50 50\"><rect width=\"50\" height=\"50\" fill=\"blue\"/></svg>";
    mySampleSvgFile = TestFileUtils.writeFileAndRefreshVfs(mySampleSvgPath, contents);
  }

  public void testCreateBitmapIcon_bigEnough() throws Exception {
    BufferedImage input = ImageIO.read(new File(getTestDataPath(), "render/imageutils/actual.png"));
    // Sanity check.
    assertThat(input.getHeight()).isGreaterThan(GutterIconCache.MAX_HEIGHT);
    assertThat(input.getWidth()).isGreaterThan(GutterIconCache.MAX_WIDTH);

    Icon icon = GutterIconCache.createBitmapIcon(input);
    assertThat(icon).isNotNull();
    assertThat(icon.getIconWidth()).isAtMost(GutterIconCache.MAX_WIDTH);
    assertThat(icon.getIconHeight()).isAtMost(GutterIconCache.MAX_HEIGHT);
  }

  public void testCreateBitmapIcon_smallAlready() throws Exception {
    BufferedImage input = ImageIO.read(new File(getTestDataPath(), "annotator/ic_tick_thumbnail.png"));
    // Sanity check.
    assertThat(input.getHeight()).isAtMost(GutterIconCache.MAX_HEIGHT);
    assertThat(input.getWidth()).isAtMost(GutterIconCache.MAX_WIDTH);

    Icon icon = GutterIconCache.createBitmapIcon(input);
    assertThat(icon).isNotNull();
    BufferedImage output = TestRenderingUtils.getImageFromIcon(icon);

    // Input and output should be identical.
    ImageDiffUtil.assertImageSimilar(getName(), input, output, 0);
  }

  public void testIsIconUpToDate_entryInvalidNotCached() {
    // Use constructor instead of statically-loaded instance to ensure fresh cache
    GutterIconCache cache = new GutterIconCache();

    // If we've never requested an Icon for the path, there should be no valid cache entry.
    assertThat(cache.isIconUpToDate(mySampleSvgPath.toString())).isFalse();
  }

  public void testIsIconUpToDate_entryValid() {
    GutterIconCache.getInstance().getIcon(mySampleSvgPath.toString(), null);

    // If we haven't modified the image since creating an Icon, the cache entry is still valid
    assertThat(GutterIconCache.getInstance().isIconUpToDate(mySampleSvgPath.toString())).isTrue();
  }

  public void testIsIconUpToDate_entryInvalidUnsavedChanges() {
    GutterIconCache.getInstance().getIcon(mySampleSvgPath.toString(), null);

    // "Modify" Document by rewriting its contents
    Document document = FileDocumentManager.getInstance().getDocument(mySampleSvgFile);
    ApplicationManager.getApplication().runWriteAction(() -> document.setText(document.getText()));

    // Modifying the image should have invalidated the cache entry.
    assertThat(GutterIconCache.getInstance().isIconUpToDate(mySampleSvgPath.toString())).isFalse();
  }

  public void testIconUpToDate_entryInvalidSavedChanges() {
    GutterIconCache.getInstance().getIcon(mySampleSvgPath.toString(), null);

    // Modify image resource by adding an empty comment and then save to disk
    Document document = FileDocumentManager.getInstance().getDocument(mySampleSvgFile);
    ApplicationManager.getApplication().runWriteAction(() -> {
      document.setText(document.getText() + "<!---->");
      FileDocumentManager.getInstance().saveDocument(document);
    });

    // Modifying the image should have invalidated the cache entry.
    assertThat(GutterIconCache.getInstance().isIconUpToDate(mySampleSvgPath.toString())).isFalse();
  }

  public void testIconUpToDate_entryInvalidDiskChanges() throws Exception {
    GutterIconCache.getInstance().getIcon(mySampleSvgPath.toString(), null);

    // "Modify" file by resetting its lastModified field
    Files.setLastModifiedTime(mySampleSvgPath, FileTime.fromMillis(System.currentTimeMillis()));
    mySampleSvgFile.refresh(false, false);

    // Modifying the image should have invalidated the cache entry.
    assertThat(GutterIconCache.getInstance().isIconUpToDate(mySampleSvgPath.toString())).isFalse();
  }
}
