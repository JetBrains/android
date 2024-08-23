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
package com.example;

import com.google.common.collect.ImmutableList;
import java.util.List;

/** Code that has a no-op transform applied to it at build time. */
public class TestClass {

  private final ImmutableList<String> list;

  public TestClass() {
    list = ImmutableList.of("hello", "world");
  }

  public List<String> get() {
    return list;
  }
}
