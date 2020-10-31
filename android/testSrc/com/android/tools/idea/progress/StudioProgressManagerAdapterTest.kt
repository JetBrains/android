/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.progress

import com.android.io.CancellableFileIo
import com.android.testutils.file.DelegatingFileSystemProvider
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.ProgressRunner
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test for [StudioProgressManagerAdapter] and [CancellableFileIo].
 */
class StudioProgressManagerAdapterTest {
  @get:Rule
  val rule = ApplicationRule()

  private val fileSystemProvider = MockFileSystemProvider(Jimfs.newFileSystem(Configuration.unix()))
  private val fileSystem = fileSystemProvider.fileSystem

  /** Checks cancellation of I/O operations by a pending write action. */
  @Test
  fun testIoCancellation() {
    val ioOperationCount = AtomicInteger()
    val readActionStarted = CountDownLatch(1)

    // Start a read action on a background thread.
    ApplicationManager.getApplication().executeOnPooledThread {
      ProgressIndicatorUtils.runInReadActionWithWriteActionPriority {
        readActionStarted.countDown()
        for (i in 1..100) {
          val file = fileSystem.getPath("/dir/file$i")
          CancellableFileIo.isRegularFile(file)
          ioOperationCount.set(i)
        }
      }
    }

    // Wait until read action starts.
    readActionStarted.await()

    val writeActionCompleted = CountDownLatch(1)
    // Start a write action asynchronously.
    ProgressRunner<Unit, ProgressIndicator>(writeActionCompleted::countDown).submit()

    // Allow one I/O operation every 100 milliseconds.
    for (i in 1..100) {
      writeActionCompleted.await(100, TimeUnit.MILLISECONDS)
      fileSystemProvider.proceed()
    }

    // The read action should be cancelled after the first I/O operation.
    assertThat(ioOperationCount.get()).isAtMost(1)
  }

  private class MockFileSystemProvider(fileSystem: FileSystem) : DelegatingFileSystemProvider(fileSystem) {
    private val semaphore = Semaphore(Integer.MAX_VALUE).apply { drainPermits() }

    /** Allows the given number of I/O operations to be executed. */
    fun proceed(numberOfOperations: Int = 1) {
      semaphore.release(numberOfOperations)
    }

    override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A {
      semaphore.acquire()
      return super.readAttributes(path, type, *options)
    }
  }
}