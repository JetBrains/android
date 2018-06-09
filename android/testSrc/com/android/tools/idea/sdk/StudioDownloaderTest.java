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

import com.android.repository.testframework.FakeSettingsController;
import junit.framework.TestCase;

import java.net.URL;

public class StudioDownloaderTest extends TestCase {
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
