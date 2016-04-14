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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.concurrency.FutureResult;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.download.impl.DownloadableFileDescriptionImpl;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for {@link DistributionService}
 */

public class DistributionServiceTest extends AndroidTestCase {
  private static final String DISTRIBUTION_FILE_NAME = "testDistributions.json";
  private static final File CACHE_PATH = new File(PathManager.getTempPath(), "distributionServiceTest");

  private URL myDistributionFileUrl;

  private File myDistributionFile;
  private DownloadableFileDescription myDescription;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDistributionFileUrl = getClass().getResource(DISTRIBUTION_FILE_NAME);

    myDistributionFile = new File(myDistributionFileUrl.toURI());
    myDescription = new DownloadableFileDescriptionImpl(myDistributionFileUrl.toString(), DISTRIBUTION_FILE_NAME, "json");

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
  public void testSimpleCase() throws Exception {
    FileDownloader downloader = Mockito.mock(FileDownloader.class);
    Mockito.when(downloader.download(Matchers.any(File.class)))
      .thenReturn(ImmutableList.of(Pair.create(myDistributionFile, myDescription)));
    DistributionService service = new DistributionService(downloader, CACHE_PATH, myDistributionFileUrl);
    assertEquals(0.7, service.getSupportedDistributionForApiLevel(16), 0.0001);
  }

  /**
   * Test that we don't download on every request
   *
   * @throws Exception
   */
  public void testCache() throws Exception {
    FileDownloader downloader = Mockito.mock(FileDownloader.class);
    Mockito.when(downloader.download(Matchers.any(File.class)))
      .thenReturn(ImmutableList.of(Pair.create(myDistributionFile, myDescription)));
    DistributionService service = new DistributionService(downloader, CACHE_PATH, myDistributionFileUrl);
    service.getSupportedDistributionForApiLevel(19);
    service.getDistributionForApiLevel(21);
    Mockito.verify(downloader).download(Matchers.any(File.class));
  }

  /**
   * Test that refresh will run asynchronously.
   */
  public void testAsync() throws Exception {
    final FileDownloader downloader = Mockito.mock(FileDownloader.class);
    final Semaphore s = new Semaphore();
    s.down();
    Mockito.when(downloader.download(Matchers.any(File.class))).thenAnswer(new Answer<List<Pair<File, DownloadableFileDescription>>>() {
      @Override
      public List<Pair<File, DownloadableFileDescription>> answer(InvocationOnMock invocation) throws Throwable {
        assertTrue(s.waitFor(5000));
        return ImmutableList.of(Pair.create(myDistributionFile, myDescription));
      }
    });
    final DistributionService service = new DistributionService(downloader, CACHE_PATH, myDistributionFileUrl);
    service.refresh(new Runnable() {
      @Override
      public void run() {
        service.getSupportedDistributionForApiLevel(19);
        service.getDistributionForApiLevel(21);

        try {
          Mockito.verify(downloader).download(Matchers.any(File.class));
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }, null);
    s.up();
  }

  /**
   * Test that if we get another call to refresh while one is in progress, it's callbacks will be queued.
   */
  public void testAsync2() throws Exception {
    final FileDownloader downloader = Mockito.mock(FileDownloader.class);
    final Semaphore s = new Semaphore();
    s.down();
    final Semaphore s2 = new Semaphore();
    s2.down();
    Mockito.when(downloader.download(Matchers.any(File.class))).thenAnswer(new Answer<List<Pair<File, DownloadableFileDescription>>>() {
      @Override
      public List<Pair<File, DownloadableFileDescription>> answer(InvocationOnMock invocation) throws Throwable {
        assertTrue(s.waitFor(5000));
        return ImmutableList.of(Pair.create(myDistributionFile, myDescription));
      }
    });
    DistributionService service = new DistributionService(downloader, CACHE_PATH, myDistributionFileUrl);
    final AtomicBoolean check = new AtomicBoolean(false);
    service.refresh(new Runnable() {
      @Override
      public void run() {
        check.set(true);
      }
    }, null);
    service.refresh(new Runnable() {
      @Override
      public void run() {
        assertTrue(check.get());
        s2.up();
      }
    }, null);

    s.up();
    assertTrue(s2.waitFor(5000));

    service.getSupportedDistributionForApiLevel(19);

    try {
      Mockito.verify(downloader).download(Matchers.any(File.class));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  /**
   * Test that the failure callback will be called if the download fails, and then the fallback data will be used.
   */
  public void testFailure() throws Exception {
    FileDownloader downloader = Mockito.mock(FileDownloader.class);
    Mockito.when(downloader.download(Matchers.any(File.class))).thenThrow(new RuntimeException("expected exception"));

    DistributionService service = new DistributionService(downloader, CACHE_PATH, myDistributionFileUrl);
    final FutureResult<Boolean> result = new FutureResult<Boolean>();
    service.refresh(new Runnable() {
      @Override
      public void run() {
        assert false;
      }
    }, new Runnable() {
      @Override
      public void run() {
        result.set(true);
      }
    });
    assertTrue(result.get(5, TimeUnit.SECONDS));
    assertEquals(0.4, service.getSupportedDistributionForApiLevel(17), 0.001);
  }

  /**
   * Test that we pick up previously-downloaded distributions if a new one can't be loaded.
   */
  public void testFallbackToPrevious() throws Exception {
    File newFile = new File(CACHE_PATH, "distributions_2.json");
    FileUtil.copy(new File(getClass().getResource("testPreviousDistributions.json").toURI()), newFile);

    if (!newFile.setLastModified(20000)) {
      fail();
    }

    File oldFile = new File(CACHE_PATH, "distributions.json");
    FileUtil.copy(new File(getClass().getResource("testPreviousDistributions2.json").toURI()), oldFile);

    if (!oldFile.setLastModified(10000)) {
      fail();
    }

    FileDownloader downloader = Mockito.mock(FileDownloader.class);
    Mockito.when(downloader.download(Matchers.any(File.class))).thenThrow(new RuntimeException("expected exception"));
    DistributionService service = new DistributionService(downloader, CACHE_PATH, myDistributionFileUrl);
    final FutureResult<Boolean> result = new FutureResult<Boolean>();
    service.refresh(new Runnable() {
      @Override
      public void run() {
        result.set(true);
      }
    }, new Runnable() {
      @Override
      public void run() {
        assert false;
      }
    });
    assertTrue(result.get(5, TimeUnit.SECONDS));
    assertEquals(.3, service.getSupportedDistributionForApiLevel(16), 0.0001);

  }
}
