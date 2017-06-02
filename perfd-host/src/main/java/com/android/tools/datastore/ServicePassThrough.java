/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.datastore;

import io.grpc.ServerServiceDefinition;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.util.List;

/**
 * Interface for a class that wraps a grpc service. Once connected to the service, you will need to
 * trigger its runner (probably on a background thread) to begin polling it.
 */
public interface ServicePassThrough {
  /**
   * @return bound service object for setting up an RPC client.
   */
  @NotNull
  ServerServiceDefinition bindService();

  /**
   * @return a list of namespaces to store the data
   */
  @NotNull
  List<DataStoreService.BackingNamespace> getBackingNamespaces();

  /**
   * @param namespace a namespace corresponding to an entry in the list returned from {@link #getBackingNamespaces()}
   * @param connection {@link Connection} to the backing store
   */
  void setBackingStore(@NotNull DataStoreService.BackingNamespace namespace, @NotNull Connection connection);
}
