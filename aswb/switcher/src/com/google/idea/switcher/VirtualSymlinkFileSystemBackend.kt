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

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.nioFs.impl.MultiRoutingFileSystemBackend
import java.io.IOException
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting const val SWITCHES_ROOT = "ws"
@VisibleForTesting const val SWITCH_SELF_MARKER_NAME = ".aswb.self"
@VisibleForTesting const val SWITCH_ROOT_MARKER_NAME = ".aswb.root"

/** Application service managing virtual-to-physical workspace mappings. */
interface WorkspaceMappingManager {

  /** Flow broadcasting events when workspace mappings change. */
  val mappingChangeEvents: SharedFlow<Unit>

  /** The canonicalized virtual switches root directory. */
  val switchesRoot: Path

  /**
   * Configures or redirects the workspace mapping for [workspaceName] to point to [physicalWorkspaceDir].
   *
   * @param workspaceName The logical name of the workspace.
   * @param physicalWorkspaceDir The physical source workspace directory.
   * @return The virtual switch link path mounted inside the container.
   */
  fun setWorkspaceTarget(workspaceName: String, physicalWorkspaceDir: Path): Path

  /**
   * Retrieves the physical target of a workspace name.
   *
   * @param workspaceName The logical name of the workspace.
   * @return The physical workspace target path, or null if unmapped.
   */
  fun getWorkspaceTarget(workspaceName: String): Path?

  /**
   * Retrieves the virtual workspace directory path for [workspaceName].
   *
   * @param workspaceName The logical name of the workspace.
   * @return The virtual workspace directory path.
   */
  fun getWorkspacePath(workspaceName: String): Path

  /**
   * Recovers the workspace name from a virtual workspace directory path.
   *
   * @param path The virtual workspace path (or a child path under it).
   * @return The logical name of the workspace, or null if the path does not match the workspace pattern.
   */
  fun getWorkspaceName(path: Path): String?

  /**
   * Resolves a virtual switch workspace path into its underlying canonical physical path on disk.
   *
   * @param path The incoming path.
   * @return The physical path.
   */
  fun unwrapPhysicalPath(path: Path): Path

  /** Testing verification API. */
  val testApi: TestApi

  interface TestApi {
    val computeCount: AtomicInteger
  }

  companion object {
    fun getInstance(): WorkspaceMappingManager = WorkspaceMappingManagerImpl
  }
}

internal object WorkspaceMappingManagerImpl : WorkspaceMappingManager {
  private val logger = logger<WorkspaceMappingManagerImpl>()
  override val switchesRoot: Path = PathManager.getSystemDir().resolve(SWITCHES_ROOT).toAbsolutePath().normalize()
  private val systemPrefixPath: String = switchesRoot.toString()
  private val _mappingChangeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  override val mappingChangeEvents: SharedFlow<Unit> = _mappingChangeEvents.asSharedFlow()

  private val discoveredRootsCache = ConcurrentHashMap<String, VirtualSymlinkFileSystem>()
  private val counter = AtomicInteger(0)

  internal fun getWorkspaceRoot(path: String): String? {
    if (!path.startsWith(systemPrefixPath)) return null
    var startIndex = systemPrefixPath.length
    while (startIndex < path.length && path[startIndex] == '/') {
      startIndex++
    }
    if (startIndex >= path.length) return null
    val nextSlash = path.indexOf('/', startIndex)
    return if (nextSlash == -1) path else path.substring(0, nextSlash)
  }

