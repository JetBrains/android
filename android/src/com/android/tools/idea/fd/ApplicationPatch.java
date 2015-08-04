/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// This class is used in both the Android runtime and in the IDE.
// Technically we only need the write protocol on the IDE side and the
// read protocol on the Android app size, but keeping it all together and
// in sync right now.
@SuppressWarnings({"Assert", "unused"})
public class ApplicationPatch {
  /**
   * Magic (random) number used to identify the protocol
   */
  public static final long PROTOCOL_IDENTIFIER = 0x35107124L;
  public static final int PROTOCOL_VERSION = 1;

  public final String path;
  public final byte[] data;
  public boolean forceRestart;

  public ApplicationPatch(@NonNull String path, @NonNull byte[] data) {
    this.path = path;
    this.data = data;
  }

  @Override
  public String toString() {
    return "ApplicationPatch{" +
           "path='" + path + '\'' +
           ", data.length='" + data.length + '\'' +
           '}';
  }

  // Only needed on the IDE side
  public static void write(@NonNull DataOutputStream output, @Nullable List<ApplicationPatch> changes, boolean forceRestart)
      throws IOException {
    output.writeLong(PROTOCOL_IDENTIFIER);
    output.writeInt(PROTOCOL_VERSION);

    if (changes == null) {
      output.writeInt(0);
    } else {
      output.writeInt(changes.size());
      for (ApplicationPatch change : changes) {
        write(output, change);
      }
    }
    output.writeBoolean(forceRestart);
  }

  // Only needed on the IDE side
  private static void write(@NonNull DataOutputStream output, @NonNull ApplicationPatch change)
    throws IOException {
    output.writeUTF(change.path);
    byte[] bytes = change.data;
    output.writeInt(bytes.length);
    output.write(bytes);
  }

  // Only needed on the Android side
  @Nullable
  public static List<ApplicationPatch> read(@NonNull DataInputStream input) throws IOException {
    long magic = input.readLong();
    if (magic != PROTOCOL_IDENTIFIER) {
      return null;
    }
    int version = input.readInt();
    if (version != PROTOCOL_VERSION) {
      return null;
    }

    int changeCount = input.readInt();

    List<ApplicationPatch> changes = new ArrayList<ApplicationPatch>(changeCount);
    for (int i = 0; i < changeCount; i++) {
      String path = input.readUTF();
      int size = input.readInt();
      byte[] bytes = new byte[size];
      input.readFully(bytes);
      changes.add(new ApplicationPatch(path, bytes));
    }

    boolean forceRestart = input.readBoolean();
    if (changeCount > 0) {
      changes.get(0).forceRestart = forceRestart;
    }

    return changes;
  }

  public String getPath() {
    return path;
  }

  public byte[] getBytes() {
    return data;
  }
}
