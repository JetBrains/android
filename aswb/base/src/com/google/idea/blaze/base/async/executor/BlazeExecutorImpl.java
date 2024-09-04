/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.async.executor;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.concurrent.Callable;

/** Executes blaze tasks on the an executor. */
public class BlazeExecutorImpl extends BlazeExecutor {

  private final ListeningExecutorService executorService =
      MoreExecutors.listeningDecorator(
          AppExecutorUtil.createBoundedApplicationPoolExecutor("BlazeExecutor", 16));

  @Override
  public <T> ListenableFuture<T> submit(Callable<T> callable) {
    return executorService.submit(callable);
  }

  @Override
  public ListeningExecutorService getExecutor() {
    return executorService;
  }

}
