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

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink
import com.intellij.openapi.vfs.local.PluggableFileWatcher
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

private val logger = logger<WorkspaceFileWatcher>()

/**
 * A pluggable file watcher that intercepts virtual switcher workspace paths, routing their watching and target-switching diffs to
 * specialized environment watchers.
 */
class WorkspaceFileWatcher : PluggableFileWatcher(), Disposable {

  private val lock = Any()
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private lateinit var notificationSink: FileWatcherNotificationSink

  private class ActiveWatcherState(
    val virtualRoot: String,
    var physicalRoot: Path,
    var translatingSink: TranslatingNotificationSink,
    var watcher: WorkspaceWatcher,
    var lastVirtualRecursive: List<Path> = emptyList(),
    var lastVirtualFlat: List<Path> = emptyList(),
  )

  private class ExpectedWatcherConfig(val physicalRoot: Path, val recursivePaths: List<Path>, val flatPaths: List<Path>)

  private val activeWatchers = ConcurrentHashMap<String, ActiveWatcherState>()

  override fun initialize(notificationSink: FileWatcherNotificationSink) {
    this.notificationSink = notificationSink

    // Subscribe to mapping changes
    scope.launch { WorkspaceMappingManager.getInstance().mappingChangeEvents.collect { handleMappingChange() } }
  }

  override fun startup() {
    // No-op. PluggableFileWatcher.startup() is test-only or lifecycle start.
  }

  override fun shutdown() {
    cleanupWatchers()
  }

  override fun dispose() {
    scope.cancel()
    cleanupWatchers()
  }

  private fun cleanupWatchers() {
    synchronized(lock) {
      activeWatchers.values.forEach { state -> state.watcher.shutdown() }
      activeWatchers.clear()
    }
  }

  override fun isOperational(): Boolean {
    return activeWatchers.values.all { it.watcher.isOperational() }
  }

  override fun isSettingRoots(): Boolean = false

  override fun setWatchRoots(recursiveCanonicalPaths: List<String>, flatCanonicalPaths: List<String>, shuttingDown: Boolean) {
    if (shuttingDown) {
      logger.info("WorkspaceFileWatcher shutting down; cleaning up active watchers")
      cleanupWatchers()
      return
    }

    synchronized(lock) {
      val switchesRoot = WorkspaceMappingManager.getInstance().switchesRoot.toString()
      logger.info("Configuring watch roots: ${recursiveCanonicalPaths.size} recursive, ${flatCanonicalPaths.size} flat")

      // 1. Partition roots: find paths under virtual switchesRoot
      val recursiveByWorkspace = groupPathsByWorkspaceRoot(recursiveCanonicalPaths, switchesRoot)
      val flatByWorkspace = groupPathsByWorkspaceRoot(flatCanonicalPaths, switchesRoot)

      val activeWorkspaceRoots = recursiveByWorkspace.keys + flatByWorkspace.keys

      // 2. Build expected configurations
      val expectedConfigs =
        activeWorkspaceRoots
          .mapNotNull { virtualRootPath ->
            val workspaceName = WorkspaceMappingManager.getInstance().getWorkspaceName(Path.of(virtualRootPath)) ?: return@mapNotNull null
            val physicalRoot = WorkspaceMappingManager.getInstance().getWorkspaceTarget(workspaceName) ?: return@mapNotNull null
            val recursivePaths = recursiveByWorkspace[virtualRootPath]?.map { Path.of(it) } ?: emptyList()
            val flatPaths = flatByWorkspace[virtualRootPath]?.map { Path.of(it) } ?: emptyList()
            virtualRootPath to ExpectedWatcherConfig(physicalRoot, recursivePaths, flatPaths)
          }
          .toMap()

      applyConfigurationsLocked(expectedConfigs)

      // 3. Report paths that we do not watch back to the platform
      val unrecognizedRecursive = recursiveCanonicalPaths.filter { !it.startsWith(switchesRoot) }
      val unrecognizedFlat = flatCanonicalPaths.filter { !it.startsWith(switchesRoot) }
      val manualRoots = unrecognizedRecursive + unrecognizedFlat
      logger.info(
        "Partitioned watch roots for ${expectedConfigs.size} workspace(s): ${expectedConfigs.keys.joinToString()}; ${manualRoots.size} manual/unrecognized root(s)"
      )
      notificationSink.notifyManualWatchRoots(this, manualRoots)
    }
  }

  internal fun handleMappingChange() {
    synchronized(lock) {
      logger.info("Handling workspace mapping change for ${activeWatchers.size} active watcher(s)")
      // Build expected configurations based on current active watchers and their updated mappings
      val expectedConfigs =
        activeWatchers.values
          .mapNotNull { state ->
            val workspaceName = WorkspaceMappingManager.getInstance().getWorkspaceName(Path.of(state.virtualRoot)) ?: return@mapNotNull null
            val physicalRoot = WorkspaceMappingManager.getInstance().getWorkspaceTarget(workspaceName) ?: return@mapNotNull null
            state.virtualRoot to ExpectedWatcherConfig(physicalRoot, state.lastVirtualRecursive, state.lastVirtualFlat)
          }
          .toMap()

      applyConfigurationsLocked(expectedConfigs)
    }
  }

