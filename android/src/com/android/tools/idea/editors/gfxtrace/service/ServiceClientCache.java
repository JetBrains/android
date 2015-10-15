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
package com.android.tools.idea.editors.gfxtrace.service;

import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;

public class ServiceClientCache extends ServiceClientWrapper {
  private ListenableFuture<Schema> mySchema;
  private final Object mySchemaLock = new Object();
  private final LoadingCache<Path, ListenableFuture<Object>> myPathCache;
  private static final int MAXIMUM_CACHE_COUNT = 1000;

  public ServiceClientCache(ServiceClient client) {
    super(client);
    myPathCache = CacheBuilder.newBuilder()
      .maximumSize(MAXIMUM_CACHE_COUNT)
      .softValues()
      .build(new CacheLoader<Path, ListenableFuture<Object>>() {
        @Override
        public ListenableFuture<Object> load(Path p) {
          return myClient.get(p);
        }
      });
  }

  @Override
  public ListenableFuture<Schema> getSchema() {
    synchronized (mySchemaLock) {
      if (mySchema == null) {
        mySchema = myClient.getSchema();
      }
    }
    return mySchema;
  }

  @Override
  public ListenableFuture<Object> get(Path p) {
    return myPathCache.getUnchecked(p);
  }
}
