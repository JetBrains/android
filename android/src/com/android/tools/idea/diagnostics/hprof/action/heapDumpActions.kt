/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.hprof.action

import com.android.tools.idea.diagnostics.hprof.action.HeapDumpSnapshotRunnable.AnalysisOption.IMMEDIATE
import com.android.tools.idea.diagnostics.hprof.action.HeapDumpSnapshotRunnable.AnalysisOption.SCHEDULE_ON_NEXT_START
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class UserInvokedFullAnalysisAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    HeapDumpSnapshotRunnable(true, IMMEDIATE).run()
  }
}

class UserInvokedHeapDumpSnapshotAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    HeapDumpSnapshotRunnable(true, SCHEDULE_ON_NEXT_START).run()
  }
}

class NonuserInvokedHeapDumpSnapshotAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    HeapDumpSnapshotRunnable(false, SCHEDULE_ON_NEXT_START).run()
  }
}
