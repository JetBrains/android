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
package com.google.idea.blaze.common.artifact;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.BufferedInputStream;
import java.io.IOException;

/** A build artifact, either a source or output (generated) artifact. */
public interface BlazeArtifact {

  /** Returns the length of the underlying file in bytes, or 0 if this can't be determined. */
  long getLength();

  /** A buffered input stream providing the contents of this artifact. */
  @MustBeClosed
  BufferedInputStream getInputStream() throws IOException;

}
