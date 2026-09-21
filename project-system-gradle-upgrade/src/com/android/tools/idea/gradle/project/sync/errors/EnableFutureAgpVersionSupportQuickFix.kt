// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.project.sync.errors
// The whole file is a JetBrains patch for enabling future AGP support studio flag

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.plugin.AgpVersions
import com.android.tools.idea.gradle.project.sync.AndroidSyncExceptionType
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import java.util.concurrent.CompletableFuture

internal fun BuildIssueComposer.enableFutureAgpVersionSupportQuickFix(rootCauseType: AndroidSyncExceptionType) {
  if (rootCauseType !== AndroidSyncExceptionType.AGP_VERSION_TOO_NEW) return
  if (StudioFlags.SUPPORT_FUTURE_AGP_VERSIONS.get()) {
    val supportedVersion = AgpVersions.latestKnown.toString()
    val unsupportedVersion = Version.parseVersion(supportedVersion)?.run { Version(major, minor + 1, 0).toString() } ?: "unknown"
    addDescriptionOnNewLine(AgpUpgradeBundle.message("futureAgpVersions.enabled.description", unsupportedVersion, supportedVersion))
  }
  else {
    addQuickFix(EnableFutureAgpVersionSupportQuickFix())
  }
}

/**
 *
 * Hyperlink that turns on the support of future Android Gradle plugin versions. The IDE reads the flag at the start of a sync, thus the new
 * value needs a restart.
 */
class EnableFutureAgpVersionSupportQuickFix : DescribedBuildIssueQuickFix {
  override val id: String = "android.gradle.plugin.enable.future.agp.versions"
  override val description: String = AgpUpgradeBundle.message("futureAgpVersions.quickFix.enable")

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    StudioFlags.SUPPORT_FUTURE_AGP_VERSIONS.override(true)
    ApplicationManager.getApplication().restart()

    return CompletableFuture.completedFuture(Unit)
  }
}
