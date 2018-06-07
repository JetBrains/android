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
import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class ApkVirtualFileTest {
  @Test
  public void testCreateFileAtRoot() throws Exception {
    final String FILENAME = "filename";
    VirtualFile file = ApkVirtualFile.create(Paths.get(FILENAME), new byte[0]);
    assertEquals(FILENAME, file.getName());
    assertEquals(null, file.getParent());
  }

  @Test
  public void testCreateFile() throws Exception {
    final String PARENT = "path";
    final String FILENAME = "filename";
    VirtualFile file = ApkVirtualFile.create(Paths.get(PARENT, FILENAME), new byte[0]);
    VirtualFile parent = file.getParent();
    VirtualFile grandParent = parent.getParent();

    assertEquals(FILENAME, file.getName());
    assertEquals(false, file.isDirectory());

    assertEquals(PARENT, parent.getName());
    assertEquals(true, parent.isDirectory());

    assertEquals(null, grandParent);
  }
}

