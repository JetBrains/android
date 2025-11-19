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
package com.example.project

import com.example.external.ExternalJavaInSrcJar
import com.example.external.ExternalJavaSource
import com.example.external.ExternalKtInSrcJar
import com.example.external.ExternalKtSource
import com.example.external.gensrcjar.ExternalJavaSourceInGenSrcjar
import com.example.external.gensrcjar.ExternalKtSourceInGenSrcjar

/** Class1 test class  */
class Class2 {
  val string: String
    get() {
      val s: ExternalJavaSource = ExternalJavaSource()
      return (s.copy(ExternalJavaSource.STRING)
              + s.copy(ExternalKtSource.STRING)
              + ExternalJavaInSrcJar.STRING
              + ExternalJavaSourceInGenSrcjar.STRING
              + ExternalKtInSrcJar.STRING
              + ExternalKtSourceInGenSrcjar.STRING)
    }
}