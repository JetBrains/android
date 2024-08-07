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
package com.google.idea.blaze.base.io;

import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/** Default implementation of InputStreamProvider. */
final class InputStreamProviderImpl implements InputStreamProvider {

  @Override
  @MustBeClosed
  public InputStream forFile(File file) throws FileNotFoundException {
    return new FileInputStream(file);
  }

  @Override
  @MustBeClosed
  public BufferedInputStream forOutputArtifact(BlazeArtifact output) throws IOException {
    return output.getInputStream();
  }
}
