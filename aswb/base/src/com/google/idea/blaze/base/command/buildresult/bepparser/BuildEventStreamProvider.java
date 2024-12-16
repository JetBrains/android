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
package com.google.idea.blaze.base.command.buildresult.bepparser;

import com.google.common.io.CountingInputStream;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import javax.annotation.Nullable;

/** Provides {@link BuildEventStreamProtos.BuildEvent} */
public interface BuildEventStreamProvider extends AutoCloseable {

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

  /**
   * Creates a {@link BuildEventStreamProvider} from the given {@code stream}.
   *
   * <p>Note: takes ownership of the {@code stream} and closes it when is being closed.
   */
  static BuildEventStreamProvider fromInputStream(InputStream stream) {
    CountingInputStream countingStream = new CountingInputStream(stream);
    return new BuildEventStreamProvider() {
      @Override
      public void close() {
        try {
          stream.close();
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public Object getId() {
        return Optional.empty();
      }

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

  /**
   * @return an object that represents the identity of the build to the user.
   */
  Object getId();

  /** Returns the next build event in the stream, or null if there are none remaining. */
  @Nullable
  BuildEventStreamProtos.BuildEvent getNext() throws BuildEventStreamException;

  long getBytesConsumed();

  @Override
  void close();
}
