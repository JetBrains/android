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
package com.google.idea.blaze.exception;

/**
 * Base class for all exceptions that can occur when interacting with the build system.
 *
 * <p>This class may be thrown directly, or may be extended if doing so adds value.
 */
public class BuildException extends Exception {
  public BuildException(Throwable cause) {
    super(cause);
  }

  public BuildException(String message) {
    super(message);
  }

  public BuildException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Indicates if this exception is caused by an IDE error/bug, or if it is expected as part of
   * normal IDE operation (e.g. a build error caused by an error in the users build file).
   *
   * <p>This method may be overridden by subclasses to return false. Any implementations that return
   * false from here should ensure that the exception has a user facing message, to inform the user
   * what went wrong and how they can fix it.
   */
  public boolean isIdeError() {
    return true;
  }
}
