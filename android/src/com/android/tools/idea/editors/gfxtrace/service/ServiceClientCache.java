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
import com.android.tools.rpclib.schema.Message;
import com.google.common.base.Function;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.intellij.openapi.diagnostic.Logger;

import java.util.concurrent.Callable;

public class ServiceClientCache extends ServiceClientWrapper {
  private static final Logger LOG = Logger.getInstance(ServiceClientCache.class);

  private ListenableFuture<Message> mySchema;
  private final Object mySchemaLock = new Object();
  private final RpcCache<Path, Object> myPathCache;
  private final RpcCache<Path, Path> myFollowCache;

  public ServiceClientCache(final ServiceClient client, ListeningExecutorService executorService) {
    super(client);
    myPathCache = new RpcCache<Path, Object>(executorService) {
      @Override
      protected ListenableFuture<Object> fetch(Path key) {
        return client.get(key);
      }
    };
    myFollowCache = new RpcCache<Path, Path>(executorService) {
      @Override
      protected ListenableFuture<Path> fetch(Path key) {
        return client.follow(key);
      }
    };
  }

  @Override
  public ListenableFuture<Message> getSchema() {
    synchronized (mySchemaLock) {
      if (mySchema == null) {
        mySchema = myClient.getSchema();
      }
    }
    return mySchema;
  }

  @Override
  public ListenableFuture<Path> follow(Path p) {
    LOG.debug("Following " + p);
    return myFollowCache.get(p);
  }

  @Override
  public ListenableFuture<Object> get(Path p) {
    LOG.debug("Getting " + p);
    return myPathCache.get(p);
  }

  private abstract static class RpcCache<K, V> {
    private final Cache<K, V> myCache = CacheBuilder.newBuilder().softValues().build();
    private final ListeningExecutorService myExecutorService;

    public RpcCache(ListeningExecutorService executorService) {
      myExecutorService = executorService;
    }

    public ListenableFuture<V> get(final K key) {
      // Look up the value in the cache using the executor.
      ListenableFuture<V> cacheLookUp = myExecutorService.submit(new Callable<V>() {
        @Override
        public V call() throws Exception {
          return myCache.getIfPresent(key);
        }
      });
      return Futures.transform(cacheLookUp, new AsyncFunction<V, V>() {
        private boolean alreadyFetching = false;

        @Override
        public ListenableFuture<V> apply(V result) throws Exception {
          // If we found it in the cache or already tried to fetch it, return it, ...
          if (result != null || alreadyFetching) {
            myCache.put(key, result);
            return Futures.immediateFuture(result);
          }
          // ... otherwise go ahead and try to fetch it.
          alreadyFetching = true;
          return Futures.transform(fetch(key), this);
        }
      });
    }

    protected abstract ListenableFuture<V> fetch(K key);
  }
}
