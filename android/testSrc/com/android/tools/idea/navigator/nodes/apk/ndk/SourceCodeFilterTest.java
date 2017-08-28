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
package com.android.tools.idea.navigator.nodes.apk.ndk;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.createDirectory;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link SourceCodeFilter}.
 */
public class SourceCodeFilterTest extends IdeaTestCase {
  @Mock private PsiDirectory myItem;

  private VirtualFile mySrcFolder;
  private VirtualFile mySrcCppFolder;
  private VirtualFile mySrcCppIncludeFolder;

  private SourceCodeFilter myCodeFilter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    File srcFolderPath = createTempDir("src");
    mySrcFolder = findFileByIoFile(srcFolderPath, true);

    File srcCppFolderPath = new File(srcFolderPath, "cpp");
    createDirectory(srcCppFolderPath);
    mySrcCppFolder = findFileByIoFile(srcCppFolderPath, true);

    File srcCppIncludeFolderPath = new File(srcCppFolderPath, "include");
    createDirectory(srcCppIncludeFolderPath);
    mySrcCppIncludeFolder = findFileByIoFile(srcCppIncludeFolderPath, true);

    List<String> sourceFolderPaths = Collections.singletonList(srcCppFolderPath.getPath());
    myCodeFilter = new SourceCodeFilter(sourceFolderPaths);
  }

  public void testShouldShow() {
    when(myItem.getVirtualFile()).thenReturn(mySrcFolder);
    assertTrue(myCodeFilter.shouldShow(myItem));

    when(myItem.getVirtualFile()).thenReturn(mySrcCppFolder);
    assertTrue(myCodeFilter.shouldShow(myItem));

    when(myItem.getVirtualFile()).thenReturn(mySrcCppIncludeFolder);
    assertTrue(myCodeFilter.shouldShow(myItem));
  }
}