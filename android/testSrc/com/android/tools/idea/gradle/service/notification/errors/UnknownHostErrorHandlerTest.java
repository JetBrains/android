/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification.errors;

import junit.framework.TestCase;

import java.net.HttpURLConnection;
import java.net.URL;

import static com.android.tools.idea.gradle.service.notification.errors.UnknownHostErrorHandler.GRADLE_PROXY_ACCESS_DOCS_URL;

/**
 * Tests for {@link UnknownHostErrorHandler}.
 */
public class UnknownHostErrorHandlerTest extends TestCase {
  public void testGradleProxyDocsUrlIsValid() throws Exception {
    URL docsUrl = new URL(GRADLE_PROXY_ACCESS_DOCS_URL);
    HttpURLConnection connection = (HttpURLConnection)docsUrl.openConnection();

    // to avoid 403 Forbidden HTTP status add user-agent request property
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.157 Safari/537.36");
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
    int responseCode = connection.getResponseCode();
    if (responseCode == 400) {
      System.out.println(String.format("Failed to conect to '%1$s'. Please check the URL manually.", GRADLE_PROXY_ACCESS_DOCS_URL));
      return;
    }
    assertEquals(200 /* OK */, responseCode);
  }
}
