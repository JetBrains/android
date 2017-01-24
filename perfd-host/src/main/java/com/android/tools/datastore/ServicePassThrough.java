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

import com.android.tools.datastore.database.DatastoreTable;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;

import java.util.concurrent.RunnableFuture;

/**
 * Interface for a class that wraps a grpc service. Once connected to the service, you will need to
 * trigger its runner (probably on a background thread) to begin polling it.
 */
public interface ServicePassThrough {

  /**
   * @return bound service object for setting up an RPC client.
   */
  ServerServiceDefinition bindService();

  /**
   * @return a DatastoreTable for the Datastore to register a connection with, or null.
   */
  DatastoreTable getDatastoreTable();
}
