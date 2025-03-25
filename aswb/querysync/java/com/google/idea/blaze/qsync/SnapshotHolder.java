/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.ByteSource;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.exception.BuildException;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Keeps a reference to the most up-to date {@link QuerySyncProjectSnapshot} instance. */
public class SnapshotHolder {

  private final Object lock = new Object();
  @Nullable private QuerySyncProjectSnapshot currentInstance = null;

  private final List<QuerySyncProjectListener> listeners = Lists.newArrayList();

  public SnapshotHolder() {}

  public void addListener(QuerySyncProjectListener listener) {
    synchronized (lock) {
      listeners.add(listener);
    }
  }

  public void setCurrent(Context<?> context, QuerySyncProjectSnapshot newInstance)
      throws BuildException {
    ImmutableList<QuerySyncProjectListener> listeners;
    synchronized (lock) {
      if (currentInstance == newInstance) {
        return;
      }
      currentInstance = newInstance;
      listeners = ImmutableList.copyOf(this.listeners);
    }
    for (QuerySyncProjectListener l : listeners) {
      l.onNewProjectSnapshot(context, newInstance);
    }
  }

  public Optional<QuerySyncProjectSnapshot> getCurrent() {
    synchronized (lock) {
      return Optional.ofNullable(currentInstance);
    }
  }

  public ImmutableMap<String, ByteSource> getBugreportFiles() {
    QuerySyncProjectSnapshot instance = currentInstance;
    if (instance == null) {
      return ImmutableMap.of("projectProto", ByteSource.empty());
    }
    return ImmutableMap.of("projectProto", ByteSource.wrap(instance.project().toByteArray()));
  }
}
