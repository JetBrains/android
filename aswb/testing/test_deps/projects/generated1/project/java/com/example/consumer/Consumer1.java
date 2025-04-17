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
package com.example.consumer;

import com.example.srcjar.Sample1;
import com.example.srcjar.Sample2;

/** A sample consumer class consuming generated `.jar` and `.srcjar` files. */
final class Consumer1 {

  public final Sample1 sample1 = new Sample1();
  public final Sample2 sample2 = new Sample2();

  private Consumer1() {}
}
