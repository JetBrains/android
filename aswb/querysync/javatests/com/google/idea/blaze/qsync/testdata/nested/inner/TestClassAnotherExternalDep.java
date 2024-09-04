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
package com.google.idea.blaze.qsync.testdata.nested.inner;

import com.google.gson.Gson;

public class TestClassAnotherExternalDep {

  private TestClassAnotherExternalDep() {}

  @SuppressWarnings("JdkImmutableCollections")
  public static String getString() {
    Foo foo = new Foo();
    Gson gson = new Gson();
    return gson.toJson(foo);
  }

  private static class Foo {
    private Foo() {}
  }
}
