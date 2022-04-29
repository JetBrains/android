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
package org.jetbrains.android.refactoring

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

/**
 * Requests a sync to be performed after [BaseRefactoringProcessor.performRefactoring] is done but before
 * [BaseRefactoringProcessor.performPsiSpoilingRefactoring] runs and before the refactoring action is finished.
 *
 * This means that organizing imports and shortening references is done after the sync. We rely on the fact that
 * [BaseRefactoringProcessor.doRefactoring] drains the dumb mode tasks queue after running [BaseRefactoringProcessor.performRefactoring].
 */
fun syncBeforeFinishingRefactoring(project: Project, trigger: GradleSyncStats.Trigger, listener: GradleSyncListener?) {
  assert(ApplicationManager.getApplication().isDispatchThread)

  GradleSyncInvoker.getInstance().requestProjectSync(
    project,
    GradleSyncInvoker.Request(trigger),
    listener
  )
}