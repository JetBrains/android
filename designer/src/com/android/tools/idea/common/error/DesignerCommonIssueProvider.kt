/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.common.error

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

interface DesignerCommonIssueProvider<T> : Disposable {
  var filter: (Issue) -> Boolean
  fun getFilteredIssues(): List<Issue>
  fun registerUpdateListener(listener: Runnable)
}

class DesignToolsIssueProvider(project: Project) : DesignerCommonIssueProvider<Any?> {

  private val sourceToIssueMap = mutableMapOf<Any, List<Issue>>()

  private val listeners = mutableListOf<Runnable>()
  private val messageBusConnection = project.messageBus.connect()

  private var _filter: (Issue) -> Boolean = { true }
  override var filter: (Issue) -> Boolean
    get() = _filter
    set(value) { _filter = value }

  init {
    Disposer.register(project, this)
    messageBusConnection.subscribe(IssueProviderListener.TOPIC, object : IssueProviderListener {
      override fun issueUpdated(source: Any, issues: List<Issue>) {
        if (issues.isEmpty()) {
          sourceToIssueMap.remove(source)
        }
        else {
          sourceToIssueMap[source] = issues
        }
        listeners.forEach { it.run() }
      }
    })
  }

  override fun getFilteredIssues(): List<Issue> = sourceToIssueMap.values.flatten().filter(filter).toList()

  override fun registerUpdateListener(listener: Runnable) {
    listeners.add(listener)
  }

  override fun dispose() {
    messageBusConnection.disconnect()
    listeners.clear()
  }
}
