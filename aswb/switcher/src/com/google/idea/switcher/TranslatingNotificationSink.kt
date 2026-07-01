/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.switcher

import com.intellij.notification.NotificationListener
import com.intellij.openapi.util.Pair as IJPair
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink
import com.intellij.openapi.vfs.local.PluggableFileWatcher
import java.io.File

/**
 * A [FileWatcherNotificationSink] wrapper that translates physical file paths from a custom background file watcher back into virtual
 * workspace paths before forwarding to the VFS.
 */
class TranslatingNotificationSink(
  private val delegate: FileWatcherNotificationSink,
  private val delegator: PluggableFileWatcher,
  private val virtualRootPrefix: String,
  private val physicalRootPrefix: String,
) : FileWatcherNotificationSink {

  private val physicalPrefix =
    if (physicalRootPrefix.endsWith(File.separator)) physicalRootPrefix else "$physicalRootPrefix${File.separator}"
  private val virtualPrefix = if (virtualRootPrefix.endsWith(File.separator)) virtualRootPrefix else "$virtualRootPrefix${File.separator}"

  private fun translatePath(path: String): String? {
    if (path.startsWith(physicalPrefix)) {
      return virtualPrefix + path.substring(physicalPrefix.length)
    }
    if (path == physicalRootPrefix) {
      return virtualRootPrefix
    }
    return null
  }

  override fun notifyMapping(mapping: Collection<IJPair<String, String>>) {
    val translatedMapping =
      mapping.mapNotNull { pair ->
        val translatedFirst = translatePath(pair.first)
        val translatedSecond = translatePath(pair.second)
        if (translatedFirst != null && translatedSecond != null) {
          IJPair.create(translatedFirst, translatedSecond)
        } else {
          null
        }
      }
    if (translatedMapping.isNotEmpty()) {
      delegate.notifyMapping(translatedMapping)
    }
  }

  override fun notifyDirtyPath(path: String) {
    translatePath(path)?.let { delegate.notifyDirtyPath(it) }
  }

  override fun notifyPathCreatedOrDeleted(path: String) {
    translatePath(path)?.let { delegate.notifyPathCreatedOrDeleted(it) }
  }

  override fun notifyDirtyDirectory(path: String) {
    translatePath(path)?.let { delegate.notifyDirtyDirectory(it) }
  }

  override fun notifyDirtyPathRecursive(path: String) {
    translatePath(path)?.let { delegate.notifyDirtyPathRecursive(it) }
  }

  override fun notifyReset(cause: String?) {
    delegate.notifyReset(cause)
  }

  override fun notifyUserOnFailure(message: String, listener: NotificationListener?) {
    delegate.notifyUserOnFailure(message, listener)
  }

  override fun notifyManualWatchRoots(watcher: PluggableFileWatcher, roots: Collection<String>) {
    val translatedRoots = roots.mapNotNull { translatePath(it) }
    delegate.notifyManualWatchRoots(delegator, translatedRoots)
  }
}
