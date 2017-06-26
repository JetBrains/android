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
package com.android.tools.idea.sampledata.datasource;

import com.google.common.base.Charsets;
import com.intellij.openapi.util.io.StreamUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.util.function.Function;

public class ResourceContent implements Function<OutputStream, Exception> {
  final InputStream myInputStream;

  private ResourceContent(@NotNull InputStream content) {
    myInputStream = content;
  }

  @NotNull
  public static ResourceContent fromDirectory(@Nullable URL pathUrl) {
    StringBuilder content = new StringBuilder();

    File path = pathUrl != null && pathUrl.getFile() != null ? new File(pathUrl.getFile()) : null;
    File[] files = path != null ? path.listFiles() : null;

    if (files != null) {
      for (File file : files) {
        content.append(file.getAbsolutePath()).append('\n');
      }
    }

    return new ResourceContent(new ByteArrayInputStream(content.toString().getBytes(Charsets.UTF_8)));
  }

  @NotNull
  public static ResourceContent fromInputStream(@NotNull InputStream stream) {
    return new ResourceContent(stream);
  }

  @Override
  public Exception apply(OutputStream stream) {
    try {
      StreamUtil.copyStreamContent(myInputStream, stream);
    }
    catch (IOException e) {
      return e;
    }

    return null;
  }
}
