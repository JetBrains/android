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
package com.example.sample.nested;

import com.google.common.io.Closer;

/** NestedClass test class. */
public final class NestedClass {
  private final int data;
  private final Closer closer = Closer.create();

  public NestedClass(int data) {
    this.data = data;
  }

  public void method() {
    System.out.println(data);
    System.out.println(closer.getClass());
  }
}
