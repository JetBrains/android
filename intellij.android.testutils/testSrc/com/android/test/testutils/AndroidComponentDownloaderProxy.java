/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.test.testutils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AndroidComponentDownloaderProxy {
  public static final Logger LOGGER = Logger.getInstance(AndroidComponentDownloaderProxy.class);

  private final Method download;
  private final Method getInst;
  private final String className;

  protected AndroidComponentDownloaderProxy(String className) {
    Method download = null;
    Method getInst = null;

    try {
      Class<?> downloader = Class.forName(className);
      download = downloader.getMethod("makeSureComponentIsInPlace");
      getInst = downloader.getMethod("getInstance");
    }
    catch (ClassNotFoundException e) {
      LOGGER.warn("Will not download, because class not found: " + className, e);
    }
    catch (NoSuchMethodException e) {
      throw new IllegalStateException("Could not instantiate @" + className, e);
    }

    this.download = download;
    this.getInst = getInst;
    this.className = className;
  }

  public void makeSureComponentIsInPlace() {
    if (download == null || getInst == null) return;

    Future<?> future = AppExecutorUtil.getAppExecutorService().submit(() -> {
      try {
        Object inst = getInst.invoke(null);
        download.invoke(inst);
      }
      catch (Exception e) {
        throw new IllegalStateException("Could not download component @" + className, e);
      }
    });

    try {
      future.get(2, TimeUnit.MINUTES);
    }
    catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Could not download component @" + className, e);
    }
  }
}

