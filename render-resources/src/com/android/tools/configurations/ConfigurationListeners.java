/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.configurations;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Handles pub/sub notifications and bulk editing counts for {@link Configuration}.
 */
public class ConfigurationListeners {
  private final List<ConfigurationListener> myListeners = new ArrayList<>();
  private int myBulkEditingCount;

  public void startBulkEditing() {
    myBulkEditingCount++;
  }

  public boolean finishBulkEditing() {
    myBulkEditingCount--;
    return myBulkEditingCount == 0;
  }

  public boolean isBulkEditing() {
    return myBulkEditingCount > 0;
  }

  public void addListener(@NotNull ConfigurationListener listener) {
    synchronized (myListeners) {
      myListeners.add(listener);
    }
  }

  public void removeListener(@NotNull ConfigurationListener listener) {
    synchronized (myListeners) {
      myListeners.remove(listener);
    }
  }

  public void notifyListeners(int changedFlags) {
    ImmutableList<ConfigurationListener> listeners;
    synchronized (myListeners) {
      listeners = ImmutableList.copyOf(myListeners);
    }
    for (ConfigurationListener listener : listeners) {
      listener.changed(changedFlags);
    }
  }
}