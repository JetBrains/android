/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.stats;

import static org.junit.Assert.assertEquals;

import com.android.test.testutils.TestUtils;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.download.impl.DownloadableFileDescriptionImpl;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.net.URL;

/**
 * Tests for {@link DistributionService}
 */

public class DistributionServiceTest {
  @Rule
  public ApplicationRule myApplicationRule = new ApplicationRule();

  private static final String DISTRIBUTION_PATH = "tools/adt/idea/android/testData/stats";
  private static final String DISTRIBUTION_FILE = new File(DISTRIBUTION_PATH, "testDistributions.json").getPath();
  private static final File CACHE_PATH = new File(PathManager.getTempPath(), "distributionServiceTest");

  private URL myDistributionFileUrl;

  private Path myDistributionFile;
  private DownloadableFileDescription myDescription;

  @Before
  public void setUp() throws Exception {
    myDistributionFile = TestUtils.resolveWorkspacePath(DISTRIBUTION_FILE);
    myDistributionFileUrl = myDistributionFile.toUri().toURL();
    myDescription = new DownloadableFileDescriptionImpl(myDistributionFileUrl.toString(), DISTRIBUTION_FILE, "json");

    File[] files = CACHE_PATH.listFiles();
    if (files != null) {
      for (File file : files) {
        if (!file.delete()) {
          throw new RuntimeException();
        }
      }
    }
  }

  /**
   * Test that we get back the correct sum for an api level
   */
  @Test
  public void testSimpleCase() throws Exception {
    FileDownloader downloader = Mockito.mock(FileDownloader.class);
    Mockito.when(downloader.download(Mockito.any(File.class)))
      .thenReturn(ImmutableList.of(Pair.create(myDistributionFile.toFile(), myDescription)));
    DistributionService service = new DistributionService(downloader, CACHE_PATH, myDistributionFileUrl);
    assertEquals(0.7, service.getSupportedDistributionForApiLevel(16), 0.0001);
  }

  /**
   * Test that we don't download on every request
   */
  @Test
  public void testCache() throws Exception {
    FileDownloader downloader = Mockito.mock(FileDownloader.class);
    Mockito.when(downloader.download(Mockito.any(File.class)))
      .thenReturn(ImmutableList.of(Pair.create(myDistributionFile.toFile(), myDescription)));
    DistributionService service = new DistributionService(downloader, CACHE_PATH, myDistributionFileUrl);
    service.getSupportedDistributionForApiLevel(19);
    service.getDistributionForApiLevel(21);
    Mockito.verify(downloader).download(Mockito.any(File.class));
  }
}