  private fun configureWorkspaceTarget(workspaceName: String, physicalWorkspaceDir: Path): Path {
    val workspacePath = Path.of(workspacePath(workspaceName))
    Files.createDirectories(workspacePath.parent)
    val currentTarget =
      try {
        val markerSelf = workspacePath.resolve(SWITCH_SELF_MARKER_NAME)
        if (Files.isSymbolicLink(markerSelf)) Files.readSymbolicLink(markerSelf) else null
      } catch (e: IOException) {
        null
      }

    if (currentTarget?.toString() != physicalWorkspaceDir.toString()) {
      logger.info("Configuring workspace target for '$workspaceName' -> '$physicalWorkspaceDir' (previous target: '$currentTarget')")
      val markerSelf = workspacePath.resolve(SWITCH_SELF_MARKER_NAME)
      Files.deleteIfExists(markerSelf)
      Files.createSymbolicLink(
        markerSelf,
        workspacePath.resolve(SWITCH_ROOT_MARKER_NAME).resolve(Path.of("/").relativize(physicalWorkspaceDir)),
      )
      discoveredRootsCache[workspacePath.toString()]?.reload()
      publishMappingChangedEvent()
      WorkspaceFileWatcher.getInstance()?.handleMappingChange()
    } else {
      logger.info("Workspace target for '$workspaceName' unchanged ('$currentTarget'); skipping symlink update")
    }
    return workspacePath.toRealPath()
  }

  internal fun publishMappingChangedEvent() {
    _mappingChangeEvents.tryEmit(Unit)
  }

  override fun setWorkspaceTarget(workspaceName: String, physicalWorkspaceDir: Path): Path =
    configureWorkspaceTarget(workspaceName, physicalWorkspaceDir)

  override fun getWorkspaceTarget(workspaceName: String): Path? {
    Path.of(workspacePath(workspaceName)) // Populate cache.
    return discoveredRootsCache[workspacePath(workspaceName)]?.let { switchesRoot.fileSystem.getPath(it.dest.toString()) }
  }

  override fun getWorkspacePath(workspaceName: String): Path {
    return Path.of(workspacePath(workspaceName))
  }

  override fun getWorkspaceName(path: Path): String? {
    val normalizedPath = path.normalize()
    if (!normalizedPath.startsWith(switchesRoot)) return null
    if (normalizedPath == switchesRoot) return null
    val relative = switchesRoot.relativize(normalizedPath)
    val name = relative.getName(0).toString()
    return if (name.isEmpty()) null else name
  }

  override fun unwrapPhysicalPath(path: Path): Path {
    if (!path.isAbsolute) return path
    val root = getWorkspaceRoot(path.toString()) ?: return path
    // Resolve via the physical symlink.
    return Path.of(root).resolve(SWITCH_ROOT_MARKER_NAME).resolve(Path.of("/").relativize(path)).toRealPath()
  }

  fun compute(localFS: FileSystem, sanitizedPath: String): VirtualSymlinkFileSystem? {
    val wsRoot = getWorkspaceRoot(sanitizedPath) ?: return null
    testApi.computeCount.incrementAndGet()

    return discoveredRootsCache.computeIfAbsent(wsRoot) {
      publishMappingChangedEvent()
      VirtualSymlinkFileSystem(localFS, this, localFS.getPath(wsRoot))
    }
  }

  fun getCustomRoots(): List<String> {
    val activeRoots = discoveredRootsCache.keys.map { it.toString() }
    return if (activeRoots.isNotEmpty()) {
      activeRoots
    } else {
      listOf(System.getProperty("user.home"), "/tmp", systemPrefixPath)
    }
  }

  private fun workspacePath(workspaceName: String): String = "$systemPrefixPath/$workspaceName"

  override val testApi =
    object : WorkspaceMappingManager.TestApi {
      override val computeCount = counter
    }
}

class VirtualSymlinkFileSystemBackend : MultiRoutingFileSystemBackend {

  private val managerImpl: WorkspaceMappingManagerImpl
    get() = WorkspaceMappingManager.getInstance() as WorkspaceMappingManagerImpl

  override fun compute(localFS: FileSystem, sanitizedPath: String): FileSystem? {
    return managerImpl.compute(localFS, sanitizedPath)
  }

  override fun getCustomRoots(): List<String> {
    return managerImpl.getCustomRoots()
  }

  override fun getCustomFileStores(localFS: FileSystem): List<FileStore> = emptyList()
}

private fun FileSystemProvider.isSymbolicLink(path: Path): Boolean =
  this.getFileAttributeView(path, BasicFileAttributeView::class.java).readAttributes().isSymbolicLink
