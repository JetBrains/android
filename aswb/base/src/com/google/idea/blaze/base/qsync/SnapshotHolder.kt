/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync

import com.google.common.collect.ImmutableMap
import com.google.common.io.ByteSource
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import java.util.Optional

/** Keeps a reference to the most up-to date [QuerySyncProjectSnapshot] instance.  */
class SnapshotHolder {
  private val lock = Any()
  private var currentInstance: QuerySyncProjectSnapshot? = null

  private val listeners = mutableListOf<QuerySyncProjectListener>()

  fun addListener(listener: QuerySyncProjectListener) {
    synchronized(lock) {
      listeners.add(listener)
    }
  }

  @Throws(BuildException::class)
  fun setCurrent(context: Context<*>, querySyncProject: QuerySyncProject, newInstance: QuerySyncProjectSnapshot) {
    val listeners  = synchronized(lock) {
      if (currentInstance == newInstance) {
        return
      }
      currentInstance = newInstance
      this.listeners.toList()
    }
    for (l in listeners) {
      l.onNewProjectSnapshot(context, querySyncProject, newInstance)
    }
  }

  val current: Optional<QuerySyncProjectSnapshot>
    get() = synchronized(lock) { Optional.ofNullable<QuerySyncProjectSnapshot>(currentInstance) }

  operator fun invoke(): QuerySyncProjectSnapshot? = synchronized(lock) { currentInstance }

  fun getBugreportFiles(): Map<String, ByteSource> {
    val instance = synchronized(lock) { currentInstance }
    return ImmutableMap.of(
      "projectProto",
      instance?.let { ByteSource.wrap(it.project().toByteArray()) } ?: ByteSource.empty()
    )
  }
}
