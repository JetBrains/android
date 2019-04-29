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
import com.intellij.openapi.util.io.FileUtil;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Headers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;

@RunWith(JUnit4.class)
public class StudioDownloaderTest {
  private static final String LOCALHOST = "127.0.0.1";
  private static final String EXPECTED_NO_CACHE_HEADERS = "Pragma: no-cache\nCache-control: no-cache\n";
  private static final String EXPECTED_HEADERS_IF_CACHING_ALLOWED = ""; // none

  private HttpServer myServer;
  private String myUrl;

  @Before
  public void setUp() throws IOException {
    myServer = HttpServer.create();
    myServer.bind(new InetSocketAddress(LOCALHOST, 0), 1);
    myServer.start();
    myUrl = "http://" + LOCALHOST + ":" + myServer.getAddress().getPort();
  }

  @After
  public void tearDown() {
    myServer.stop(0);
  }

  private void createServerContextWhichReturnsCachingHeaders() {
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
      ex.sendResponseHeaders(200, 0);
      ex.getResponseBody().write(response.toString().getBytes());
      ex.close();
    });
  }

  @Test
  public void testHttpNoCacheHeaders() throws Exception {
    createServerContextWhichReturnsCachingHeaders();

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
}
