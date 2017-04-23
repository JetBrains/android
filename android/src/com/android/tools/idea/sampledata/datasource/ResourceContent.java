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

import com.intellij.openapi.util.io.StreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;

public class ResourceContent implements Function<OutputStream, Exception> {
  final InputStream myInputStream;

  public ResourceContent(InputStream content) {
    myInputStream = content;
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
