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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.tools.idea.common.error.Issue
import java.util.concurrent.ConcurrentHashMap

/** List of visual lint issues. */
class VisualLintIssues {

  /**
   * Range based hashed map - used for detecting different config same issues.
   * Source of truth.
   */
  private val _map = ConcurrentHashMap<Int, Issue>()

  /** For accessing by type. To be used later for categorization. */
  private val _mapByType = ConcurrentHashMap<VisualLintErrorType, Issue>()

  val list: Collection<Issue>
    get() = _map.values

  fun clear() {
    _map.clear()
    _mapByType.clear()
  }

  fun add(errorType: VisualLintErrorType, issue: Issue) {
    if (issue is VisualLintRenderIssue) {
      val original = _map[issue.rangeBasedHashCode()]

      when {
        original == null -> {
          // new issue. Add to map
          _map[issue.rangeBasedHashCode()] = issue
          _mapByType[errorType] = issue
        }
        original != issue -> {
          // original.rangeBasedHashCode() == issue.rangeBasedHashCode()
          // original and issue are same issue, diff config.
          // TODO: Check if components already exist (set instead of list)
          (original as VisualLintRenderIssue).components.addAll(issue.components)
          original.models.addAll(issue.models)
          issue.components.forEach { original.source.addComponent(it) }
          issue.models.forEach { original.source.addModel(it) }
        }
        else -> {
          // Same issue, same config. ignore.
        }
      }
    } else {
      _mapByType[errorType] = issue
      _map[issue.hashCode()] = issue
    }
  }
}