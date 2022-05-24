// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.testutils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

abstract class AndroidComponentDownloaderProxy {
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

class AndroidLayoutlibDownloaderProxy extends AndroidComponentDownloaderProxy {
  protected AndroidLayoutlibDownloaderProxy() {
    super("org.jetbrains.android.download.AndroidLayoutlibDownloader");
  }
}

class AndroidProfilerDownloaderProxy extends AndroidComponentDownloaderProxy {
  protected AndroidProfilerDownloaderProxy() {
    super("org.jetbrains.android.download.AndroidProfilerDownloader");
  }
}

