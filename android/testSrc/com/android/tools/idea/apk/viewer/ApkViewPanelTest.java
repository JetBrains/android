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

import com.android.tools.apk.analyzer.*;
import org.jetbrains.android.AndroidTestBase;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ApkViewPanelTest {

  @Test
  public void getFirstManifestArchive_apk() throws IOException {
    Archive archive = Archives.open(getArchivePath("1.apk"));
    ArchiveNode node = ArchiveTreeStructure.create(archive);
    Archive archive2 = ApkViewPanel.getFirstManifestArchive(node);
    assertEquals(archive, archive2);
  }

  @Test
  public void getFirstManifestArchive_bundle() throws IOException {
    Archive archive = Archives.open(getArchivePath("bundle.zip"));
    ArchiveNode node = ArchiveTreeStructure.create(archive);
    Archive archive2 = ApkViewPanel.getFirstManifestArchive(node);
    assertNotEquals(archive, archive2);
    assertEquals(node.getChildren().get(0).getData().getArchive(), archive2);
  }

  private static Path getArchivePath(String s) {
    return Paths.get(AndroidTestBase.getTestDataPath(), "apk/" + s);
  }
}