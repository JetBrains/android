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
@file:JvmMultifileClass
@file:JvmName("ExternalKtUtils")
package com.example.external.withjvmname

/** ExternalKtSource test class  */
const val STRING2 = "TopLevelExternalKtSource2"

class ExternalKtSource2 {
  fun copy(s: String?): String? {
    return s
  }

  companion object {
    const val STRING: String = "ExternalKtSource"
  }
}