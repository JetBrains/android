/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.insights.client

import com.intellij.openapi.project.Project

interface TosClient {

  /**
   * Check for ToS acceptance for [firebaseProject]
   *
   * @param firebaseProject: Project ID of the firebase project
   */
  fun isTosAccepted(firebaseProject: String): Boolean

  /** Try to accept the ToS for [firebaseProject] */
  fun tryAcceptTos(firebaseProject: String, project: Project): Boolean
}

/** Stub [TosClient] that returns `true` for any requested setting */
class StubTosClient(private val response: Boolean) : TosClient {
  override fun isTosAccepted(firebaseProject: String) = response

  override fun tryAcceptTos(firebaseProject: String, project: Project): Boolean = response
}
