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
package com.android.tools.idea.downloads;

import com.android.tools.idea.TestDataPathUtils;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.download.impl.DownloadableFileDescriptionImpl;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.truth.Truth.assertThat;

public class DownloadServiceTest extends LightPlatformTestCase {
  private static final String DATA_PATH = "downloads";
  private static final String DATA_FILENAME = "test_data.json";
  private static final String DATA_FILE = new File(DATA_PATH, DATA_FILENAME).getPath();
  private static final File CACHE_PATH = new File(PathManager.getTempPath(), "downloadServiceTest");

  private URL myFallbackFileUrl;
  private File myDownloadFile;
  private DownloadableFileDescription myDescription;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File fallbackFile = new File(TestDataPathUtils.getTestDataPath().toFile(), "fallback.json");
    myFallbackFileUrl = fallbackFile.toURI().toURL();
    myDownloadFile = new File(TestDataPathUtils.getTestDataPath().toFile(), DATA_FILENAME);
    URL downloadFileUrl = fallbackFile.toURI().toURL();
    myDescription = new DownloadableFileDescriptionImpl(downloadFileUrl.toString(), DATA_FILE, "json");

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
   * Test that refresh will run asynchronously.
   */
  public void testAsync() throws Exception {
    FileDownloader downloader = Mockito.mock(FileDownloader.class);
    Semaphore s = new Semaphore();
    s.down();
    Mockito.when(downloader.download(ArgumentMatchers.any(File.class))).thenAnswer(invocation -> {
      assertThat(s.waitFor(5000)).isTrue();
      return ImmutableList.of(Pair.create(myDownloadFile, myDescription));
    });
    MyDownloadService service = new MyDownloadService(downloader, myFallbackFileUrl);
    service.refresh(() -> {
      assertThat(service.getLoadedCount()).isEqualTo(1);
      assertThat(service.getLastLoadUrl()).isNotEqualTo(myFallbackFileUrl);

      try {
        Mockito.verify(downloader).download(ArgumentMatchers.any(File.class));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }, () -> {
      throw new RuntimeException("Failure callback");
    });
    s.up();
  }

  /**
   * Test that if we get another call to refresh while one is in progress, it's callbacks will be queued.
   */
  public void testAsync2() throws Exception {
    FileDownloader downloader = Mockito.mock(FileDownloader.class);
    Semaphore s = new Semaphore();
    s.down();
    Semaphore s2 = new Semaphore();
    s2.down();
    Mockito.when(downloader.download(ArgumentMatchers.any(File.class))).thenAnswer(invocation -> {
      assertThat(s.waitFor(5000)).isTrue();
      return ImmutableList.of(Pair.create(myDownloadFile, myDescription));
    });
    MyDownloadService service = new MyDownloadService(downloader, myFallbackFileUrl);
    AtomicBoolean check = new AtomicBoolean(false);
    service.refresh(() -> check.set(true), null);
    service.refresh(() -> {
      assertThat(check.get()).isTrue();
      s2.up();
    }, null);

    s.up();
    assertThat(s2.waitFor(5000)).isTrue();

    assertThat(service.getLoadedCount()).isEqualTo(1);
    assertThat(service.getLastLoadUrl()).isNotEqualTo(myFallbackFileUrl);

    try {
      Mockito.verify(downloader).download(ArgumentMatchers.any(File.class));
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
    Mockito.when(downloader.download(ArgumentMatchers.any(File.class))).thenThrow(new RuntimeException("expected exception"));

    MyDownloadService service = new MyDownloadService(downloader, myFallbackFileUrl);
    final CompletableFuture<Boolean> result = new CompletableFuture<>();
    service.refresh(() -> {
      assert false;
    }, () -> result.complete(true));
    assertThat(result.get(5, TimeUnit.SECONDS)).isTrue();
    assertThat(service.getLoadedCount()).isEqualTo(1);
    assertThat(service.getLastLoadUrl()).isEqualTo(myFallbackFileUrl);
  }

  /**
   * Test that we pick up previously-downloaded distributions if a new one can't be loaded.
   */
  public void testFallbackToPrevious() throws Exception {
    File newFile = new File(CACHE_PATH, "test_data2.json");
    FileUtil.copy(new File(new File(TestDataPathUtils.getTestDataPath().toFile(), DATA_PATH), "testPreviousData.json"), newFile);

    if (!newFile.setLastModified(20000)) {
      fail();
    }

    File oldFile = new File(CACHE_PATH, "test_data.json");
    FileUtil.copy(new File(new File(TestDataPathUtils.getTestDataPath().toFile(), DATA_PATH), "testPreviousData.json"), oldFile);

    if (!oldFile.setLastModified(10000)) {
      fail();
    }

    FileDownloader downloader = Mockito.mock(FileDownloader.class);
    Mockito.when(downloader.download(ArgumentMatchers.any(File.class))).thenThrow(new RuntimeException("expected exception"));
    MyDownloadService service = new MyDownloadService(downloader, myFallbackFileUrl);
    CompletableFuture<Boolean> result = new CompletableFuture<>();
    service.refresh(() -> result.complete(true), () -> {
      assert false;
    });
    assertThat(result.get(5, TimeUnit.SECONDS)).isTrue();
    assertThat(service.getLoadedCount()).isEqualTo(1);
    assertThat(service.getLastLoadUrl()).isEqualTo(oldFile.toURI().toURL());
  }

  private static class MyDownloadService extends DownloadService {
    private int myLoadedCount;
    private URL myLastLoadUrl;

    private MyDownloadService(@NotNull FileDownloader downloader, @NotNull URL fallbackUrl) {
      super(downloader, "My Service", fallbackUrl, CACHE_PATH, DATA_FILENAME);
    }

    @Override
    public void loadFromFile(@NotNull URL url) {
      myLoadedCount++;
      myLastLoadUrl = url;
    }

    public int getLoadedCount() {
      return myLoadedCount;
    }

    public URL getLastLoadUrl() {
      return myLastLoadUrl;
    }
  }
}
