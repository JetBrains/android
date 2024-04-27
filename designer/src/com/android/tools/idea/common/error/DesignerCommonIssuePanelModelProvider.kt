/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.Invoker
import com.intellij.util.concurrency.InvokerSupplier

interface DesignerCommonIssuePanelModelProvider {
  fun createModel(): DesignerCommonIssueModel

  companion object {
    fun getInstance(project: Project): DesignerCommonIssuePanelModelProvider {
      return project.getService(DesignerCommonIssuePanelModelProvider::class.java)
    }
  }
}

class AsyncDesignerCommonIssuePanelModelProvider : DesignerCommonIssuePanelModelProvider {
  override fun createModel() = AsyncableDesignerCommonIssueModel()
}

/**
 * Implement the [InvokerSupplier] so [com.intellij.ui.tree.AsyncTreeModel] can use [getInvoker] to
 * have different background thread.
 */
class AsyncableDesignerCommonIssueModel : DesignerCommonIssueModel(), InvokerSupplier {
  private val invoker = Invoker.forBackgroundThreadWithReadAction(this)

  override fun getInvoker(): Invoker = invoker
}
