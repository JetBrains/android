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

import com.google.idea.blaze.base.BlazeIntegrationTestCase
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class WorkspaceFileWatcherTest : BlazeIntegrationTestCase() {

  private lateinit var fakeProvider: FakeWorkspaceWatcherProvider
  private lateinit var testSink: FileWatcherTestSink
  private lateinit var fileWatcher: WorkspaceFileWatcher
  private lateinit var tempDir: Path

  @Before
  fun init() {
    tempDir = Files.createTempDirectory("watcher-test").toRealPath()

    fakeProvider = FakeWorkspaceWatcherProvider(tempDir)
    registerExtension(WorkspaceWatcherProvider.EP_NAME, fakeProvider)

    testSink = FileWatcherTestSink()
    fileWatcher = WorkspaceFileWatcher()
    fileWatcher.initialize(testSink)
  }

  @After
  fun tearDownTemp() {
    fileWatcher.dispose()

    // Clean up temp directories recursively
    if (Files.exists(tempDir)) {
      Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
  }

  @Test
  fun testPartitioning_ignoresPhysicalPathsAndRoutesVirtualPaths() {
    val mappingManager = WorkspaceMappingManager.getInstance()
    val physicalRoot = tempDir.resolve("physical_ws1")
    Files.createDirectories(physicalRoot)

    // Mount virtual path: /ws/my-workspace -> /physical_ws1
    val virtualRoot = mappingManager.setWorkspaceTarget("my-workspace", physicalRoot)
    val virtualGoogle3 = virtualRoot.resolve("google3")

    // Define watch roots: 1 virtual, 1 completely unrelated physical
    val physicalUnrelated = tempDir.resolve("unrelated/path")
    Files.createDirectories(physicalUnrelated)

    fileWatcher.setWatchRoots(
      recursiveCanonicalPaths = listOf(virtualGoogle3.toString(), physicalUnrelated.toString()),
      flatCanonicalPaths = emptyList(),
      shuttingDown = false,
    )

    // Verify it ignored the physical unrelated path and created a watcher for the physical target
    val createdWatchers = fakeProvider.createdWatchers
    assertEquals(1, createdWatchers.size)

    val activeWatcher = createdWatchers.first()
    assertEquals(physicalRoot, activeWatcher.physicalRoot)
    assertEquals(1, activeWatcher.recursiveRoots.size)
    assertEquals(physicalRoot.resolve("google3"), activeWatcher.recursiveRoots.first())
  }

  @Test
  fun testWatcherLifecycle_startupAndShutdown() {
    val mappingManager = WorkspaceMappingManager.getInstance()
    val physicalRoot = tempDir.resolve("physical_ws1")
    Files.createDirectories(physicalRoot)

    val virtualRoot = mappingManager.setWorkspaceTarget("my-workspace", physicalRoot)
    val virtualGoogle3 = virtualRoot.resolve("google3")

    fileWatcher.setWatchRoots(
      recursiveCanonicalPaths = listOf(virtualGoogle3.toString()),
      flatCanonicalPaths = emptyList(),
      shuttingDown = false,
    )

    val activeWatcher = fakeProvider.createdWatchers.first()
    assertFalse(activeWatcher.isShutDown)

    fileWatcher.shutdown()
    assertTrue(activeWatcher.isShutDown)
  }

  @Test
  fun testTargetSwitch_triggersDiffAndHotSwapsWatchers() = runBlocking {
    val mappingManager = WorkspaceMappingManager.getInstance()

    val physicalRoot1 = tempDir.resolve("physical_ws1")
    val physicalRoot2 = tempDir.resolve("physical_ws2")
    Files.createDirectories(physicalRoot1.resolve("google3"))
    Files.createDirectories(physicalRoot2.resolve("google3"))

    val virtualRoot = mappingManager.setWorkspaceTarget("my-workspace", physicalRoot1)
    val virtualGoogle3 = virtualRoot.resolve("google3")

    fileWatcher.setWatchRoots(
      recursiveCanonicalPaths = listOf(virtualGoogle3.toString()),
      flatCanonicalPaths = emptyList(),
      shuttingDown = false,
    )

    val watcher1 = fakeProvider.createdWatchers.first()
    assertFalse(watcher1.isShutDown)

    // Configure provider to return a modified file during diffing
    fakeProvider.nextDiffResults = listOf(physicalRoot2.resolve("google3/ModifiedFile.kt"))

    // Simulate target switch (emits MappingChangeEvent)
    mappingManager.setWorkspaceTarget("my-workspace", physicalRoot2)

    // Yield to allow coroutines to handle the mapping change event
    delay(500)

    // Verify:
    // 1. Old watcher is shutdown
    assertTrue(watcher1.isShutDown)

    // 2. Diff operation was queried
    assertEquals(1, fakeProvider.diffOperationsCreated)

    // 3. New watcher was started for physicalRoot2
    assertEquals(2, fakeProvider.createdWatchers.size)
    val watcher2 = fakeProvider.createdWatchers[1]
    assertEquals(physicalRoot2, watcher2.physicalRoot)
    assertFalse(watcher2.isShutDown)
    assertEquals(physicalRoot2.resolve("google3"), watcher2.recursiveRoots.first())

    // 4. Optimized diff results are streamed to the sink (and translated to virtual paths!)
    // We expect "ModifiedFile.kt" to be marked dirty under the VFS virtual root.
    val expectedVirtualFile = virtualGoogle3.resolve("ModifiedFile.kt")
    val expectedLog = "dirty: $expectedVirtualFile"
    assertTrue("Expected log containing translated dirty path. Found:\n$testSink", testSink.toString().contains(expectedLog))
  }

  @Test
  fun testTargetSwitch_fallbackInvalidatesAllWatchRootsOnFail() = runBlocking {
    val mappingManager = WorkspaceMappingManager.getInstance()

    val physicalRoot1 = tempDir.resolve("physical_ws1")
    val physicalRoot2 = tempDir.resolve("physical_ws2")
    Files.createDirectories(physicalRoot1.resolve("google3"))
    Files.createDirectories(physicalRoot2.resolve("google3"))

    val virtualRoot = mappingManager.setWorkspaceTarget("my-workspace", physicalRoot1)
    val virtualGoogle3 = virtualRoot.resolve("google3")

    fileWatcher.setWatchRoots(
      recursiveCanonicalPaths = listOf(virtualGoogle3.toString()),
      flatCanonicalPaths = emptyList(),
      shuttingDown = false,
    )

    // Configure provider to simulate metadata read / diff failure
    fakeProvider.shouldFailDiff = true

    // Simulate target switch
    mappingManager.setWorkspaceTarget("my-workspace", physicalRoot2)

    delay(500)

    // Verify new watcher was created
    assertEquals(2, fakeProvider.createdWatchers.size)
    val watcher2 = fakeProvider.createdWatchers[1]
    assertEquals(physicalRoot2, watcher2.physicalRoot)

    // Verify fallback path: the entire watched root (virtualGoogle3) is recursively marked dirty
    val expectedLog = "dirty_recursive: $virtualGoogle3"
    assertTrue("Expected fallback full recursive invalidation. Found:\n$testSink", testSink.toString().contains(expectedLog))
  }

  @Test
  fun testPathTranslation_translatesPhysicalEventsToVirtualPaths() {
    val mappingManager = WorkspaceMappingManager.getInstance()
    val physicalRoot = tempDir.resolve("physical_ws1")
    Files.createDirectories(physicalRoot)

    val virtualRoot = mappingManager.setWorkspaceTarget("my-workspace", physicalRoot)
    val virtualGoogle3 = virtualRoot.resolve("google3")

    fileWatcher.setWatchRoots(
      recursiveCanonicalPaths = listOf(virtualGoogle3.toString()),
      flatCanonicalPaths = emptyList(),
      shuttingDown = false,
    )

    val watcher = fakeProvider.createdWatchers.first()

    // Fire physical file event from the underlying watcher
    val physicalFile = physicalRoot.resolve("google3/Foo.kt")
    watcher.fireFileChanged(physicalFile)

    // Verify VFS sink received the translated virtual path
    val expectedVirtualFile = virtualGoogle3.resolve("Foo.kt")
    val expectedLog = "dirty: $expectedVirtualFile"
    assertTrue("Expected translated log. Found:\n$testSink", testSink.toString().contains(expectedLog))
  }

  @Test
  fun testPathTranslation_dropsEventsOutsideWorkspaceRoot() {
    val mappingManager = WorkspaceMappingManager.getInstance()
    val physicalRoot = tempDir.resolve("physical_ws1")
    Files.createDirectories(physicalRoot)

    val virtualRoot = mappingManager.setWorkspaceTarget("my-workspace", physicalRoot)
    val virtualGoogle3 = virtualRoot.resolve("google3")

    fileWatcher.setWatchRoots(
      recursiveCanonicalPaths = listOf(virtualGoogle3.toString()),
      flatCanonicalPaths = emptyList(),
      shuttingDown = false,
    )

    val watcher = fakeProvider.createdWatchers.first()

    // Fire physical file event completely OUTSIDE the physical workspace root
    val externalFile = tempDir.resolve("some_unrelated_dir/Foo.kt")
    watcher.fireFileChanged(externalFile)

    // The sink should remain completely empty because it's not under physicalRoot
    val expectedVirtualFile = virtualRoot.resolve("some_unrelated_dir/Foo.kt")
    val expectedLog = "dirty: $expectedVirtualFile"
    assertFalse(testSink.toString().contains(expectedLog))
    assertTrue(testSink.toString().isEmpty())
  }

  @Test
  fun testUnmountedRoots_areGracefullyIgnored() {
    val mappingManager = WorkspaceMappingManager.getInstance()

    // No mapping set!
    val virtualRoot = mappingManager.switchesRoot.resolve("unmapped-workspace")
    val virtualGoogle3 = virtualRoot.resolve("google3")

    fileWatcher.setWatchRoots(
      recursiveCanonicalPaths = listOf(virtualGoogle3.toString()),
      flatCanonicalPaths = emptyList(),
      shuttingDown = false,
    )

    // It should not crash, and no watcher should be created
    assertEquals(0, fakeProvider.createdWatchers.size)
  }

  @Test
  fun testManualWatchRoots_reportsUnrecognizedPaths() {
    val mappingManager = WorkspaceMappingManager.getInstance()

    // Mapped virtual root
    val physicalRoot = tempDir.resolve("physical_ws1")
    Files.createDirectories(physicalRoot)
    val virtualRoot1 = mappingManager.setWorkspaceTarget("my-workspace", physicalRoot)
    val virtualGoogle3 = virtualRoot1.resolve("google3")

    // Unmapped virtual root (starts with switchesRoot but not currently mapped)
    val virtualRoot2 = mappingManager.switchesRoot.resolve("unmapped-workspace")
    val unmappedGoogle3 = virtualRoot2.resolve("google3")

    // Completely external path
    val externalPath = tempDir.resolve("external/project/path")
    Files.createDirectories(externalPath)

    fileWatcher.setWatchRoots(
      recursiveCanonicalPaths = listOf(virtualGoogle3.toString(), unmappedGoogle3.toString(), externalPath.toString()),
      flatCanonicalPaths = emptyList(),
      shuttingDown = false,
    )

    // Verify:
    // 1. Mapped virtual path (virtualGoogle3) is NOT reported in manual watch roots.
    // 2. Unmapped virtual path (unmappedGoogle3) is NOT reported in manual watch roots.
    // 3. External path is reported as manual.
    val logs = testSink.toString()
    assertFalse("Mapped virtual path should not be reported", logs.contains("manual: $virtualGoogle3"))
    assertFalse("Unmapped virtual path should not be reported", logs.contains("manual: $unmappedGoogle3"))
    assertTrue("External path must be reported as manual watch root", logs.contains("manual: $externalPath"))
  }

  // --- Mocks and Fakes ---

  class FakeWorkspaceWatcherProvider(private val physicalPrefix: Path) : WorkspaceWatcherProvider {
    val createdWatchers = mutableListOf<FakeWorkspaceWatcher>()
    var nextDiffResults: List<Path>? = null
    var shouldFailDiff: Boolean = false
    var diffOperationsCreated = 0

    override fun createWatcher(physicalRoot: Path, sink: FileWatcherNotificationSink): WorkspaceWatcher? {
      if (!physicalRoot.startsWith(physicalPrefix)) return null
      val watcher = FakeWorkspaceWatcher(physicalRoot, sink)
      createdWatchers.add(watcher)
      return watcher
    }

    override fun createDiffOperation(oldPhysicalRoot: Path, newPhysicalRoot: Path): ((Set<Path>, FileWatcherNotificationSink) -> Unit)? {
      diffOperationsCreated++
      if (shouldFailDiff) {
        return { watchedPaths, sink ->
          watchedPaths.forEach { physicalPath ->
            if (physicalPath.startsWith(oldPhysicalRoot)) {
              val relative = oldPhysicalRoot.relativize(physicalPath)
              val newPhysicalPath = newPhysicalRoot.resolve(relative)
              sink.notifyDirtyPathRecursive(newPhysicalPath.toString())
            }
          }
        }
      }

      return { _, sink ->
        nextDiffResults?.forEach { relativePath ->
          val absolutePhysical = newPhysicalRoot.resolve(relativePath)
          sink.notifyPathCreatedOrDeleted(absolutePhysical.toString())
        }
      }
    }
  }

  class FakeWorkspaceWatcher(val physicalRoot: Path, val sink: FileWatcherNotificationSink) : WorkspaceWatcher {
    var isShutDown = false
    var recursiveRoots: List<Path> = emptyList()
    var flatRoots: List<Path> = emptyList()

    override fun isOperational(): Boolean = !isShutDown

    override fun setWatchRoots(recursivePaths: List<Path>, flatPaths: List<Path>) {
      this.recursiveRoots = recursivePaths
      this.flatRoots = flatPaths
    }

    override fun shutdown() {
      isShutDown = true
    }

    fun fireFileChanged(physicalPath: Path) {
      sink.notifyPathCreatedOrDeleted(physicalPath.toString())
    }
  }

  class FileWatcherTestSink : FileWatcherNotificationSink {
    private val log = StringBuilder()

    override fun notifyMapping(mapping: MutableCollection<out com.intellij.openapi.util.Pair<String, String>>) {
      mapping.forEach { log.append("mapping: ${it.first} -> ${it.second}\n") }
    }

    override fun notifyDirtyPath(path: String) {
      log.append("dirty: $path\n")
    }

    override fun notifyPathCreatedOrDeleted(path: String) {
      log.append("dirty: $path\n")
    }

    override fun notifyDirtyPathRecursive(path: String) {
      log.append("dirty_recursive: $path\n")
    }

    override fun notifyDirtyDirectory(path: String) {
      log.append("dirty_recursive: $path\n")
    }

    override fun notifyReset(path: String?) {
      log.append("reset: $path\n")
    }

    override fun notifyManualWatchRoots(watcher: com.intellij.openapi.vfs.local.PluggableFileWatcher, roots: Collection<String>) {
      roots.forEach { log.append("manual: $it\n") }
    }

    override fun notifyUserOnFailure(error: String, detail: com.intellij.notification.NotificationListener?) {
      log.append("failure: $error\n")
    }

    override fun toString(): String = log.toString()
  }
}
