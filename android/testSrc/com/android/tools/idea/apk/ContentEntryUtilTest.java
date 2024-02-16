/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.apk;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Tests for {@link ContentEntryUtil}.
 */
public class ContentEntryUtilTest extends HeavyPlatformTestCase {
  public void testFindContentEntryWithFileInContentEntry() {
    List<ContentEntry> contentEntries = new ArrayList<>();
    ContentEntry contentEntry = createContentEntry(getModule());
    contentEntries.add(contentEntry);

    Module module2 = createModule("module2");
    contentEntries.add(createContentEntry(module2));

    File fakeLibraryPath = createFakeLibraryIn(contentEntry);
    ContentEntry found = ContentEntryUtil.findParentContentEntry(fakeLibraryPath, contentEntries.stream());
    assertSame(contentEntry, found);
  }

  public void testFindContentEntryWithFileNotInContentEntry() throws IOException {
    List<ContentEntry> contentEntries = new ArrayList<>();
    contentEntries.add(createContentEntry(getModule()));

    Module module2 = createModule("module2");
    contentEntries.add(createContentEntry(module2));

    // This file exists outside the project. Should be in any content roots.
    File fakeLibraryPath = createFakeLibraryOutsideProject();

    ContentEntry found = ContentEntryUtil.findParentContentEntry(fakeLibraryPath, contentEntries.stream());
    assertNull(found);
  }

  public void testIsPathInContentEntryWithFileInContentEntry() {
    ContentEntry contentEntry = createContentEntry(getModule());
    File fakeLibraryPath = createFakeLibraryIn(contentEntry);
    assertTrue(ContentEntryUtil.isPathInContentEntry(fakeLibraryPath, contentEntry));
  }

  @NotNull
  private static File createFakeLibraryIn(@NotNull ContentEntry contentEntry) {
    VirtualFile contentEntryRootFile = contentEntry.getFile();
    assertNotNull(contentEntryRootFile);
    File folderPath = virtualToIoFile(contentEntryRootFile);
    return new File(folderPath, "fakeLibrary.jar");
  }

  public void testIsPathInContentEntryWithFileNotInContentEntry() throws IOException {
    ContentEntry contentEntry = createContentEntry(getModule());
    File fakeLibraryPath = createFakeLibraryOutsideProject();
    assertFalse(ContentEntryUtil.isPathInContentEntry(fakeLibraryPath, contentEntry));
  }

  @NotNull
  private static File createFakeLibraryOutsideProject() throws IOException {
    return Files.createTempFile("fakeLibrary", ".jar").toFile();
  }

  @NotNull
  private ContentEntry createContentEntry(@NotNull Module module) {
    VirtualFile rootFolder = getTempDir().createVirtualDir();
    ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    ContentEntry contentEntry = rootModel.addContentEntry(rootFolder);
    ApplicationManager.getApplication().runWriteAction(rootModel::commit);
    return contentEntry;
  }
}