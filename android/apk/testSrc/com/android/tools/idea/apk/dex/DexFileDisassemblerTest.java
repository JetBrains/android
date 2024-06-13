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
package com.android.tools.idea.apk.dex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.AndroidTestBase.getTestDataPath;

/**
 * Tests for {@link DexFileDisassembler}.
 */
public class DexFileDisassemblerTest extends HeavyPlatformTestCase {
  private DexFileDisassembler myDisassembler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDisassembler = new DexFileDisassembler();
  }

  public void testDisassemble() throws Throwable {
    VirtualFile outFolder = ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, Throwable>() {
      @Override
      public VirtualFile compute() throws Throwable {
        return VfsUtil.createDirectoryIfMissing(getProject().getBasePath() + "/out");
      }
    });

    File dexFilePath = new File(getTestDataPath(), join("apk", "Test.dex"));

    assertThat(outFolder.getChildren()).isEmpty();
    myDisassembler.disassemble(dexFilePath, virtualToIoFile(outFolder));

    LocalFileSystem.getInstance().refresh(false /* synchronous */);
    assertThat(outFolder.getChildren()).isNotEmpty();

    List<String> smaliFileNames = new ArrayList<>();
    collectSmaliFileNames(outFolder, smaliFileNames);

    assertThat(smaliFileNames).contains("Test.smali");
  }

  private static void collectSmaliFileNames(@NotNull VirtualFile folder, @NotNull List<String> smaliFilePaths) {
    //noinspection UnsafeVfsRecursion
    for (VirtualFile child : folder.getChildren()) {
      if (child.isDirectory()) {
        collectSmaliFileNames(child, smaliFilePaths);
      }
      else if ("smali".equals(child.getExtension())) {
        smaliFilePaths.add(child.getName());
      }
    }
  }
}