/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base;

import static java.util.Objects.requireNonNull;

import com.google.devtools.build.runfiles.Runfiles;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.rules.ExternalResource;

public class TestData extends ExternalResource {

  private Runfiles runfiles;
  public Path root;

  @Override
  protected void before() throws IOException {
    runfiles = Runfiles.preload().unmapped();
    String workspace = requireNonNull(System.getenv("TEST_WORKSPACE"));
    root =
        Path.of(
            runfiles.rlocation(
                workspace
                    + "/tools/adt/idea/aswb/base/tests/utils/testdata/java"));
  }

  public String get(String path) {
    return root + "/" + path;
  }

  public Path getPath(String path) {
    return root.resolve(Path.of(path));
  }
}
