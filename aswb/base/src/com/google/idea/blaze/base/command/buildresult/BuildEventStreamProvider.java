/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.buildresult;

import com.google.common.io.CountingInputStream;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;

/** Provides {@link BuildEventStreamProtos.BuildEvent} */
public interface BuildEventStreamProvider {

  /** An exception parsing a stream of build events. */
  class BuildEventStreamException extends BuildException {
    public BuildEventStreamException(String message, Throwable e) {
      super(message, e);
    }

    public BuildEventStreamException(String message) {
      super(message);
    }
  }

  @Nullable
  static BuildEventStreamProtos.BuildEvent parseNextEventFromStream(InputStream stream)
      throws BuildEventStreamException {
    try {
      return BuildEventStreamProtos.BuildEvent.parseDelimitedFrom(stream);
    } catch (IOException e) {
      throw new BuildEventStreamException(e.getMessage(), e);
    }
  }

  static BuildEventStreamProvider fromInputStream(InputStream stream) {
    CountingInputStream countingStream = new CountingInputStream(stream);
    return new BuildEventStreamProvider() {
      @Nullable
      @Override
      public BuildEvent getNext() throws BuildEventStreamException {
        return parseNextEventFromStream(countingStream);
      }

      @Override
      public long getBytesConsumed() {
        return countingStream.getCount();
      }
    };
  }

  /** Returns the next build event in the stream, or null if there are none remaining. */
  @Nullable
  BuildEventStreamProtos.BuildEvent getNext() throws BuildEventStreamException;

  long getBytesConsumed();
}
