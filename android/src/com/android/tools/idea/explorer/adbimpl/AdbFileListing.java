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

import com.android.ddmlib.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.android.ddmlib.FileListingService.*;

public class AdbFileListing {
  public static Logger LOGGER = Logger.getInstance(AdbFileListing.class);

  @NotNull private final IDevice myDevice;
  @NotNull private final Executor myExecutor;
  @NotNull private final AdbFileListingEntry myRoot;

  public AdbFileListing(@NotNull IDevice device, @NotNull Executor taskExecutor) {
    myDevice = device;
    myExecutor = taskExecutor;
    myRoot = new AdbFileListingEntry("/",
                                     AdbFileListingEntry.EntryKind.DIRECTORY,
                                     null,
                                     null,
                                     null,
                                     null,
                                     null,
                                     null,
                                     null);
  }

  @NotNull
  public ListenableFuture<AdbFileListingEntry> getRoot() {
    return Futures.immediateFuture(myRoot);
  }

  @NotNull
  public ListenableFuture<List<AdbFileListingEntry>> getChildren(@NotNull AdbFileListingEntry parentEntry) {
    SettableFuture<List<AdbFileListingEntry>> futureResult = SettableFuture.create();
    myExecutor.execute(() -> {
      try {
        long startTime = System.nanoTime();
        List<AdbFileListingEntry> result = listChildren(parentEntry);

        if (LOGGER.isTraceEnabled()) {
          long endTime = System.nanoTime();
          LOGGER.trace(String.format("Listing entries of \"%s\" took %,d ms",
                                     parentEntry.getFullPath(),
                                     (endTime - startTime) / 1_000_000));
        }
        futureResult.set(result);
      }
      catch (Throwable t) {
        futureResult.setException(t);
      }
    });
    return futureResult;
  }

  /**
   * Determine if a symlink entry points to a directory. This is a best effort process,
   * as the target of the symlink might not be accessible, in which case the future value
   * is {@code false}. The future may still complete with an exception in case of ADB
   * specific errors, such as device disconnected, etc.
   */
  @NotNull
  public ListenableFuture<Boolean> isDirectoryLink(@NotNull AdbFileListingEntry entry) {
    SettableFuture<Boolean> futureResult = SettableFuture.create();

    myExecutor.execute(() -> {
      try {
        final int[] nLines = {0};
        MultiLineReceiver receiver = new MultiLineReceiver() {
          @Override
          public void processNewLines(String[] lines) {
            for (String line : lines) {
              Matcher m = LS_LD_PATTERN.matcher(line);
              if (m.matches()) {
                nLines[0]++;
              }
            }
          }

          @Override
          public boolean isCancelled() {
            return false;
          }
        };


        // We simply need to determine whether the referent is a directory or not.
        // We do this by running `ls -ld ${link}/`.  If the referent exists and is a
        // directory, we'll see the normal directory listing.  Otherwise, we'll see an
        // error of some sort.
        final String command = String.format("ls -l -d %s%s", getEscapedPath(entry.getFullPath()), FILE_SEPARATOR);

        long startTime = System.nanoTime();

        myDevice.executeShellCommand(command, receiver);

        if (LOGGER.isTraceEnabled()) {
          long endTime = System.nanoTime();
          LOGGER.trace(String.format("isDirectoryLink for \"%s\" took %,d ms", entry.getFullPath(), (endTime - startTime) / 1_000_000));
        }

        futureResult.set(nLines[0] > 0);
      }
      catch (Throwable t) {
        futureResult.setException(t);
      }
    });

    return futureResult;
  }

  @NotNull
  private static String getEscapedPath(@NotNull String path) {
    // Special case for root
    if (FILE_SEPARATOR.equals(path)) {
      return path;
    }

    // Escape each segment, then re-join them by file separator
    return StringUtil.split(path, FILE_SEPARATOR)
      .stream()
      .map(x -> FILE_SEPARATOR + FileEntry.escape(x))
      .collect(Collectors.joining());
  }

  @NotNull
  private List<AdbFileListingEntry> listChildren(@NotNull AdbFileListingEntry entry)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    // create a list that will receive the list of the entries
    List<AdbFileListingEntry> entryList = new ArrayList<>();

    // create the command
    String command = "ls -l " + getEscapedPath(entry.getFullPath()); //$NON-NLS-1$
    // If we expect a file to behave like a directory, we should stick a "/" at the end.
    // This is a good habit, and is mandatory for symlinks-to-directories, which will
    // otherwise behave like symlinks.
    if (!command.endsWith(FILE_SEPARATOR)) {
      command += FILE_SEPARATOR;
    }

    // create the receiver object that will parse the result from ls
    LsReceiver receiver = new LsReceiver(entry, entryList);

    // call ls.
    myDevice.executeShellCommand(command, receiver);

    // sort the children by name
    entryList.sort((entry1, entry2) -> {
      if (entry1 != null && entry2 != null) {
        return entry1.getName().compareTo(entry2.getName());
      }
      return 0;
    });

    return entryList;
  }

  private static class LsReceiver extends MultiLineReceiver {
    @NotNull private AdbFileListingEntry myParentEntry;
    @NotNull private List<AdbFileListingEntry> myEntryList;

    public LsReceiver(@NotNull AdbFileListingEntry parentEntry, @NotNull List<AdbFileListingEntry> entryList) {
      myParentEntry = parentEntry;
      myEntryList = entryList;
    }

    @Override
    public void processNewLines(@NotNull String[] lines) {
      for (String line : lines) {
        // no need to handle empty lines.
        if (line.isEmpty()) {
          continue;
        }

        // run the line through the regexp
        Matcher m = LS_L_PATTERN.matcher(line);
        if (!m.matches()) {
          continue;
        }

        // get the name
        String name = m.group(7);

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

        String path = AdbPathUtil.resolve(myParentEntry.getFullPath(), name);

        // Create entry and add it to result
        AdbFileListingEntry entry = new AdbFileListingEntry(path,
                                                            objectType,
                                                            permissions,
                                                            owner,
                                                            group,
                                                            date,
                                                            time,
                                                            size,
                                                            info);
        myEntryList.add(entry);
      }
    }

    @Override
    public boolean isCancelled() {
      return false;
    }
  }
}