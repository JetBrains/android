// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.android.synthetic.idea.res

import com.android.tools.idea.diagnostics.crash.StudioCrashReporter
import com.android.tools.idea.diagnostics.crash.StudioPsiInvalidationTraceReport
import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.android.synthetic.res.AndroidLayoutGroupData

class IDEAndroidLayoutXmlFileManager(module: Module) : IDEAndroidCommonLayoutXmlFileManager(module) {
  override fun getLayouts(layoutGroup: AndroidLayoutGroupData): List<PsiFile> {
    // Sometimes due to a race of later-invoked runnables, the PsiFiles can be invalidated; for now log their invalidation trace
    layoutGroup.layouts.firstOrNull { !it.isValid }?.let {
      StudioCrashReporter.getInstance().submit(
        StudioPsiInvalidationTraceReport(it, ThreadDumper.dumpThreadsToString()))
    }
    return super.getLayouts(layoutGroup)
  }
}