/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A device identifier. When the selected device is persisted in the {@link com.intellij.ide.util.PropertiesComponent PropertiesComponent,}
 * the output of the device key's {@link #toString} method is what actually gets persisted.
 */
public final class Key {
  @NotNull
  private final String myDeviceKey;

  @Nullable
  private final String mySnapshotKey;

  public Key(@NotNull String key) {
    int index = key.indexOf('/');

    if (index == -1) {
      myDeviceKey = key;
      mySnapshotKey = null;
    }
    else {
      myDeviceKey = key.substring(0, index);
      mySnapshotKey = key.substring(index + 1);
    }
  }

  Key(@NotNull String deviceKey, @Nullable Snapshot snapshot) {
    myDeviceKey = deviceKey;
    mySnapshotKey = snapshot == null ? null : snapshot.getDirectory().toString();
  }

  @NotNull
  String getDeviceKey() {
    return myDeviceKey;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof Key)) {
      return false;
    }

    Key key = (Key)object;
    return myDeviceKey.equals(key.myDeviceKey) && Objects.equals(mySnapshotKey, key.mySnapshotKey);
  }

  @Override
  public int hashCode() {
    return 31 * myDeviceKey.hashCode() + Objects.hashCode(mySnapshotKey);
  }

  @NotNull
  @Override
  public String toString() {
    return mySnapshotKey == null ? myDeviceKey : myDeviceKey + '/' + mySnapshotKey;
  }
}
