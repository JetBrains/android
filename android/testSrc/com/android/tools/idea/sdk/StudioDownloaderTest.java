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
package com.android.tools.idea.sdk;

import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeSettingsController;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.intellij.idea.Bombed;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Headers;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;

public class StudioDownloaderTest extends LightPlatformTestCase {
  private static final String LOCALHOST = "127.0.0.1";
  private static final String EXPECTED_NO_CACHE_HEADERS = "Pragma: no-cache\nCache-control: no-cache\n";
  private static final String EXPECTED_HEADERS_IF_CACHING_ALLOWED = ""; // none

  private HttpServer myServer;
  private String myUrl;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myServer = HttpServer.create();
    myServer.bind(new InetSocketAddress(LOCALHOST, 0), 1);
    myServer.start();
    myUrl = "http://" + LOCALHOST + ":" + myServer.getAddress().getPort();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myServer.stop(0);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private void createServerContextThatMirrorsRequestHeaders() {
    myServer.createContext("/", ex -> {
      StringBuilder response = new StringBuilder(64);
      Headers headers = ex.getRequestHeaders();
      List<String> pragmaHeader = headers.get("Pragma");
      if (pragmaHeader != null) {
        response.append("Pragma: ");
        response.append(Joiner.on(';').join(pragmaHeader));
        response.append("\n");
      }
      List<String> cacheControlHeader = headers.get("Cache-control");
      if (cacheControlHeader != null) {
        response.append("Cache-control: ");
        response.append(Joiner.on(';').join(cacheControlHeader));
        response.append("\n");
      }
      byte[] responseBody = response.toString().getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(200, responseBody.length);
      ex.getResponseBody().write(responseBody);
      ex.close();
    });
  }

  private void createServerContextThatReturnsCustomContent(String content) {
    myServer.createContext("/", ex -> {
      StringBuilder response = new StringBuilder(content.length());
      Headers headers = ex.getRequestHeaders();
      List<String> rangeHeader = headers.get("Range");
      String contentToReturn = content;
      int httpResponseCode = 200;
      if (rangeHeader != null) {
        Pattern rangeHeaderPattern = Pattern.compile("bytes=(\\d+)-(\\d*)");
        Matcher matcher = rangeHeaderPattern.matcher(rangeHeader.get(0));
        if (matcher.matches()) {
          int fromByte = Integer.parseInt(matcher.group(1));
          int toByte = 0;
          if (!Strings.isNullOrEmpty(matcher.group(2))) {
            toByte = Integer.parseInt(matcher.group(2));
            contentToReturn = content.substring(fromByte, toByte);
          }
          else {
            contentToReturn = content.substring(fromByte);
          }
          httpResponseCode = 206;
          Headers responseHeaders = ex.getResponseHeaders();
          responseHeaders.add("Content-Range", String.format("bytes $1%s-$2%s/$3%s", fromByte,
                                                             (toByte == 0) ? "" : toByte,
                                                             content.length()));
        }
      }

      response.append(contentToReturn);
      byte[] responseBody = response.toString().getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(httpResponseCode, responseBody.length);
      ex.getResponseBody().write(responseBody);
      ex.close();
    });
  }

  public void testHttpNoCacheHeaders() throws Exception {
    createServerContextThatMirrorsRequestHeaders();

    File downloadResult = FileUtil.createTempFile("studio_downloader_test", "txt");
    downloadResult.deleteOnExit();

    FakeSettingsController settingsController = new FakeSettingsController(true);
    StudioDownloader downloader = new StudioDownloader(settingsController);
    String headers;

    downloader.downloadFully(new URL(myUrl), downloadResult, null, new FakeProgressIndicator());
    headers = FileUtil.loadFile(downloadResult);
    assertEquals(EXPECTED_NO_CACHE_HEADERS, headers);

    downloader.downloadFullyWithCaching(new URL(myUrl), downloadResult, null, new FakeProgressIndicator());
    headers = FileUtil.loadFile(downloadResult);
    assertEquals(EXPECTED_HEADERS_IF_CACHING_ALLOWED, headers);
  }

