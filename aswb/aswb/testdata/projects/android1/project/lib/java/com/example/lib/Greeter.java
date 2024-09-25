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
package com.example.lib;

import com.example.bazel.R;

/** A tiny Greeter library for the Bazel Android "Hello, World" app. */
public class Greeter {
  public String sayHello() {
    int unused = R.string.click_me_button;
    return "Hello Bazel! \uD83D\uDC4B\uD83C\uDF31"; // Unicode for 👋🌱
  }
}
