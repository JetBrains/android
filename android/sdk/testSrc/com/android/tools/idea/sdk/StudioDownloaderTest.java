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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.repository.io.FileOpUtils;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeSettingsController;
import com.android.testutils.file.DelegatingFileSystemProvider;
import com.android.testutils.file.InMemoryFileSystems;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.testFramework.ApplicationRule;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class StudioDownloaderTest {
  private static final String LOCALHOST = "127.0.0.1";
  private static final String EXPECTED_NO_CACHE_HEADERS = "Pragma: no-cache\nCache-control: no-cache\n";
  private static final String EXPECTED_HEADERS_IF_CACHING_ALLOWED = ""; // none

  @Rule
  public ApplicationRule rule = new ApplicationRule();

  private HttpServer myServer;
  private String myUrl;

  @Before
  public void setUp() throws Exception {
    myServer = HttpServer.create();
    myServer.bind(new InetSocketAddress(LOCALHOST, 0), 1);
    myServer.start();
    myUrl = "http://" + LOCALHOST + ":" + myServer.getAddress().getPort() + "/myfile";
  }

  @After
  public void tearDown() throws Exception {
    myServer.stop(0);
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
    myServer.createContext("/myfile", ex -> {
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

  @Test
  public void testHttpNoCacheHeaders() throws Exception {
    createServerContextThatMirrorsRequestHeaders();
    FileSystem fs = InMemoryFileSystems.createInMemoryFileSystem();

    Path downloadResult = FileOpUtils.getNewTempDir("studio_downloader_test", fs).resolve("download.txt");

    FakeSettingsController settingsController = new FakeSettingsController(true);
    StudioDownloader downloader = new StudioDownloader(settingsController);
    String headers;

    downloader.downloadFully(new URL(myUrl), downloadResult, null, new FakeProgressIndicator());
    headers = new String(Files.readAllBytes(downloadResult));
    assertEquals(EXPECTED_NO_CACHE_HEADERS, headers);

    downloader.downloadFullyWithCaching(new URL(myUrl), downloadResult, null, new FakeProgressIndicator());
    headers = new String(Files.readAllBytes(downloadResult));
    assertEquals(EXPECTED_HEADERS_IF_CACHING_ALLOWED, headers);
  }

  @Test
  public void testResumableDownloads() throws Exception {
    FileSystem fs = InMemoryFileSystems.createInMemoryFileSystem();
    // Create some sizeable custom content to download.
    int howMany = (1 << 20);
    String stuff = "A quick brown brown fox jumps over the lazy dog.";
    String content = stuff.repeat(howMany);
    createServerContextThatReturnsCustomContent(content);

    Path downloadResult = FileOpUtils.getNewTempDir("testResumableDownloads", fs).resolve("studio_partial_downloads_test.txt");

    FakeSettingsController settingsController = new FakeSettingsController(true);
    StudioDownloader downloader = new StudioDownloader(settingsController);
    Path intermediatesLocation = FileOpUtils.getNewTempDir("intermediates", fs);
    downloader.setDownloadIntermediatesLocation(intermediatesLocation);

    int CANCELLATIONS_COUNT = 10;
    AtomicInteger currentCancellationsCount = new AtomicInteger(0);
    Path interimDownload = intermediatesLocation.resolve(downloadResult.getFileName().toString()
                                                           + StudioDownloader.DOWNLOAD_SUFFIX_FN);
    for (int i = 0; i < CANCELLATIONS_COUNT; ++i) {
      try {
        FakeProgressIndicator interruptingProgressIndicator = new FakeProgressIndicator() {
          @Override
          public void setFraction(double fraction) {
            super.setFraction(fraction);
            if (fraction * CANCELLATIONS_COUNT >= currentCancellationsCount.get()) {
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
      assertFalse(Files.exists(downloadResult));
      assertTrue(Files.exists(interimDownload));
    }
    // Now complete it without cancellations.
    downloader.downloadFullyWithCaching(new URL(myUrl), downloadResult, null, new FakeProgressIndicator());
    assertTrue(Files.exists(downloadResult));
    assertFalse(Files.exists(interimDownload));

    String downloadedContent = new String(Files.readAllBytes(downloadResult));
    assertEquals(content, downloadedContent);
  }

  @Test
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

  @Test
  public void testDownloadProgressIndicator() {
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
      assertEquals(0.5, progressIndicator.getFraction(), 0.0001);
    }

    {
      ProgressIndicator progressIndicator = new StudioDownloader.DownloadProgressIndicator(parentProgress, "foo",
                                                                                           1000, 200);
      assertFalse(progressIndicator.isIndeterminate());
      progressIndicator.setFraction(0.5);
      assertFalse(progressIndicator.isIndeterminate());
      // Progress has to be adjusted taking into account the non-zero startOffset
      assertEquals(0.6, progressIndicator.getFraction(), 0.0001); // 200 + 0.5*(1000-200)
    }
  }

  @Test
  public void testTemporaryFiles() throws Exception {
    // jimfs doesn't support DELETE_ON_CLOSE or REPLACE_EXISTING
    FileSystem fs = new DelegatingFileSystemProvider(InMemoryFileSystems.createInMemoryFileSystem()) {
      @Override
      public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        List<OpenOption> optionsList = new ArrayList<>(Arrays.asList(options));
        boolean shouldDelete = optionsList.remove(StandardOpenOption.DELETE_ON_CLOSE);
        InputStream in = super.newInputStream(path, optionsList.toArray(new OpenOption[0]));
        return new InputStream() {
          @Override
          public int read() throws IOException {
            return in.read();
          }

          @Override
          public void close() throws IOException {
            super.close();
            if (shouldDelete) {
              Files.delete(path);
            }
          }
        };
      }

      @Override
      public void move(@NotNull Path source,
                       @NotNull Path target,
                       @NotNull CopyOption... options) throws IOException {
        if (Arrays.asList(options).contains(StandardCopyOption.REPLACE_EXISTING)) {
          Files.delete(target);
        }
        super.move(source, target, options);
      }
    }.getFileSystem();
    Path tmpPath = Files.createDirectory(InMemoryFileSystems.getSomeRoot(fs).resolve("tmp"));
    createServerContextThatReturnsCustomContent("blah");
    StudioDownloader downloader = new StudioDownloader(new FakeSettingsController(false));
    downloader.setDownloadIntermediatesLocation(tmpPath);
    byte[] bytes = new byte[10];
    // call downloadAndStream a bunch of times and verify it works
    for (int i = 0; i < 105; i++) {
      try (BufferedInputStream is = new BufferedInputStream(downloader.downloadAndStream(new URL(myUrl), new FakeProgressIndicator()))) {
        assertEquals(4, is.read(bytes));
        assertEquals("blah", new String(bytes).trim());
      }
    }

    // Verify that we haven't left any temporary files or directories around.
    assertThat(InMemoryFileSystems.getExistingFiles(fs)).isEmpty();
    assertThat(InMemoryFileSystems.getExistingFolders(fs)).containsExactly(tmpPath.toString(),
                                                                           InMemoryFileSystems.getDefaultWorkingDirectory());
  }

  @Test
  public void testTemporaryFilesWithSymlink() throws Exception {
    FileSystem fs = InMemoryFileSystems.createInMemoryFileSystem();
    Path realTmpPath = Files.createDirectory(InMemoryFileSystems.getSomeRoot(fs).resolve("realTmp"));
    Path tmpPath = Files.createSymbolicLink(InMemoryFileSystems.getSomeRoot(fs).resolve("tmp"), realTmpPath);
    createServerContextThatReturnsCustomContent("blah");
    StudioDownloader downloader = new StudioDownloader(new FakeSettingsController(false));
    downloader.setDownloadIntermediatesLocation(tmpPath);
    byte[] bytes = new byte[10];
    try (BufferedInputStream is = new BufferedInputStream(
      downloader.downloadAndStreamWithOptions(new URL(myUrl), new FakeProgressIndicator()))) {
      assertEquals(4, is.read(bytes));
      assertEquals("blah", new String(bytes).trim());
    }
  }

  @Test
  public void testTemporaryFilesWithNonexistentPath() throws Exception {
    FileSystem fs = InMemoryFileSystems.createInMemoryFileSystem();
    Path tmpPath = InMemoryFileSystems.getSomeRoot(fs).resolve("tmp");
    createServerContextThatReturnsCustomContent("blah");
    StudioDownloader downloader = new StudioDownloader(new FakeSettingsController(false));
    downloader.setDownloadIntermediatesLocation(tmpPath);
    byte[] bytes = new byte[10];
    try (BufferedInputStream is = new BufferedInputStream(
      downloader.downloadAndStreamWithOptions(new URL(myUrl), new FakeProgressIndicator()))) {
      assertEquals(4, is.read(bytes));
      assertEquals("blah", new String(bytes).trim());
    }
  }
}