  @Bombed(year = 2021, month = Calendar.DECEMBER, day=1, user = "Andrei.Kuznetsov", description = "Often fails with OOME:HeapSpace in IDEA")
  public void testResumableDownloads() throws Exception {
    // Create some sizeable custom content to download.
    int howMany = (1 << 20);
    String stuff = "A quick brown brown fox jumps over the lazy dog.";
    StringBuilder contentBuffer = new StringBuilder(howMany * stuff.length());
    for (int i = 0; i < howMany; ++i) {
      contentBuffer.append(stuff);
    }
    createServerContextThatReturnsCustomContent(contentBuffer.toString());

    File downloadResult = new File(FileUtil.getTempDirectory(), "studio_partial_downloads_test.txt");
    assertTrue(!downloadResult.exists() || downloadResult.delete());
    downloadResult.deleteOnExit();

    FakeSettingsController settingsController = new FakeSettingsController(true);
    StudioDownloader downloader = new StudioDownloader(settingsController);
    File intermediatesLocation = new File(FileUtil.getTempDirectory(), "intermediates");
    downloader.setDownloadIntermediatesLocation(intermediatesLocation);

    int CANCELLATIONS_COUNT = 10;
    AtomicInteger currentCancellationsCount = new AtomicInteger(0);
    File interimDownload = new File(intermediatesLocation, downloadResult.getName()
                                                           + StudioDownloader.DOWNLOAD_SUFFIX_FN);
    for (int i = 0; i < CANCELLATIONS_COUNT; ++i) {
      try {
        FakeProgressIndicator interruptingProgressIndicator = new FakeProgressIndicator() {
          @Override
          public void setFraction(double fraction) {
            super.setFraction(fraction);
            int p = (int)(fraction * 100);
            if (p % CANCELLATIONS_COUNT == 0 && (p / CANCELLATIONS_COUNT >= currentCancellationsCount.get())) {
              currentCancellationsCount.incrementAndGet();
              cancel();
            }
          }
        };
        downloader.downloadFullyWithCaching(new URL(myUrl), downloadResult, null, interruptingProgressIndicator);
      }
      catch (ProcessCanceledException e) {
        // ignore
      }
/* b/147223426
      assertFalse(downloadResult.exists());
      assertTrue(interimDownload.exists());
b/147223426 */
    }
    // Now complete it without cancellations.
    downloader.downloadFullyWithCaching(new URL(myUrl), downloadResult, null, new FakeProgressIndicator());
    assertTrue(downloadResult.exists());
    assertFalse(interimDownload.exists());

    String downloadedContent = FileUtil.loadFile(downloadResult);
    assertEquals(contentBuffer.toString(), downloadedContent);
  }

  public void testForceHttpUrlPreparation() throws Exception {
    FakeSettingsController settingsController = new FakeSettingsController(true);
    StudioDownloader downloader = new StudioDownloader(settingsController);

    final String TEST_URL_BASE = "studio-downloader-test.name:8080/some/path";
    assertEquals("http://" + TEST_URL_BASE, downloader.prepareUrl(new URL("https://" + TEST_URL_BASE)));
    assertEquals("http://" + TEST_URL_BASE, downloader.prepareUrl(new URL("http://" + TEST_URL_BASE)));

    settingsController.setForceHttp(false);
    assertEquals("https://" + TEST_URL_BASE, downloader.prepareUrl(new URL("https://" + TEST_URL_BASE)));
    assertEquals("http://" + TEST_URL_BASE, downloader.prepareUrl(new URL("http://" + TEST_URL_BASE)));
  }

  public void testDownloadProgressIndicator() throws Exception {
    FakeProgressIndicator parentProgress = new FakeProgressIndicator();

    {
      ProgressIndicator progressIndicator = new StudioDownloader.DownloadProgressIndicator(parentProgress, "foo", 0,
                                                                                           1234);
      assertTrue(progressIndicator.isIndeterminate());
      progressIndicator.setFraction(0.5);
      assertTrue(progressIndicator.isIndeterminate());
    }

    {
      ProgressIndicator progressIndicator = new StudioDownloader.DownloadProgressIndicator(parentProgress, "foo",
                                                                                           -1, 0);
      assertTrue(progressIndicator.isIndeterminate());
      progressIndicator.setFraction(0.5);
      assertTrue(progressIndicator.isIndeterminate());
    }

    {
      ProgressIndicator progressIndicator = new StudioDownloader.DownloadProgressIndicator(parentProgress, "foo",
                                                                                           1234, 0);
      assertFalse(progressIndicator.isIndeterminate());
      progressIndicator.setFraction(0.5);
      assertFalse(progressIndicator.isIndeterminate());
      assertEquals(0.5, progressIndicator.getFraction());
    }

    {
      ProgressIndicator progressIndicator = new StudioDownloader.DownloadProgressIndicator(parentProgress, "foo",
                                                                                           1000, 200);
      assertFalse(progressIndicator.isIndeterminate());
      progressIndicator.setFraction(0.5);
      assertFalse(progressIndicator.isIndeterminate());
      // Progress has to be adjusted taking into account the non-zero startOffset
      assertEquals(0.6, progressIndicator.getFraction()); // 200 + 0.5*(1000-200)
    }
  }
}
