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

import com.android.tools.idea.io.TestFileUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
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

  // FIXME: Disabled due to flakiness b/64485516
  public void disabled_testIconUpToDate_entryInvalidSavedChanges() {
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
