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
package com.android.tools.idea.layoutinspector.protobuf;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

// Copy from com.android.tools.idea.protobuf.AbstractMessageLite.LimitedInputStream

final class LimitedInputStream extends FilterInputStream {
  private int limit;

  LimitedInputStream(InputStream in, int limit) {
    super(in);
    this.limit = limit;
  }

  public int available() throws IOException {
    return Math.min(super.available(), this.limit);
  }

  public int read() throws IOException {
    if (this.limit <= 0) {
      return -1;
    } else {
      int result = super.read();
      if (result >= 0) {
        --this.limit;
      }

      return result;
    }
  }

  public int read(byte[] b, int off, int len) throws IOException {
    if (this.limit <= 0) {
      return -1;
    } else {
      len = Math.min(len, this.limit);
      int result = super.read(b, off, len);
      if (result >= 0) {
        this.limit -= result;
      }

      return result;
    }
  }

  public long skip(long n) throws IOException {
    long result = super.skip(Math.min(n, this.limit));
    if (result >= 0L) {
      this.limit = (int)((long)this.limit - result);
    }

    return result;
  }
}
