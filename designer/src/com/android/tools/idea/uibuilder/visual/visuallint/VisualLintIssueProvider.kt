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
import com.android.tools.idea.common.error.IssueProvider
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.google.common.collect.ImmutableCollection
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.lang.ref.WeakReference

abstract class VisualLintIssueProvider(parentDisposable: Disposable) : IssueProvider(), Disposable {
  private val issues = VisualLintIssues()

  /** If using in UI Check mode, represents the Compose Preview instance being checked. */
  var uiCheckInstanceId: String? = null

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
    issueListBuilder.addAll(issues.list)
  }

  override fun dispose() {
    clear()
  }

  private fun addIssue(issue: VisualLintRenderIssue) {
    customizeIssue(issue)
    this.issues.add(issue)
  }

  fun addAllIssues(issues: List<VisualLintRenderIssue>) = issues.forEach { addIssue(it) }

  fun getIssues() = issues.list

  fun clear() = issues.clear()

  fun getUnsuppressedIssues() = issues.list.filterNot { it.isSuppressed() }

  /** This is applied to all issues added to this [IssueProvider] */
  abstract fun customizeIssue(issue: VisualLintRenderIssue)

  @Suppress("UnstableApiUsage")
  class VisualLintIssueSource(models: Set<NlModel>, components: List<NlComponent>) : IssueSource {
    private val modelRefs = models.map { WeakReference(it) }.toMutableList()
    private val componentRefs = components.map { WeakReference(it) }.toMutableList()

    val models: Set<NlModel>
      get() = synchronized(modelRefs) { modelRefs.mapNotNull { it.get() }.toSet() }

    val components: List<NlComponent>
      get() = synchronized(componentRefs) { componentRefs.mapNotNull { it.get() }.toList() }

    override val files: Set<VirtualFile>
      get() = models.map { BackedVirtualFile.getOriginFileIfBacked(it.virtualFile) }.toSet()

    override val displayText = ""

    fun addComponent(component: NlComponent) {
      synchronized(componentRefs) { componentRefs.add(WeakReference(component)) }
    }

    fun addModel(model: NlModel) {
      synchronized(modelRefs) { modelRefs.add(WeakReference(model)) }
    }
  }
}

interface VisualLintSuppressTask : Runnable {
  fun isValid(): Boolean
}