  private fun applyConfigurationsLocked(expected: Map<String, ExpectedWatcherConfig>) {
    // 1. Remove watchers for roots that are no longer expected
    val staleRoots = activeWatchers.keys - expected.keys
    staleRoots.forEach { staleRoot ->
      logger.info("Removing file watcher for deactivated workspace root '$staleRoot'")
      activeWatchers.remove(staleRoot)?.let { state -> state.watcher.shutdown() }
    }

    // 2. Add or update watchers for expected configurations
    expected.forEach { (virtualRootPath, config) ->
      val state = activeWatchers[virtualRootPath]
      if (state == null) {
        // Spin up a new watcher
        logger.info("Creating new file watcher for workspace root '$virtualRootPath' -> '${config.physicalRoot}'")
        val translatingSink = TranslatingNotificationSink(notificationSink, this, virtualRootPath, config.physicalRoot.toString())
        val watcher =
          WorkspaceWatcherProvider.EP_NAME.extensionList.firstNotNullOfOrNull { provider ->
            provider.createWatcher(config.physicalRoot, translatingSink)
          }

        if (watcher != null) {
          val newState =
            ActiveWatcherState(virtualRootPath, config.physicalRoot, translatingSink, watcher, config.recursivePaths, config.flatPaths)
          activeWatchers[virtualRootPath] = newState
          applyRootsToWatcher(newState)
        }
      } else {
        // Update paths for existing watcher
        val oldPhysicalRoot = state.physicalRoot
        val newPhysicalRoot = config.physicalRoot
        val pathsChanged = state.lastVirtualRecursive != config.recursivePaths || state.lastVirtualFlat != config.flatPaths

        state.lastVirtualRecursive = config.recursivePaths
        state.lastVirtualFlat = config.flatPaths

        if (oldPhysicalRoot == newPhysicalRoot) {
          if (pathsChanged) {
            logger.info(
              "Updating watch roots for workspace '$virtualRootPath' (${config.recursivePaths.size} recursive, ${config.flatPaths.size} flat)"
            )
            applyRootsToWatcher(state)
          }
        } else {
          // Physical root changed (e.g. target switched to a different workspace). Recreate the watcher.
          logger.info("Workspace mapping redirected for '$virtualRootPath': '$oldPhysicalRoot' -> '$newPhysicalRoot'")
          recreateWatcherLocked(state, newPhysicalRoot)
        }
      }
    }
  }

  private fun groupPathsByWorkspaceRoot(paths: List<String>, switchesRoot: String): Map<String, List<String>> {
    return paths
      .filter { it.startsWith(switchesRoot) }
      .mapNotNull { path ->
        val workspaceRoot = WorkspaceMappingManagerImpl.getWorkspaceRoot(path) ?: return@mapNotNull null
        workspaceRoot to path
      }
      .groupBy({ it.first }, { it.second })
  }

  private fun applyRootsToWatcher(state: ActiveWatcherState) {
    val physicalRecursive = state.lastVirtualRecursive.map { translateVirtualToPhysical(it, state.virtualRoot, state.physicalRoot) }
    val physicalFlat = state.lastVirtualFlat.map { translateVirtualToPhysical(it, state.virtualRoot, state.physicalRoot) }
    logger.debug(
      "Applied watch roots to watcher for '${state.virtualRoot}': ${physicalRecursive.size} physical recursive, ${physicalFlat.size} physical flat"
    )
    state.watcher.setWatchRoots(physicalRecursive, physicalFlat)
  }

  private fun translateVirtualToPhysical(virtualPath: Path, virtualRoot: String, physicalRoot: Path): Path {
    val virtualRootPath = Path.of(virtualRoot)
    if (virtualPath.startsWith(virtualRootPath)) {
      return physicalRoot.resolve(virtualRootPath.relativize(virtualPath))
    }
    return virtualPath
  }

  private fun recreateWatcherLocked(state: ActiveWatcherState, newPhysicalRoot: Path) {
    val oldPhysicalRoot = state.physicalRoot

    // 1. Shutdown old watcher
    logger.info("Recreating watcher for workspace '${state.virtualRoot}': shutting down old watcher on '$oldPhysicalRoot'")
    state.watcher.shutdown()

    // Update state and sink early to avoid race conditions in the background coroutine
    state.physicalRoot = newPhysicalRoot
    val newSink = TranslatingNotificationSink(notificationSink, this, state.virtualRoot, newPhysicalRoot.toString())
    state.translatingSink = newSink

    // 2. Query and run background diff
    val diffOperation =
      WorkspaceWatcherProvider.EP_NAME.extensionList.firstNotNullOfOrNull { provider ->
        provider.createDiffOperation(oldPhysicalRoot, newPhysicalRoot)
      }

    if (diffOperation != null) {
      val oldPhysicalPaths =
        (state.lastVirtualRecursive + state.lastVirtualFlat)
          .map { path -> translateVirtualToPhysical(path, state.virtualRoot, oldPhysicalRoot) }
          .toSet()

      logger.info("Executing switch diff operation from '$oldPhysicalRoot' to '$newPhysicalRoot' on ${oldPhysicalPaths.size} path(s)")
      diffOperation(oldPhysicalPaths, newSink)
      logger.info("Switch diff operation completed for '${state.virtualRoot}'")
    }

    // 3. Create new watcher
    val newWatcher =
      WorkspaceWatcherProvider.EP_NAME.extensionList.firstNotNullOfOrNull { provider -> provider.createWatcher(newPhysicalRoot, newSink) }

    if (newWatcher != null) {
      logger.info("Started new watcher for workspace '${state.virtualRoot}' on '$newPhysicalRoot'")
      state.watcher = newWatcher
      applyRootsToWatcher(state)
    } else {
      logger.error("No WorkspaceWatcherProvider found to watch new physical root: $newPhysicalRoot")
      activeWatchers.remove(state.virtualRoot)
    }
  }

  companion object {
    fun getInstance(): WorkspaceFileWatcher? = EP_NAME.findExtension(WorkspaceFileWatcher::class.java)
  }
}
