/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.ddms.adb;

import com.android.builder.model.AdbOptions;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Getter;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.GuardedBy;
import java.util.List;

public class AdbOptionsService implements Getter<AdbOptionsService> {
  private static final String USE_LIBUSB = "adb.use.libusb";
  private static final boolean LIBUSB_DEFAULT = false;

  private final Object LOCK = new Object();

  @GuardedBy("LOCK")
  private List<AdbOptionsListener> myListeners = new SmartList<>();

  public interface AdbOptionsListener {
    void optionsChanged();
  }

  public static AdbOptionsService getInstance() {
    return ServiceManager.getService(AdbOptionsService.class);
  }

  @Override
  public AdbOptionsService get() {
    return this;
  }

  public boolean shouldUseLibusb() {
    return PropertiesComponent.getInstance().getBoolean(USE_LIBUSB, LIBUSB_DEFAULT);
  }

  public void setUseLibusb(boolean en) {
    PropertiesComponent.getInstance().setValue(USE_LIBUSB, en);
    updateListeners();
  }

  private void updateListeners() {
    List<AdbOptionsListener> listeners;
    synchronized (LOCK) {
      listeners = ImmutableList.copyOf(myListeners);
    }

    for (AdbOptionsListener listener : listeners) {
      listener.optionsChanged();
    }
  }

  public void addListener(@NotNull AdbOptionsListener listener) {
    synchronized (LOCK) {
      myListeners.add(listener);
    }
  }

  public void removeListener(@NotNull AdbOptionsListener listener) {
    synchronized (LOCK) {
      myListeners.remove(listener);
    }
  }
}
