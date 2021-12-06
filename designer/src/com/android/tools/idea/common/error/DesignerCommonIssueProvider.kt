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

import com.intellij.openapi.vfs.VirtualFile

interface DesignerCommonIssueProvider<T> {
  val source: T
  fun getIssues(file: IssuedFileData): List<Issue>
  fun getIssuedFileDataList(): List<IssuedFileData>

  /**
   * Callback when issue provider is removed.
   */
  fun onRemoved() = Unit
}

data class IssuedFileData(val file: VirtualFile, val source: Any?)

object EmptyIssueProvider : DesignerCommonIssueProvider<Any?> {
  override val source: Any? = null
  override fun getIssues(file: IssuedFileData): List<Issue> = emptyList()
  override fun getIssuedFileDataList(): List<IssuedFileData> = emptyList()
}

/**
 * An adapter of [DesignerCommonIssueProvider] to wrap the data from [IssueModel].
 */
class IssueModelProvider(private val issueModel: IssueModel, private val file: VirtualFile, private val onRemovedTask: () -> Unit = {})
  : DesignerCommonIssueProvider<IssueModel> {
  override val source: IssueModel = issueModel
  override fun getIssues(file: IssuedFileData): List<Issue> {
    return if (file.source == issueModel) issueModel.issues else emptyList()
  }

  override fun getIssuedFileDataList(): List<IssuedFileData> = listOf(IssuedFileData(file, issueModel))

  override fun onRemoved() = onRemovedTask()
}
