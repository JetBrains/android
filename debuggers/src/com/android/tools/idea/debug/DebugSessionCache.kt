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
package com.android.tools.idea.debug

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/*
 * This class is a general purpose cache that stores values tied to a specific debug process.
 * The cache is unique for each debug process and gets invalidated automatically once the
 * debug session has ended.
 *
 * Internally, the cache stores Map<String, MutableMap<Any, Any>> for every debug session.
 * Given a string token, you can access a map that holds arbitrary values based on your needs.
 *
 * # Example usage
 * Suggest you want to have a cache of `Map<com.sun.jdi.Location, ApkFile>` that is valid only while
 * the debug process is running. You could achieve it like this:
 * ```
 * val locationToApk = DebugSessionCache.getInstance(project).getMapping(debugProcess, LOCATION_TO_APK_TOKEN)
 * val apkFile = locationToApk?.get(location) as? ApkFile
 * ```
 */
@Service(Service.Level.PROJECT)
class DebugSessionCache private constructor(project: Project) {
  companion object {
    const val DEX_CACHE_TOKEN = "DEX"

    @JvmStatic fun getInstance(project: Project): DebugSessionCache = project.service()
  }

  private class DebugSessionCacheListener(private val project: Project) : DebuggerManagerListener {
    override fun sessionCreated(session: DebuggerSession) {
      getInstance(project).initCache(session.process)
    }

    override fun sessionRemoved(session: DebuggerSession) {
      getInstance(project).removeCache(session.process)
    }
  }

  private class Cache(val debugProcess: DebugProcess) {
    val tokenToMapping = mutableMapOf<String, MutableMap<Any, Any>>()
  }

  fun getMapping(debugProcess: DebugProcess, token: String): MutableMap<Any, Any>? {
    val cache = caches.firstOrNull { it.debugProcess == debugProcess } ?: return null
    return cache.tokenToMapping.getOrPut(token) { mutableMapOf() }
  }

  // There is one cache per debug process.
  // The size of the list will often be equal to 1 when debugging.
  private val caches = mutableListOf<Cache>()

  private fun initCache(debugProcess: DebugProcess) {
    caches.add(Cache(debugProcess))
  }

  private fun removeCache(debugProcess: DebugProcess) {
    caches.removeIf { it.debugProcess === debugProcess }
  }
}
