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
import com.example.external.gensrcjar.ExternalJavaSourceInGenSrcjar
import com.example.external.withjvmname.ExternalKtSource1
import com.example.external.withjvmname.ExternalKtSource2
import com.example.external.withjvmname.gensrcjar.ExternalKtSourceInGenSrcjar1
import com.example.external.withjvmname.gensrcjar.ExternalKtSourceInGenSrcjar2
import com.example.external.withjvmname.srcjar.ExternalKtInSrcJar1
import com.example.external.withjvmname.srcjar.ExternalKtInSrcJar2
import com.example.external.withoutjvmname.ExternalKtSource
import com.example.external.withoutjvmname.srcjar.ExternalKtInSrcJar
import com.example.external.withoutjvmname.gensrcjar.ExternalKtSourceInGenSrcjar

/** Class1 test class  */
class Class2 {
  val string: String
    get() {
      val s: ExternalJavaSource = ExternalJavaSource()
      return (s.copy(ExternalJavaSource.STRING)
              + s.copy(ExternalKtSource.STRING)
              + ExternalKtSource1.STRING
              + ExternalKtSource2.STRING
              + com.example.external.withjvmname.STRING1
              + com.example.external.withjvmname.STRING2
              + com.example.external.withoutjvmname.STRING
              + ExternalJavaInSrcJar.STRING
              + ExternalJavaSourceInGenSrcjar.STRING
              + ExternalKtInSrcJar.STRING
              + ExternalKtInSrcJar1.STRING
              + ExternalKtInSrcJar2.STRING
              + com.example.external.withjvmname.srcjar.STRING1
              + com.example.external.withjvmname.srcjar.STRING2
              + com.example.external.withoutjvmname.srcjar.STRING
              + ExternalKtSourceInGenSrcjar.STRING
              + ExternalKtSourceInGenSrcjar1.STRING
              + ExternalKtSourceInGenSrcjar2.STRING
              + com.example.external.withjvmname.gensrcjar.STRING1
              + com.example.external.withjvmname.gensrcjar.STRING2
              + com.example.external.withoutjvmname.gensrcjar.STRING)
    }
}