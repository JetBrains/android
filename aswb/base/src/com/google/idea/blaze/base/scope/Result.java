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
package com.google.idea.blaze.base.scope;

/** Helper class to be used when you want to return a result or error in a scoped function. */
public class Result<T> {
  public final T result;
  public final Throwable error;

  public Result(T result) {
    this.result = result;
    this.error = null;
  }

  public Result(Throwable error) {
    this.result = null;
    this.error = error;
  }

  public static <T> Result<T> of(T result) {
    return new Result<T>(result);
  }

  public static <T> Result<T> error(Throwable t) {
    return new Result<T>(t);
  }
}
