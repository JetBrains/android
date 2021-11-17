/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.explorer.adbimpl

import com.android.ddmlib.IDevice
import com.android.tools.idea.explorer.adbimpl.AdbDeviceCapabilities
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.adbimpl.AdbFileListingEntry
import com.android.tools.idea.adb.AdbShellCommandsUtil
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.adb.AdbShellCommandResult
import com.android.tools.idea.explorer.adbimpl.AdbFileListing
import com.android.ddmlib.FileListingService
import kotlin.Throws
import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.TimeoutException
import java.io.IOException
import com.android.tools.idea.explorer.adbimpl.AdbShellCommandBuilder
import com.android.tools.idea.explorer.adbimpl.AdbFileListingEntry.EntryKind
import com.android.tools.idea.explorer.adbimpl.AdbPathUtil
import com.android.tools.idea.explorer.adbimpl.AdbFileListingEntryBuilder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.Logger
import java.util.Objects
import java.util.concurrent.Executor
import java.util.regex.MatchResult
import java.util.regex.Pattern
import java.util.stream.Collectors

class AdbFileListing(private val myDevice: IDevice, private val myDeviceCapabilities: AdbDeviceCapabilities, taskExecutor: Executor) {
  private val myExecutor: FutureCallbackExecutor
  private val myRoot: AdbFileListingEntry
  private val myShellCommandsUtil = AdbShellCommandsUtil(StudioFlags.ADBLIB_MIGRATION_DEVICE_EXPLORER.get())
  val root: ListenableFuture<AdbFileListingEntry>
    get() = Futures.immediateFuture(myRoot)

  fun getChildren(parentEntry: AdbFileListingEntry): ListenableFuture<List<AdbFileListingEntry?>> {
    return getChildrenRunAs(parentEntry, null)
  }

  fun getChildrenRunAs(
    parentEntry: AdbFileListingEntry,
    runAs: String?
  ): ListenableFuture<List<AdbFileListingEntry?>> {
    return myExecutor.executeAsync {

      // Run "ls -al" command and process matching output lines
      val command = getCommand(runAs, "ls -al ").withDirectoryEscapedPath(parentEntry.fullPath).build() //$NON-NLS-1$
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      val escaping = myDeviceCapabilities.hasEscapingLs()
      val entries = commandResult.output
        .stream()
        .map { line: String -> processLsOutputLine(line, escaping, parentEntry) }
        .filter { obj: AdbFileListingEntry? -> Objects.nonNull(obj) }
        .collect(Collectors.toList())
      if (entries.isEmpty() && commandResult.isError) {
        commandResult.throwIfError()
      }
      entries
    }
  }

  /**
   * Determine if a symlink entry points to a directory. This is a best effort process,
   * as the target of the symlink might not be accessible, in which case the future value
   * is `false`. The future may still complete with an exception in case of ADB
   * specific errors, such as device disconnected, etc.
   */
  fun isDirectoryLink(entry: AdbFileListingEntry): ListenableFuture<Boolean> {
    return isDirectoryLinkRunAs(entry, null)
  }

  fun isDirectoryLinkRunAs(
    entry: AdbFileListingEntry,
    runAs: String?
  ): ListenableFuture<Boolean> {
    return if (!entry.isSymbolicLink) {
      Futures.immediateFuture(false)
    } else myExecutor.executeAsync {

      // We simply need to determine whether the referent is a directory or not.
      // We do this by running `ls -ld ${link}/`.  If the referent exists and is a
      // directory, we'll see the normal directory listing.  Otherwise, we'll see an
      // error of some sort.
      val command = getCommand(runAs, "ls -l -d ").withDirectoryEscapedPath(entry.fullPath).build()
      val commandResult = myShellCommandsUtil.executeCommandNoErrorCheck(myDevice, command)

      // Look for at least one line matching the expected output
      var lineCount = 0
      for (line in commandResult.output) {
        val m = FileListingService.LS_LD_PATTERN.matcher(line)
        if (m.matches()) {
          if (lineCount > 0) {
            // It is odd to have more than one line matching "ls -l -d"
            LOGGER.warn(String.format("Unexpected additional output line matching result of ld -l -d: %s", line))
          }
          lineCount++
        }
      }
      lineCount > 0
    }
  }

  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  private fun getCommand(runAs: String?, text: String): AdbShellCommandBuilder {
    val command = AdbShellCommandBuilder()
    if (runAs != null) {
      command.withRunAs(runAs)
    } else if (myDeviceCapabilities.supportsSuRootCommand()) {
      command.withSuRootPrefix()
    }
    return command.withText(text)
  }

  companion object {
    val LOGGER = Logger.getInstance(AdbFileListing::class.java)
    private val BACKSLASH = Pattern.compile("\\", Pattern.LITERAL)
    private fun processLsOutputLine(line: String, escaping: Boolean, parentEntry: AdbFileListingEntry): AdbFileListingEntry? {
      // no need to handle empty lines.
      if (line.isEmpty()) {
        return null
      }

      // run the line through the regexp
      val m = FileListingService.LS_L_PATTERN.matcher(line)
      if (!m.matches()) {
        return null
      }
      var name = getName(m, escaping)
      if (name == "." || name == "..") {
        return null
      }

      // get the rest of the groups
      val permissions = m.group(1)
      val owner = m.group(2)
      val group = m.group(3)
      val size = m.group(4)
      val date = m.group(5)
      val time = m.group(6)
      var info: String? = null

      // and the type
      var objectType = EntryKind.OTHER
      when (permissions[0]) {
        '-' -> objectType = EntryKind.FILE
        'b' -> objectType = EntryKind.BLOCK
        'c' -> objectType = EntryKind.CHARACTER
        'd' -> objectType = EntryKind.DIRECTORY
        'l' -> objectType = EntryKind.SYMBOLIC_LINK
        's' -> objectType = EntryKind.SOCKET
        'p' -> objectType = EntryKind.FIFO
      }

      // now check what we may be linking to
      if (objectType == EntryKind.SYMBOLIC_LINK) {
        val segments = name.split("\\s->\\s".toRegex()).toTypedArray() //$NON-NLS-1$

        // we should have 2 segments
        if (segments.size == 2) {
          // update the entry name to not contain the link
          name = segments[0]

          // and the link name
          info = segments[1]
        }

        // add an arrow in front to specify it's a link.
        info = "-> $info" //$NON-NLS-1$;
      }
      val path = AdbPathUtil.resolve(parentEntry.fullPath, name)

      // Create entry and add it to result
      return AdbFileListingEntry(
        path,
        objectType,
        permissions,
        owner,
        group,
        date,
        time,
        size,
        info
      )
    }

    private fun getName(result: MatchResult, escaping: Boolean): String {
      val name = result.group(7)
      return if (escaping) BACKSLASH.matcher(name).replaceAll("") else name
    }
  }

  init {
    myExecutor = FutureCallbackExecutor.wrap(taskExecutor)
    myRoot = AdbFileListingEntryBuilder().setPath("/").setKind(EntryKind.DIRECTORY).build()
  }
}