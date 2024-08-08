/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.async.process;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.OutputStream;

/** An base output stream which marshals output into newline-delimited segments for processing. */
public final class LineProcessingOutputStream extends OutputStream {

  /** A line processor */
  public interface LineProcessor {
    /**
     * Process a single, complete line of output.
     *
     * @return Whether line processing should continue
     */
    boolean processLine(String line);
  }

  private final StringBuffer stringBuffer = new StringBuffer();
  private volatile boolean closed;
  private final ImmutableList<LineProcessor> lineProcessors;

  LineProcessingOutputStream(ImmutableList<LineProcessor> lineProcessors) {
    this.lineProcessors = lineProcessors;
  }

  public static LineProcessingOutputStream of(LineProcessor... lineProcessors) {
    return new LineProcessingOutputStream(ImmutableList.copyOf(lineProcessors));
  }

  public static LineProcessingOutputStream of(ImmutableList<LineProcessor> lineProcessors) {
    return new LineProcessingOutputStream(lineProcessors);
  }

  @Override
  public synchronized void write(byte[] b, int off, int len) {
    if (!closed) {
      String text = new String(b, off, len, UTF_8);
      stringBuffer.append(text);

      while (true) {
        int lineBreakIndex = -1;
        int lineBreakLength = 0;
        for (int i = 0; i < stringBuffer.length(); ++i) {
          char c = stringBuffer.charAt(i);
          if (c == '\r' || c == '\n') {
            lineBreakIndex = i;
            lineBreakLength = 1;
            if (c == '\r'
                && (i + 1) < stringBuffer.length()
                && stringBuffer.charAt(i + 1) == '\n') {
              ++lineBreakLength;
            }
            break;
          }
        }

        if (lineBreakIndex == -1) {
          return;
        }

        String line = stringBuffer.substring(0, lineBreakIndex);

        stringBuffer.delete(0, lineBreakIndex + lineBreakLength);

        for (LineProcessor lineProcessor : lineProcessors) {
          if (!lineProcessor.processLine(line)) {
            break;
          }
        }
      }
    }
  }

  @Override
  public void write(int b) throws IOException {
    write(new byte[] {(byte) b}, 0, 1);
  }

  @Override
  public void close() throws IOException {
    closed = true;
    super.close();
  }
}
