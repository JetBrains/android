/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.ide.common.util.PathString;
import com.android.testutils.TestResources;
import com.android.tools.apk.analyzer.ArchiveContext;
import com.android.tools.apk.analyzer.ArchiveNode;
import com.android.tools.apk.analyzer.Archives;
import com.android.tools.apk.analyzer.internal.GzipSizeCalculator;
import com.android.utils.StdLogger;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class ApkParserTest {
  /**
   * 2 min should be plenty enough. We have seen 5 sec as being not enough on some
   * test machines, probably due to slow I/O.
   */
  private static final long TIMEOUT_SECONDS = 120;

  @Test
  public void parserWorksForApk() throws Exception {
    PathString archivePath = getArchivePath("test.apk");
    checkArchive(archivePath);
  }

  @Test
  public void parserWorksForAppBundle() throws Exception {
    PathString archivePath = getArchivePath("bundle.aab");
    checkArchive(archivePath);
  }

  @Test
  public void parserWorksForZipBundle() throws Exception {
    PathString archivePath = getArchivePath("bundle.zip");
    checkArchive(archivePath);
  }

  private static void checkArchive(@NotNull PathString archivePath) throws Exception {
    try (ArchiveContext archiveContext = Archives.open(archivePath.toPath(), new StdLogger(StdLogger.Level.VERBOSE))) {
      ApkParser parser = new ApkParser(archiveContext, new GzipSizeCalculator());

      ListenableFuture<ArchiveNode> futureTree = parser.constructTreeStructure();
      ArchiveNode tree = futureTree.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      assertNotNull(tree);

      ListenableFuture<Long> futureSize = parser.getCompressedFullApkSize();
      Long size = futureSize.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      assertNotNull(size);
      assertTrue(size > 0);

      ListenableFuture<Long> futureApkSize = parser.getUncompressedApkSize();
      Long apkSize = futureApkSize.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      assertNotNull(apkSize);
      assertTrue(apkSize > 0);

      ListenableFuture<ArchiveNode> futureTree2 = parser.updateTreeWithDownloadSizes();
      ArchiveNode tree2 = futureTree2.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      assertNotNull(tree2);

      parser.cancelAll();
    }
  }

  @NotNull
  private static PathString getArchivePath(@NotNull String s) {
    return new PathString(TestResources.getFile("/" + s));
  }
}
