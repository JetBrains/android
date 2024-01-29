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
package com.android.tools.idea.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable

/**
 * This is a workaround to a problem introduced by the Intellij 2023.3 merge. After the merge, with the new threading model, the UI thread
 * might not have the read lock. When invoking [DumbService#runReadActionInSmartMode] from the UI thread, if it does not have the read lock, the method
 * might decide to wait for smart mode in the UI thread causing a deadlock.
 * [DumbService#runReadActionInSmartMode] checks if the thread has the read lock to avoid a deadlock, but it does not check if the call is
 * happening from the UI thread.
 * Calling [DumbService#runReadActionInSmartMode] from the UI thread should never happen, but we have a number of places where this does happen
 * and freezes the IDE.
 * This method is a workaround to the problem and should not have NEW usages.
 */
@Deprecated("Do not use this method, you should call DumbService.runReadActionInSmartMode from a worker thread instead")
fun <T> uiSafeRunReadActionInSmartMode(project: Project, computable: Computable<T>): T {
  if (ApplicationManager.getApplication().isDispatchThread && !ApplicationManager.getApplication().isReadAccessAllowed) {
    return runReadAction {
      DumbService.getInstance(project).runReadActionInSmartMode(computable)
    }
  }

  return DumbService.getInstance(project).runReadActionInSmartMode(computable)
}