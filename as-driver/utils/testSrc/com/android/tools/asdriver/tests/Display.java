/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.tests;

import com.intellij.openapi.util.SystemInfo;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public interface Display extends AutoCloseable {
  String getDisplay();

  static Display createDefault() throws IOException {
    if (SystemInfo.isMac) {
      return new MacDisplay();
    } else if (SystemInfo.isLinux) {
      return new XvfbServer();
    } else {
      return new NativeDisplay();
    }
  }

  static Display createCustom(@NotNull String resolution) throws IOException {
    if (SystemInfo.isMac) {
      return new MacDisplay();
    } else if (SystemInfo.isLinux) {
      return new XvfbServer(resolution);
    } else {
      return new NativeDisplay();
    }
  }
}
