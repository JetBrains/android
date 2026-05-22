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

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.UserPrincipal
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider
import kotlinx.io.IOException

/** [java.nio.file.FileSystem] implementation holding active virtual-to-physical mapping context. */
class VirtualSymlinkFileSystem
internal constructor(val delegate: FileSystem, internal val manager: WorkspaceMappingManagerImpl, val source: Path) : FileSystem() {
  init {
    if (source.fileSystem.provider() != delegate.provider()) error("File system mismatch: ${source.fileSystem} and $delegate")
  }

  val basePath: String = source.toString()
  val basePathSlash: String = source.toString() + "/"
  @Volatile private var dest_: Path? = null
  val dest: Path
    get() {

      while (true) {
        val result = dest_
        if (result != null) return result
        dest_ =
          try {
              if (
                delegate
                  .provider()
                  .getFileAttributeView(source, BasicFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
                  .readAttributes()
                  .isSymbolicLink
              ) {
                delegate.provider().readSymbolicLink(source)
              } else {
                source.toString()
              }
            } catch (e: IOException) {
              source.toString()
            }
            .let { delegate.getPath(it.toString()) }
            .also { manager.publishMappingChangedEvent() }
      }
    }

  internal fun reload() {
    dest_ = null
    manager.publishMappingChangedEvent()
  }

  private val provider = VirtualSymlinkFileSystemProvider(this)

  override fun provider(): VirtualSymlinkFileSystemProvider = provider

  override fun close() = delegate.close()

  override fun isOpen(): Boolean = delegate.isOpen

  override fun isReadOnly(): Boolean = delegate.isReadOnly

  override fun getSeparator(): String = delegate.separator

  override fun getRootDirectories(): Iterable<Path> = delegate.rootDirectories

  override fun getFileStores(): Iterable<FileStore> = delegate.fileStores

  override fun supportedFileAttributeViews(): Set<String> = delegate.supportedFileAttributeViews()

  override fun getPath(first: String, vararg more: String): Path {
    val delegatePath = delegate.getPath(first, *more)
    return createVirtualSymlinkPath(this, delegatePath)
  }

  override fun getPathMatcher(syntaxAndPattern: String): PathMatcher = delegate.getPathMatcher(syntaxAndPattern)

  override fun getUserPrincipalLookupService(): UserPrincipalLookupService = delegate.userPrincipalLookupService

  override fun newWatchService(): WatchService = delegate.newWatchService()
}

class VirtualSymlinkBasicFileAttributes(private val delegate: BasicFileAttributes, private val isSwitcherPath: Boolean) :
  BasicFileAttributes {

  override fun lastModifiedTime(): FileTime = delegate.lastModifiedTime()

  override fun lastAccessTime(): FileTime = delegate.lastAccessTime()

  override fun creationTime(): FileTime = delegate.creationTime()

  override fun isRegularFile(): Boolean = delegate.isRegularFile

  override fun isDirectory(): Boolean = if (isSwitcherPath) true else delegate.isDirectory

  override fun isSymbolicLink(): Boolean = if (isSwitcherPath) false else delegate.isSymbolicLink

  override fun isOther(): Boolean = delegate.isOther

  override fun size(): Long = delegate.size()

  override fun fileKey(): Any? = delegate.fileKey()
}

class VirtualSymlinkPosixFileAttributes(private val delegate: PosixFileAttributes, private val isSwitcherPath: Boolean) :
  PosixFileAttributes {

  override fun lastModifiedTime(): FileTime = delegate.lastModifiedTime()

  override fun lastAccessTime(): FileTime = delegate.lastAccessTime()

  override fun creationTime(): FileTime = delegate.creationTime()

  override fun isRegularFile(): Boolean = delegate.isRegularFile

  override fun isDirectory(): Boolean = if (isSwitcherPath) true else delegate.isDirectory

  override fun isSymbolicLink(): Boolean = if (isSwitcherPath) false else delegate.isSymbolicLink

  override fun isOther(): Boolean = delegate.isOther

  override fun size(): Long = delegate.size()

  override fun fileKey(): Any? = delegate.fileKey()

  override fun owner(): UserPrincipal = delegate.owner()

  override fun group(): GroupPrincipal = delegate.group()

  override fun permissions(): Set<PosixFilePermission> = delegate.permissions()
}

/** [java.nio.file.Path] implementation representing virtualized workspace paths. */
class VirtualSymlinkPath(private val fileSystem: VirtualSymlinkFileSystem, val virtualPath: Path) : Path {
  init {
    if (virtualPath is VirtualSymlinkPath) error("$virtualPath must not be VirtualSymlinkPath")
  }

  override fun getFileSystem(): VirtualSymlinkFileSystem = fileSystem

  override fun isAbsolute(): Boolean = virtualPath.isAbsolute

  override fun getFileName(): Path? = virtualPath.fileName?.let { VirtualSymlinkPath(fileSystem, it) }

  override fun getRoot(): Path? = virtualPath.root?.let { VirtualSymlinkPath(fileSystem, it) }

  override fun getParent(): Path? {
    val vp = virtualPath.parent ?: return null
    return VirtualSymlinkPath(fileSystem, vp)
  }

  override fun getNameCount(): Int = virtualPath.nameCount

  override fun getName(index: Int): Path = virtualPath.getName(index)

  override fun subpath(beginIndex: Int, endIndex: Int): Path = virtualPath.subpath(beginIndex, endIndex)

  override fun startsWith(other: Path): Boolean = virtualPath.startsWith(other.toString())

  override fun startsWith(other: String): Boolean = virtualPath.startsWith(other)

  override fun endsWith(other: Path): Boolean = virtualPath.endsWith(other.toString())

  override fun endsWith(other: String): Boolean = virtualPath.endsWith(other)

  override fun normalize(): Path = VirtualSymlinkPath(fileSystem, virtualPath.normalize())

  override fun resolve(other: Path): Path = VirtualSymlinkPath(fileSystem, virtualPath.resolve(other.toString()))

  override fun resolve(other: String): Path = VirtualSymlinkPath(fileSystem, virtualPath.resolve(other))

  override fun resolveSibling(other: Path): Path = VirtualSymlinkPath(fileSystem, virtualPath.resolveSibling(other.toString()))

  override fun resolveSibling(other: String): Path = VirtualSymlinkPath(fileSystem, virtualPath.resolveSibling(other))

  override fun relativize(other: Path): Path {
    val other1 = virtualPath.fileSystem.getPath(other.toString())
    return runCatching { virtualPath.relativize(other1) }
      .getOrElse { throw IOException("Failed relativize: $virtualPath(${virtualPath.javaClass}) . $other1(${other1.javaClass})", it) }
  }

  override fun toUri(): URI = virtualPath.toUri()

  override fun toAbsolutePath(): Path {
    if (isAbsolute) return this
    return VirtualSymlinkPath(fileSystem, virtualPath.toAbsolutePath())
  }

  override fun toRealPath(vararg options: LinkOption): Path {
    val provider = fileSystem.provider()
    val strPath = virtualPath.toString()

    return when {
      strPath == this.fileSystem.basePath -> this
      strPath.startsWith(this.fileSystem.basePathSlash) -> {
        val rest = strPath.substring(this.fileSystem.basePathSlash.length)
        when {
          rest == SWITCH_SELF_MARKER_NAME || rest.startsWith("$SWITCH_SELF_MARKER_NAME/") || rest.startsWith("$SWITCH_ROOT_MARKER_NAME/") ->
            provider.unwrapPhysical(this).toRealPath(*options)
          else -> {
            val unmaskedPhysical = provider.unwrapPhysical(this)
            val realPhysicalTarget = unmaskedPhysical.toRealPath(*options)
            val realDestBase = fileSystem.dest.toRealPath(*options)
            val relativeSubpath =
              if (realPhysicalTarget.startsWith(realDestBase)) {
                realDestBase.relativize(realPhysicalTarget)
              } else {
                return this
              }
            val realSource = fileSystem.source.toRealPath(*options)
            createVirtualSymlinkPath(fileSystem, fileSystem.getPath(fileSystem.source.toString() + "/" + relativeSubpath.toString()))
          }
        }
      }
      else -> this
    }
  }

  override fun toFile(): File = File(virtualPath.toString())

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>, vararg modifiers: WatchEvent.Modifier?): WatchKey =
    throw UnsupportedOperationException()

  override fun register(watcher: WatchService, vararg events: WatchEvent.Kind<*>): WatchKey = throw UnsupportedOperationException()

  override fun compareTo(other: Path): Int {
    if (other !is VirtualSymlinkPath || virtualPath.fileSystem != other.virtualPath.fileSystem) error("$this and $other cannot be compared")
    return virtualPath.compareTo(other.virtualPath)
  }

  override fun toString(): String = virtualPath.toString()

  override fun hashCode(): Int = virtualPath.hashCode()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Path) return false
    return virtualPath.toString() == other.toString()
  }
}

