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
package com.android.tools.idea.whatsnew.assistant;

import com.intellij.util.net.HttpConfigurable;
import java.net.URL;
import java.net.URLConnection;
import org.jetbrains.annotations.NotNull;

public class WhatsNewConnectionOpener {
  /**
   * Opens a connection to a URL
   */
  @NotNull
  public URLConnection openConnection(@NotNull URL url, int timeoutMillis) throws Exception {
    URLConnection connection = HttpConfigurable.getInstance().openConnection(url.toExternalForm());
    // If timeout is not > 0, the default values are used: 60s read and 10s connect
    if (timeoutMillis > 0) {
      connection.setReadTimeout(timeoutMillis);
      connection.setConnectTimeout(timeoutMillis);
    }
    return connection;
  }
}
