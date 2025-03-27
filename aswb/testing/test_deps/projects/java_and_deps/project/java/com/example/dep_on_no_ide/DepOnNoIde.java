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
package com.example.dep_on_no_ide;

import com.example.no_ide.NoIdeLib;
import com.example.top_level_lib_1.Lib1;

/** DepOnNoIde test class */
public class DepOnNoIde {
  public final NoIdeLib noIdeLib = new NoIdeLib("test");
  public final Lib1 lib1 = Lib1.createTest();

  public void method() {
    System.out.println(noIdeLib.getClass());
    System.out.println(lib1.getClass());
  }
}
