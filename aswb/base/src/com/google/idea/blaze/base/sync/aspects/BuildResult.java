/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.aspects;

import java.util.Objects;

/** The result of a blaze operation */
public class BuildResult {

  private static final int SUCCESS_EXIT_CODE = 0;
  private static final int BUILD_ERROR_EXIT_CODE = 1;
  // blaze server out-of-memory exit code
  private static final int OOM_EXIT_CODE = 33;

  /** The status of a blaze operation */
  public enum Status {
    SUCCESS, // Success
    BUILD_ERROR, // Return code 1, a build error
    FATAL_ERROR; // Some other failure

    private static Status fromExitCode(int exitCode) {
      if (exitCode == SUCCESS_EXIT_CODE) {
        return SUCCESS;
      } else if (exitCode == BUILD_ERROR_EXIT_CODE) {
        return BUILD_ERROR;
      }
      return FATAL_ERROR;
    }

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  public static final BuildResult SUCCESS = fromExitCode(SUCCESS_EXIT_CODE);
  /** A general fatal error build result */
  public static final BuildResult FATAL_ERROR = fromExitCode(-1);

  public static BuildResult fromExitCode(int exitCode) {
    return new BuildResult(exitCode);
  }

  /** Returns the 'worst' build result of the two. */
  public static BuildResult combine(BuildResult first, BuildResult second) {
    return fromExitCode(combineExitCode(first.exitCode, second.exitCode));
  }

  public final int exitCode;
  public final Status status;

  private BuildResult(int exitCode) {
    this.exitCode = exitCode;
    status = Status.fromExitCode(exitCode);
  }

  public boolean outOfMemory() {
    return exitCode == OOM_EXIT_CODE;
  }

  private static int combineExitCode(int first, int second) {
    if (first == OOM_EXIT_CODE || second == OOM_EXIT_CODE) {
      // OOM errors treated specially, so preserve them.
      return OOM_EXIT_CODE;
    }
    Status firstStatus = Status.fromExitCode(first);
    Status secondStatus = Status.fromExitCode(second);
    return firstStatus.ordinal() >= secondStatus.ordinal() ? first : second;
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof BuildResult)) {
      return false;
    }
    BuildResult buildResult = (BuildResult) object;
    return buildResult.status == status && buildResult.exitCode == exitCode;
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, exitCode);
  }
}
