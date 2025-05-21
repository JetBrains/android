/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.commit

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.CommitMetricsEvent
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class GitCommitTracker(
  private val trackedPath: String,
  private val intervalMinutes: Long = INTERVAL_IN_MINUTES) {

  companion object {
    private val LOG: Logger = Logger.getInstance(GitCommitTracker::class.java)
    private const val INITIAL_DELAY_MINUTES = 1L
    private const val INTERVAL_IN_MINUTES = 30L
    private const val COMMAND_TIMEOUT_SECONDS = 5L
  }

  private var trackingJob: ScheduledFuture<*>? = null

  enum class CommitType {
    LOCAL {
      override fun metricsCommitType() = CommitMetricsEvent.CommitType.LOCAL
    },
    REMOTE {
      override fun metricsCommitType() = CommitMetricsEvent.CommitType.REMOTE
    };

    abstract fun metricsCommitType(): CommitMetricsEvent.CommitType
  }

  data class CommitInfo(val authorTimestamp: Long, val commitTimestamp: Long, val type: CommitType, val first: Boolean)

  private fun runProcessWithTimeout(command: List<String>): String? {
    val timeoutSeconds = COMMAND_TIMEOUT_SECONDS
    val processBuilder = ProcessBuilder(command)
    processBuilder.directory(File(trackedPath))

    val process = processBuilder.start()
    val output = StringBuilder()

    val outputReaderFuture = ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val reader = InputStreamReader(process.inputStream)
        output.append(reader.readText())
      }
      catch (e: Exception) {
        LOG.error("Error reading process output", e)
      }
    }

    var success = true
    try {
      if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        LOG.warn("Process ${processBuilder.command()[0]} did not complete in allocated ${timeoutSeconds}s")
        process.destroy()
        success = false
      }
    }
    catch (_: InterruptedException) {
      LOG.warn("Process ${processBuilder.command()[0]} interrupted")

      success = false
    }


    if (!success) {
      return null
    }

    outputReaderFuture.get()
    if (process.exitValue() != 0) {
      return null
    }
    return output.toString()
  }

  fun startTracking() {
    LOG.info("Started commit tracking in directory $trackedPath")
    trackingJob = JobScheduler.getScheduler().scheduleWithFixedDelay(
      ::trackCommits,
      INITIAL_DELAY_MINUTES,
      intervalMinutes,
      TimeUnit.MINUTES)
  }

  fun stopTracking() {
    LOG.info("Stopped commit tracking in directory $trackedPath")
    trackingJob?.cancel(false)
  }

  fun trackCommits() {
    val success = doTrackCommits()
    if (!success) {
      stopTracking()
    }
  }

  private fun doTrackCommits(): Boolean {
    try {
      val userName = getUserGitInfo() ?: return false
      val commitTimestampStore = GitMetricsStore.getInstance()

      val localCommits = getNewCommits(userName, commitTimestampStore.lastLocalCommitTimestamp, CommitType.LOCAL)
      val remoteCommits = getNewCommits(userName, commitTimestampStore.lastRemoteCommitTimestamp, CommitType.REMOTE)

      if (!localCommits.isNullOrEmpty()) {
        recordCommits(localCommits)
        commitTimestampStore.lastLocalCommitTimestamp = localCommits.lastOrNull()?.commitTimestamp
      }
      if (!remoteCommits.isNullOrEmpty()) {
        recordCommits(remoteCommits)
        commitTimestampStore.lastRemoteCommitTimestamp = remoteCommits.lastOrNull()?.commitTimestamp
      }
    }
    catch (e: Exception) {
      LOG.error(e)
      return false
    }
    return true
  }

  private fun recordCommits(commits: List<CommitInfo>) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.COMMIT_METRICS_EVENT)
        .setCommitMetricsEvent(
          CommitMetricsEvent.newBuilder()
            .addAllCommitInfo(
              commits.map { commit ->
                CommitMetricsEvent.CommitInfo.newBuilder()
                  .setCommitType(commit.type.metricsCommitType())
                  .setCommitTimestamp(commit.commitTimestamp)
                  .setAuthorTimestamp(commit.authorTimestamp)
                  .setIsFirstCommit(commit.first)
                  .build()
              }
            )))
  }

  private fun getUserGitInfo(): String? {
    try {
      val command = listOf("git", "config", "user.email")
      val output = runProcessWithTimeout(command) ?: return null
      val userName = output.trim()
      if (userName.isNotEmpty())
        return userName
    }
    catch (_: Exception) {
    }
    LOG.warn("Unable to retrieve Git user information")
    return null
  }

  private fun getNewCommits(userName: String, lastCommitTimestamp: Long?, commitType: CommitType): List<CommitInfo>? {
    return try {
      val branchFilter: String

      when (commitType) {
        CommitType.LOCAL -> {
          branchFilter = "--branches"
        }
        CommitType.REMOTE -> {
          val gitCommand = listOf("git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")
          branchFilter = runProcessWithTimeout(gitCommand)?.trim() ?: return null
          if (branchFilter.startsWith("-") || branchFilter.isEmpty()) {
            return null
          }
        }
        else -> {
          return null
        }
      }

      val gitCommand = mutableListOf("git", "log", branchFilter, "--author=$userName", "--pretty=%at %ct")
      if (lastCommitTimestamp != null) {
        gitCommand.addAll(listOf("--since=${lastCommitTimestamp + 1}", "-n", "10"))
      }
      else {
        gitCommand.addAll(listOf("-n", "1"))
      }

      val output = runProcessWithTimeout(gitCommand) ?: return null
      val commits = mutableListOf<CommitInfo>()
      output.lines().forEach { line ->
        if (line.isNotBlank()) {
          val outputFields = line.trim().split(' ')
          val authorTimestamp = outputFields[0].toLong()
          val commitTimestamp = outputFields[1].toLong()
          commits.add(CommitInfo(authorTimestamp, commitTimestamp, commitType, false))
        }
      }
      commits.sortBy { it.commitTimestamp }

      // Update first entry to mark it a first one
      if (commits.isNotEmpty() && lastCommitTimestamp == null) {
        commits[0] = commits[0].copy(first = true)
      }
      commits.toList()
    }
    catch (e: Exception) {
      LOG.warn("Error: Unable to retrieve Git log.")
      null
    }
  }
}