class VirtualSymlinkFileSystemProvider(private val fileSystem: VirtualSymlinkFileSystem) : FileSystemProvider() {

  private val delegate: FileSystemProvider
    get() = fileSystem.delegate.provider()

  internal fun unwrapPhysical(path: Path): Path {
    val path = path as VirtualSymlinkPath
    val strPath = path.toString()
    return when {
      strPath == fileSystem.basePath -> fileSystem.source
      strPath.startsWith(fileSystem.basePathSlash) -> {
        val rest = strPath.substring(fileSystem.basePathSlash.length)
        when {
          rest == SWITCH_SELF_MARKER_NAME -> fileSystem.delegate.getPath(fileSystem.source.toString())
          rest.startsWith("$SWITCH_SELF_MARKER_NAME/") ->
            fileSystem.delegate.getPath(fileSystem.source.toString(), rest.substring("$SWITCH_SELF_MARKER_NAME/".length))
          rest.startsWith("$SWITCH_ROOT_MARKER_NAME/") ->
            fileSystem.delegate.getPath("/", rest.substring("$SWITCH_ROOT_MARKER_NAME/".length))
          else -> fileSystem.delegate.getPath(fileSystem.dest.toString(), rest)
        }
      }
      else -> path.virtualPath
    }
  }

  override fun getScheme(): String = "virtual-symlink"

