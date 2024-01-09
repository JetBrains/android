/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.adbimpl

import com.android.adblib.ConnectedDevice
import com.android.ddmlib.FileListingService
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.adb.AdbShellCommandsUtil
import com.android.tools.idea.device.explorer.files.adbimpl.AdbFileListingEntry.EntryKind
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.regex.MatchResult

class AdbFileListing(
  myDevice: ConnectedDevice,
  private val myDeviceCapabilities: AdbDeviceCapabilities,
  private val dispatcher: CoroutineDispatcher) {
  private val LOGGER = thisLogger()
  private val myShellCommandsUtil = AdbShellCommandsUtil.create(myDevice)

  suspend fun getChildren(parentEntry: AdbFileListingEntry): List<AdbFileListingEntry> {
    return getChildrenRunAs(parentEntry, null)
  }

  suspend fun getChildrenRunAs(
    parentEntry: AdbFileListingEntry,
    runAs: String?
  ): List<AdbFileListingEntry> {
    return withContext(dispatcher) {
      // Run "ls -al" command and process matching output lines
      val command = getCommand(runAs, "ls -al ").withDirectoryEscapedPath(parentEntry.fullPath).build() //$NON-NLS-1$
      val commandResult = myShellCommandsUtil.executeCommand(command)
      val escaping = myDeviceCapabilities.hasEscapingLs()
      val entries = commandResult.output
        .mapNotNull { line -> processLsOutputLine(line, escaping, parentEntry) }
      if (entries.isEmpty() && commandResult.isError) {
        commandResult.throwIfError()
      }
      entries
    }
  }

  suspend fun getRoot(): AdbFileListingEntry {
    return withContext(dispatcher) {
      try {
        val command = getCommand(null, "stat -c \"%A %U %G %z %s %n\" ").withDirectoryEscapedPath("/").build() //$NON-NLS-1$
        val commandResult = myShellCommandsUtil.executeCommand(command)
        if (commandResult.output.isEmpty() || commandResult.isError) {
          commandResult.throwIfError()
        }
        processStatOutputLine(commandResult.output[0], defaultRoot)
      } catch (e: AdbShellCommandException) {
        LOGGER.debug(e.message)
        defaultRoot
      }
    }
  }

  suspend fun getDataDirectory(): AdbFileListingEntry {
    return withContext(dispatcher) {
      try {
        val command = getCommand(null, "stat -c \"%A %U %G %z %s %n\" ").withDirectoryEscapedPath("/data/data").build() //$NON-NLS-1$
        val commandResult = myShellCommandsUtil.executeCommand(command)
        if (commandResult.output.isEmpty() || commandResult.isError) {
          commandResult.throwIfError()
        }
        processStatOutputLine(commandResult.output[0], defaultData)
      } catch (e: AdbShellCommandException) {
        LOGGER.debug(e.message)
        defaultData
      }
    }
  }

  /**
   * Determine if a symlink entry points to a directory. This is a best effort process,
   * as the target of the symlink might not be accessible, in which case the return value
   * is `false`.
   *
   * May throw an exception in case of ADB specific errors, such as device disconnected, etc.
   */
  suspend fun isDirectoryLink(entry: AdbFileListingEntry): Boolean {
    return isDirectoryLinkRunAs(entry, null)
  }

  suspend fun isDirectoryLinkRunAs(
    entry: AdbFileListingEntry,
    runAs: String?
  ): Boolean {
    return if (!entry.isSymbolicLink) {
      false
    } else withContext(dispatcher) {

      // We simply need to determine whether the referent is a directory or not.
      // We do this by running `ls -ld ${link}/`.  If the referent exists and is a
      // directory, we'll see the normal directory listing.  Otherwise, we'll see an
      // error of some sort.
      val command = getCommand(runAs, "ls -l -d ").withDirectoryEscapedPath(entry.fullPath).build()
      val commandResult = myShellCommandsUtil.executeCommandNoErrorCheck(command)

      // Look for at least one line matching the expected output
      var lineCount = 0
      for (line in commandResult.output) {
        val m = FileListingService.LS_LD_PATTERN.matcher(line)
        if (m.matches()) {
          if (lineCount > 0) {
            // It is odd to have more than one line matching "ls -l -d"
            LOGGER.warn("Unexpected additional output line matching result of ld -l -d: $line")
          }
          lineCount++
        }
      }
      lineCount > 0
    }
  }

  private suspend fun getCommand(runAs: String?, text: String): AdbShellCommandBuilder {
    val command = AdbShellCommandBuilder()
    if (runAs != null) {
      command.withRunAs(runAs)
    } else if (myDeviceCapabilities.supportsSuRootCommand()) {
      command.withSuRootPrefix()
    }
    return command.withText(text)
  }

  private fun processStatOutputLine(line: String, defaultFileEntry: AdbFileListingEntry): AdbFileListingEntry {
    if (line.isEmpty()) {
      LOGGER.warn("Stat command return an unexpected empty line, using default root instead")
      return defaultFileEntry
    }

    val m = FileListingService.STAT_PATTERN.matcher(line)
    if (!m.matches()) {
      LOGGER.warn("Stat command return an unexpected string pattern, using default root instead")
      return defaultFileEntry
    }

    val permissions = m.group(1)
    val owner = m.group(2)
    val group = m.group(3)
    val date = m.group(4)
    val time = if (m.group(5).length > 5) m.group(5).substring(0,5) else m.group(5)
    val size = m.group(7)
    val name = m.group(8)

    return AdbFileListingEntry(
      name,
      getObjectType(permissions),
      permissions,
      owner,
      group,
      date,
      time,
      size,
      null
    )
  }
  companion object {
    val defaultRoot: AdbFileListingEntry = AdbFileListingEntryBuilder().setPath("/").setKind(EntryKind.DIRECTORY).build()
    private val defaultData: AdbFileListingEntry = AdbFileListingEntryBuilder().setPath("/data/data/").setKind(EntryKind.DIRECTORY).build()
  }
}

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
  val objectType = getObjectType(permissions)

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

private fun getObjectType(permissions: String): EntryKind {
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
  return objectType
}

private fun getName(result: MatchResult, escaping: Boolean): String {
  val name = result.group(7)
  return if (escaping) name.replace("\\", "") else name
}
