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

import com.example.jar1.Jar1Class;
import com.example.jar2.Jar2Class;
import com.google.common.collect.ImmutableList;

/** Test class that refers to classes defined in targets with generate srcjars. */
public class Example {

  private Example() {}

  public static final ImmutableList<String> LIST =
      ImmutableList.of(Jar1Class.STRING, Jar2Class.STRING);
}