  override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
    throw UnsupportedOperationException()
  }

  override fun getFileSystem(uri: URI): FileSystem = fileSystem

  override fun getPath(uri: URI): Path = createVirtualSymlinkPath(fileSystem, delegate.getPath(uri))

  override fun newByteChannel(path: Path, options: Set<OpenOption>, vararg attrs: FileAttribute<*>): SeekableByteChannel {
    return delegate.newByteChannel(unwrapPhysical(path), options, *attrs)
  }

  override fun newInputStream(path: Path, vararg options: OpenOption): InputStream {
    return delegate.newInputStream(unwrapPhysical(path), *options)
  }

  override fun newOutputStream(path: Path, vararg options: OpenOption): OutputStream {
    return delegate.newOutputStream(unwrapPhysical(path), *options)
  }

  override fun newFileChannel(path: Path, options: Set<OpenOption>, vararg attrs: FileAttribute<*>): FileChannel {
    return delegate.newFileChannel(unwrapPhysical(path), options, *attrs)
  }

  override fun newAsynchronousFileChannel(
    path: Path,
    options: Set<OpenOption>,
    executor: java.util.concurrent.ExecutorService?,
    vararg attrs: FileAttribute<*>,
  ): java.nio.channels.AsynchronousFileChannel {
    return delegate.newAsynchronousFileChannel(unwrapPhysical(path), options, executor, *attrs)
  }

  override fun createSymbolicLink(link: Path, target: Path, vararg attrs: FileAttribute<*>) {
    delegate.createSymbolicLink(unwrapPhysical(link), unwrapPhysical(target), *attrs)
  }

  override fun createLink(link: Path, existing: Path) {
    delegate.createLink(unwrapPhysical(link), unwrapPhysical(existing))
  }

  override fun deleteIfExists(path: Path): Boolean {
    return delegate.deleteIfExists(unwrapPhysical(path))
  }

  override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>): DirectoryStream<Path> {
    val physicalStream =
      delegate.newDirectoryStream(unwrapPhysical(dir)) { entry ->
        val wrappedEntry = createVirtualSymlinkPath(fileSystem, entry)
        filter.accept(wrappedEntry)
      }
    return object : DirectoryStream<Path> {
      override fun iterator(): MutableIterator<Path> {
        val physicalIterator = physicalStream.iterator()
        return object : MutableIterator<Path> {
          override fun hasNext(): Boolean = physicalIterator.hasNext()

          override fun next(): Path = createVirtualSymlinkPath(fileSystem, physicalIterator.next())

          override fun remove() = physicalIterator.remove()
        }
      }

      override fun close() = physicalStream.close()
    }
  }

  override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) {
    delegate.createDirectory(unwrapPhysical(dir), *attrs)
  }

  override fun delete(path: Path) {
    delegate.delete(unwrapPhysical(path))
  }

  override fun copy(source: Path, target: Path, vararg options: CopyOption) {
    delegate.copy(unwrapPhysical(source), unwrapPhysical(target), *options)
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption) {
    delegate.move(unwrapPhysical(source), unwrapPhysical(target), *options)
  }

  override fun isSameFile(path: Path, path2: Path): Boolean {
    return delegate.isSameFile(unwrapPhysical(path), unwrapPhysical(path2))
  }

  override fun isHidden(path: Path): Boolean {
    return delegate.isHidden(unwrapPhysical(path))
  }

  override fun getFileStore(path: Path): FileStore {
    return delegate.getFileStore(unwrapPhysical(path))
  }

  override fun checkAccess(path: Path, vararg modes: AccessMode) {
    delegate.checkAccess(unwrapPhysical(path), *modes)
  }

  override fun <V : FileAttributeView> getFileAttributeView(path: Path, type: Class<V>, vararg options: LinkOption): V? {
    return delegate.getFileAttributeView(unwrapPhysical(path), type, *options)
  }

  override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A {
    val unwrapped = unwrapPhysical(path)
    val attrs = delegate.readAttributes(unwrapped, type, *options)
    @Suppress("UNCHECKED_CAST")
    return when (attrs) {
      is PosixFileAttributes -> VirtualSymlinkPosixFileAttributes(attrs, isSwitcherPath = path.isSwitchPath()) as A
      is BasicFileAttributes -> VirtualSymlinkBasicFileAttributes(attrs, isSwitcherPath = path.isSwitchPath()) as A
      else -> attrs
    }
  }

  override fun readAttributes(path: Path, attributes: String, vararg options: LinkOption): Map<String, Any> {
    val map = delegate.readAttributes(unwrapPhysical(path), attributes, *options).toMutableMap()
    if (path.isSwitchPath()) {
      map["isSymbolicLink"] = false
      map["isDirectory"] = true
    }
    return map
  }

  private fun Path.isSwitchPath(): Boolean =
    (this as? VirtualSymlinkPath)?.virtualPath == this@VirtualSymlinkFileSystemProvider.fileSystem.source

  override fun setAttribute(path: Path, attribute: String, value: Any, vararg options: LinkOption) {
    delegate.setAttribute(unwrapPhysical(path), attribute, value, *options)
  }

  override fun readSymbolicLink(link: Path): Path {
    val physicalLink = delegate.readSymbolicLink(unwrapPhysical(link))
    return createVirtualSymlinkPath(fileSystem, physicalLink)
  }
}

private fun virtualizePath(fileSystem: VirtualSymlinkFileSystem, path: Path): Path {
  if (!path.isAbsolute) return path
  val destStr = fileSystem.dest.toString()
  val pathStr = path.toString()
  return if (pathStr.startsWith(destStr)) {
    val rest = pathStr.substring(destStr.length).trimStart('/')
    fileSystem.source.resolve(rest)
  } else {
    path
  }
}

private fun createVirtualSymlinkPath(fileSystem: VirtualSymlinkFileSystem, path: Path): Path {
  if (path is VirtualSymlinkPath) {
    return path
  }
  val normalized = path.normalize()
  val virtualized = virtualizePath(fileSystem, normalized)
  return VirtualSymlinkPath(fileSystem, virtualized)
}
