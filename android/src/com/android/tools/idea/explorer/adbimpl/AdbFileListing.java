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
package com.android.tools.idea.explorer.adbimpl;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.idea.concurrent.FutureCallbackExecutor;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.android.ddmlib.FileListingService.LS_LD_PATTERN;
import static com.android.ddmlib.FileListingService.LS_L_PATTERN;

public class AdbFileListing {
  @NotNull public static final Logger LOGGER = Logger.getInstance(AdbFileListing.class);
  @NotNull private static final Pattern BACKSLASH = Pattern.compile("\\", Pattern.LITERAL);

  @NotNull private final IDevice myDevice;
  @NotNull private AdbDeviceCapabilities myDeviceCapabilities;
  @NotNull private final FutureCallbackExecutor myExecutor;
  @NotNull private final AdbFileListingEntry myRoot;

  public AdbFileListing(@NotNull IDevice device, @NotNull AdbDeviceCapabilities deviceCapabilities, @NotNull Executor taskExecutor) {
    myDevice = device;
    myDeviceCapabilities = deviceCapabilities;
    myExecutor = FutureCallbackExecutor.wrap(taskExecutor);
    myRoot = new AdbFileListingEntryBuilder().setPath("/").setKind(AdbFileListingEntry.EntryKind.DIRECTORY).build();
  }

  @NotNull
  public ListenableFuture<AdbFileListingEntry> getRoot() {
    return Futures.immediateFuture(myRoot);
  }

  @NotNull
  public ListenableFuture<List<AdbFileListingEntry>> getChildren(@NotNull AdbFileListingEntry parentEntry) {
    return getChildrenRunAs(parentEntry, null);
  }

  @NotNull
  public ListenableFuture<List<AdbFileListingEntry>> getChildrenRunAs(@NotNull AdbFileListingEntry parentEntry,
                                                                      @Nullable String runAs) {
    return myExecutor.executeAsync(() -> {
      // Run "ls -l" command and process matching output lines
      String command = getCommand(runAs, "ls -l ").withDirectoryEscapedPath(parentEntry.getFullPath()).build(); //$NON-NLS-1$

      AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
      boolean escaping = myDeviceCapabilities.hasEscapingLs();

      List<AdbFileListingEntry> entries = commandResult.getOutput()
        .stream()
        .map(line -> processLsOutputLine(line, escaping, parentEntry))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
      if (entries.isEmpty() && commandResult.isError()) {
        commandResult.throwIfError();
      }
      return entries;
    });
  }

  /**
   * Determine if a symlink entry points to a directory. This is a best effort process,
   * as the target of the symlink might not be accessible, in which case the future value
   * is {@code false}. The future may still complete with an exception in case of ADB
   * specific errors, such as device disconnected, etc.
   */
  @NotNull
  public ListenableFuture<Boolean> isDirectoryLink(@NotNull AdbFileListingEntry entry) {
    return isDirectoryLinkRunAs(entry, null);
  }

  @NotNull
  public ListenableFuture<Boolean> isDirectoryLinkRunAs(@NotNull AdbFileListingEntry entry,
                                                        @Nullable String runAs) {
    if (!entry.isSymbolicLink()) {
      return Futures.immediateFuture(false);
    }

    return myExecutor.executeAsync(() -> {
      // We simply need to determine whether the referent is a directory or not.
      // We do this by running `ls -ld ${link}/`.  If the referent exists and is a
      // directory, we'll see the normal directory listing.  Otherwise, we'll see an
      // error of some sort.
      String command = getCommand(runAs, "ls -l -d ").withDirectoryEscapedPath(entry.getFullPath()).build();
      AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommandNoErrorCheck(myDevice, command);

      // Look for at least one line matching the expected output
      int lineCount = 0;
      for (String line : commandResult.getOutput()) {
        Matcher m = LS_LD_PATTERN.matcher(line);
        if (m.matches()) {
          if (lineCount > 0) {
            // It is odd to have more than one line matching "ls -l -d"
            LOGGER.warn(String.format("Unexpected additional output line matching result of ld -l -d: %s", line));
          }
          lineCount++;
        }
      }

      // All done
      return lineCount > 0;
    });
  }

  @Nullable
  private static AdbFileListingEntry processLsOutputLine(@NotNull String line, boolean escaping, @NotNull AdbFileListingEntry parentEntry) {
    // no need to handle empty lines.
    if (line.isEmpty()) {
      return null;
    }

    // run the line through the regexp
    Matcher m = LS_L_PATTERN.matcher(line);
    if (!m.matches()) {
      return null;
    }

    String name = getName(m, escaping);

    // get the rest of the groups
    String permissions = m.group(1);
    String owner = m.group(2);
    String group = m.group(3);
    String size = m.group(4);
    String date = m.group(5);
    String time = m.group(6);
    String info = null;

    // and the type
    AdbFileListingEntry.EntryKind objectType = AdbFileListingEntry.EntryKind.OTHER;
    switch (permissions.charAt(0)) {
      case '-':
        objectType = AdbFileListingEntry.EntryKind.FILE;
        break;
      case 'b':
        objectType = AdbFileListingEntry.EntryKind.BLOCK;
        break;
      case 'c':
        objectType = AdbFileListingEntry.EntryKind.CHARACTER;
        break;
      case 'd':
        objectType = AdbFileListingEntry.EntryKind.DIRECTORY;
        break;
      case 'l':
        objectType = AdbFileListingEntry.EntryKind.SYMBOLIC_LINK;
        break;
      case 's':
        objectType = AdbFileListingEntry.EntryKind.SOCKET;
        break;
      case 'p':
        objectType = AdbFileListingEntry.EntryKind.FIFO;
        break;
    }

    // now check what we may be linking to
    if (objectType == AdbFileListingEntry.EntryKind.SYMBOLIC_LINK) {
      String[] segments = name.split("\\s->\\s"); //$NON-NLS-1$

      // we should have 2 segments
      if (segments.length == 2) {
        // update the entry name to not contain the link
        name = segments[0];

        // and the link name
        info = segments[1];
      }

      // add an arrow in front to specify it's a link.
      info = "-> " + info; //$NON-NLS-1$;
    }

    String path = AdbPathUtil.resolve(parentEntry.getFullPath(), name);

    // Create entry and add it to result
    return new AdbFileListingEntry(path,
                                   objectType,
                                   permissions,
                                   owner,
                                   group,
                                   date,
                                   time,
                                   size,
                                   info);
  }

  @NotNull
  private static String getName(@NotNull MatchResult result, boolean escaping) {
    String name = result.group(7);
    return escaping ? BACKSLASH.matcher(name).replaceAll("") : name;
  }

  @NotNull
  private AdbShellCommandBuilder getCommand(@Nullable String runAs, @NotNull String text)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    AdbShellCommandBuilder command = new AdbShellCommandBuilder();
    if (runAs != null) {
      command.withRunAs(runAs);
    }
    else if (myDeviceCapabilities.supportsSuRootCommand()) {
      command.withSuRootPrefix();
    }
    return command.withText(text);
  }
}