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
package com.android.tools.idea.apk.viewer;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.BinaryLightVirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Implementation of an in-memory {@link VirtualFile} that correctly implements the {#getParent()} method.
 */
class ApkVirtualFile {
  private ApkVirtualFile() {
  }

  @Nullable public static VirtualFile create(@NotNull Path path, @NotNull byte[] content) {
    Path fileName = path.getFileName();
    if (fileName == null) {
      return null;
    }
    Path parent = path.getParent();
    boolean isBinary = true; // Default to binary for files inside an APK
    if (path.toString().matches("/META-INF/.*\\.version")) {
      isBinary = false;
    }

    if (isBinary) {
      return new BinaryLightVirtualFile(fileName.toString(), content) {
        @Override
        public VirtualFile getParent() {
          return ApkVirtualFolder.getDirectory(parent);
        }
      };
    } else {
      return new LightVirtualFile(fileName.toString(), new String(content, StandardCharsets.UTF_8)) {
        @Override
        public VirtualFile getParent() {
          return ApkVirtualFolder.getDirectory(parent);
        }
      };
    }
  }
}
