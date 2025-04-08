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
package com.android.tools.idea.insights.ai.codecontext

import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.StacktraceGroup

open class FakeCodeContextResolver(private var codeContext: List<CodeContext>) :
  CodeContextResolver {
  override suspend fun getSource(conn: Connection, stack: StacktraceGroup): CodeContextData {

    if (!conn.isMatchingProject()) return CodeContextData.DISABLED

    return CodeContextData(codeContext)
  }
}
