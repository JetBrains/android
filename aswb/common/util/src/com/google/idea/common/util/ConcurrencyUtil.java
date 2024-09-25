/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.common.util;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.concurrent.ThreadFactory;

/** Utils for concurrency. */
public final class ConcurrencyUtil {

  private static final ListeningScheduledExecutorService executor =
      MoreExecutors.listeningDecorator(AppExecutorUtil.getAppScheduledExecutorService());

  private ConcurrencyUtil() {}

  public static ListeningScheduledExecutorService getAppExecutorService() {
    return executor;
  }

  public static ThreadFactory namedDaemonThreadPoolFactory(Class<?> klass) {
    return new ThreadFactoryBuilder()
        .setNameFormat(klass.getSimpleName() + "-%d")
        .setDaemon(true)
        .build();
  }
}
