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

import static com.google.idea.blaze.base.bazel.BazelExitCode.BUILD_FAILED;
import static com.google.idea.blaze.base.bazel.BazelExitCode.PARTIAL_SUCCESS;
import static com.google.idea.blaze.base.bazel.BazelExitCode.SUCCESS;
import static com.google.idea.blaze.base.bazel.BazelExitCodeException.ThrowOption.ALLOW_BUILD_FAILURE;
import static com.google.idea.blaze.base.bazel.BazelExitCodeException.ThrowOption.ALLOW_PARTIAL_SUCCESS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.aspects.BuildResult.Status;
import com.google.idea.blaze.exception.BuildException;

/**
 * Exception to be used when a build invocation returns a non-zero exit code.
 *
 * <p>Since a non-zero exit code is considered normal operation for the IDE, this exception should
 * not be reported as an IDE error, but rather handled gracefully and reported to the user.
 */
public class BazelExitCodeException extends BuildException {

  /** Options that may be passed to {@link #throwIfFailed} to modify its behavior. */
  public enum ThrowOption {
    ALLOW_PARTIAL_SUCCESS,
    ALLOW_BUILD_FAILURE,
  }

  private final int exitCode;

  public static void throwIfFailed(
      BlazeCommand.Builder command, BuildResult result, ThrowOption... options)
      throws BazelExitCodeException {
    if (result.status == Status.SUCCESS) {
      return;
    }
    throwIfFailed(command, result.exitCode, options);
  }

  public static void throwIfFailed(
      BlazeCommand.Builder command, int exitCode, ThrowOption... options)
      throws BazelExitCodeException {
    throwIfFailed("Command: " + command.build(), exitCode, options);
  }

  public static void throwIfFailed(String message, int exitCode, ThrowOption... options)
      throws BazelExitCodeException {
    if (allowExitCode(exitCode, options)) {
      return;
    }
    throw new BazelExitCodeException(
        String.format("Build command failed with %d.\n%s", exitCode, message), exitCode);
  }

  private static boolean allowExitCode(int exitCode, ThrowOption... options) {
    if (exitCode == SUCCESS) {
      return true;
    }
    ImmutableSet<ThrowOption> optionSet = ImmutableSet.copyOf(options);
    if (exitCode == PARTIAL_SUCCESS && optionSet.contains(ALLOW_PARTIAL_SUCCESS)) {
      return true;
    }
    if (exitCode == BUILD_FAILED && optionSet.contains(ALLOW_BUILD_FAILURE)) {
      return true;
    }
    return false;
  }

  @VisibleForTesting
  public BazelExitCodeException(String message, int exitCode) {
    super(message);
    this.exitCode = exitCode;
  }

  public int getExitCode() {
    return exitCode;
  }

  @Override
  public boolean isIdeError() {
    return false;
  }
}
