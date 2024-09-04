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
package com.google.idea.blaze.base.bazel;

/**
 * Contains constants for selected bazel exit codes.
 *
 * <p>See https://docs.bazel.build/versions/0.21.0/guide.html#what-exit-code-will-i-get
 */
public class BazelExitCode {
  private BazelExitCode() {}

  public static final int SUCCESS = 0;
  public static final int BUILD_FAILED = 1;
  public static final int PARTIAL_SUCCESS = 3;
}
