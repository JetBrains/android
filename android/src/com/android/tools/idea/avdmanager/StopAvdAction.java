/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.tools.idea.concurrency.FutureUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.swing.event.SwingPropertyChangeSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class StopAvdAction extends AvdUiAction {
  private boolean myEnabled;

  @NotNull
  private final PropertyChangeSupport myPropertyChangeSupport;

  @NotNull
  private final Function<AvdInfoProvider, ListenableFuture<Boolean>> myIsAvdRunning;

  @NotNull
  private final Executor myExecutor;

  StopAvdAction(@NotNull AvdInfoProvider provider) {
    this(
      provider,
      AvdManagerConnection.getDefaultAvdManagerConnection()::isAvdRunning,
      EdtExecutorService.getInstance()
    );
  }

  @VisibleForTesting
  StopAvdAction(@NotNull AvdInfoProvider provider,
                @NotNull Function<AvdInfoProvider, ListenableFuture<Boolean>> isAvdRunning,
                @NotNull Executor executor) {
    super(provider, "Stop", "Stop the emulator running this AVD", AllIcons.Actions.Suspend);

    myEnabled = true;
    myPropertyChangeSupport = new SwingPropertyChangeSupport(this);
    myIsAvdRunning = isAvdRunning;
    myExecutor = executor;
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  @Override
  public void setEnabled(boolean enabled) {
    boolean oldEnabled = myEnabled;
    myEnabled = enabled;

    myPropertyChangeSupport.firePropertyChange("enabled", oldEnabled, enabled);
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);

    FutureUtils.addCallback(myIsAvdRunning.apply(myAvdInfoProvider), myExecutor, new FutureCallback<Boolean>() {
      @Override
      public void onSuccess(@Nullable Boolean running) {
        // noinspection ConstantConditions
        setEnabled(running);
      }

      @Override
      public void onFailure(@NotNull Throwable throwable) {
        Logger.getInstance(StopAvdAction.class).warn(throwable);
      }
    });
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  @Override
  public void actionPerformed(@NotNull ActionEvent event) {
    AvdManagerConnection.getDefaultAvdManagerConnection().stopAvd(Objects.requireNonNull(getAvdInfo()));
  }
}
