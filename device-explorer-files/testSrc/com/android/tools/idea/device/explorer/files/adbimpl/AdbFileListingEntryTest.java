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
package com.android.tools.idea.device.explorer.files.adbimpl;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class AdbFileListingEntryTest {
  @Test
  public void testFileFields() {
    AdbFileListingEntry entry = createTestEntry(AdbFileListingEntry.EntryKind.FILE);

    assertTestEntry(entry, AdbFileListingEntry.EntryKind.FILE);
    assertThat(entry.isFile()).isTrue();
    assertThat(entry.isDirectory()).isFalse();
    assertThat(entry.isSymbolicLink()).isFalse();
  }

  @Test
  public void testDirectoryFields() {
    AdbFileListingEntry entry = createTestEntry(AdbFileListingEntry.EntryKind.DIRECTORY);

    assertTestEntry(entry, AdbFileListingEntry.EntryKind.DIRECTORY);
    assertThat(entry.isFile()).isFalse();
    assertThat(entry.isDirectory()).isTrue();
    assertThat(entry.isSymbolicLink()).isFalse();
  }

  @Test
  public void testFileLinkFields() {
    AdbFileListingEntry entry = createTestEntry(AdbFileListingEntry.EntryKind.SYMBOLIC_LINK);

    assertTestEntry(entry, AdbFileListingEntry.EntryKind.SYMBOLIC_LINK);
    assertThat(entry.isFile()).isFalse();
    assertThat(entry.isDirectory()).isFalse();
    assertThat(entry.isSymbolicLink()).isTrue();
  }

  @NotNull
  private static AdbFileListingEntry createTestEntry(@NotNull AdbFileListingEntry.EntryKind kind) {
    return new AdbFileListingEntry("/foo/bar",
                                   kind,
                                   "xxx",
                                   "owner",
                                   "group",
                                   "2000-01-01",
                                   "10:00:00",
                                   "4096",
                                   "/bar/blah");
  }

  private static void assertTestEntry(@NotNull AdbFileListingEntry entry, @NotNull AdbFileListingEntry.EntryKind kind) {
    assertThat(entry.getFullPath()).isEqualTo("/foo/bar");
    assertThat(entry.getKind()).isEqualTo(kind);
    assertThat(entry.getName()).isEqualTo("bar");
    assertThat(entry.getPermissions()).isEqualTo("xxx");
    assertThat(entry.getOwner()).isEqualTo("owner");
    assertThat(entry.getGroup()).isEqualTo("group");
    assertThat(entry.getDate()).isEqualTo("2000-01-01");
    assertThat(entry.getTime()).isEqualTo("10:00:00");
    assertThat(entry.getSize()).isEqualTo(4096);
    assertThat(entry.getInfo()).isEqualTo("/bar/blah");
  }
}
