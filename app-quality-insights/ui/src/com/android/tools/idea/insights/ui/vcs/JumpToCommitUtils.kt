/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.ui.vcs

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsLogContentUtil.runInMainLog
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiEx
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

private val LOG = Logger.getInstance("JumpToCommitUtils")

/** Brings up the "Log" view for the given commit. */
internal fun jumpToRevision(project: Project, revision: String) {
  tryCreateHash(revision)?.let { jumpToRevision(project, it) }
}

/** Returns [Hash] or null if the given [revision] is malformed. */
private fun tryCreateHash(revision: String): Hash? {
  return try {
    HashImpl.build(revision)
  } catch (e: Exception) {
    // TODO: notify users
    LOG.warn("Can't create hash from $revision.")
    null
  }
}

// Copied from GitShowCommitInLogAction#jumpToRevision
internal fun jumpToRevision(project: Project, hash: Hash) {
  runInMainLog(
    project,
    Consumer { logUi: MainVcsLogUi -> jumpToRevisionUnderProgress(project, logUi, hash) }
  )
}

// Copied from GitShowCommitInLogAction#jumpToRevision
private fun jumpToRevisionUnderProgress(project: Project, logUi: VcsLogUiEx, hash: Hash) {
  val future = logUi.vcsLog.jumpToReference(hash.asString())
  if (!future.isDone) {
    ProgressManager.getInstance()
      .run(
        object :
          Task.Backgroundable(
            project,
            "Searching for Revision ${hash.asString()}",
            false,
            ALWAYS_BACKGROUND
          ) {
          override fun run(indicator: ProgressIndicator) {
            try {
              future.get()
            } catch (ignored: CancellationException) {} catch (
              ignored: InterruptedException) {} catch (e: ExecutionException) {
              LOG.warn(e)
            }
          }
        }
      )
  }
}
