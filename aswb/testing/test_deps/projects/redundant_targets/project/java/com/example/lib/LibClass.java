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

import com.example.top_level_lib_2.Lib2;

/** LibClass test class. */
public final class LibClass {
  private final String hello;

  public LibClass(String hello) {
    this.hello = hello;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LibClass libClass = (LibClass) o;

    return hello.equals(libClass.hello);
  }

  @Override
  public int hashCode() {
    return hello.hashCode() + Lib2.class.hashCode();
  }
}
