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
package com.android.tools.idea.editors.gfxtrace;

import com.android.tools.rpclib.multiplex.Channel;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.intellij.openapi.application.ApplicationManager;

import com.intellij.openapi.diagnostic.Logger;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link Rpc.Callback} that will execute part of the callback on the UI thread.
 */
public abstract class UiCallback<T, U> implements Rpc.Callback<T> {

  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final Logger myLogger;

  public UiCallback(@NotNull GfxTraceEditor gfxTracer, @NotNull Logger log) {
    myEditor = gfxTracer;
    myLogger = log;
  }


  @Override
  public final void onFinish(Rpc.Result<T> result) {
    try {
      final U value = onRpcThread(result);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          onUiThread(value);
        }
      });
    }
    catch (CancellationException cancel) {
      // Not an error, don't log.
    }
    catch (RejectedExecutionException | Channel.NotConnectedException e) {
      if (myEditor.isDisposed()) {
        return;
      }
      myLogger.error("error in UiCallback.onRpcThread", e);
    }
    catch (Exception e) {
      myLogger.error("error in UiCallback.onRpcThread", e);
    }
  }

  /**
   * Executed on the executor passed to {@link Rpc}'s listen call. The result is then passed to
   * {@link #onUiThread(Object)} which is run on the AWT event dispatch thread.
   */
  protected abstract U onRpcThread(Rpc.Result<T> result) throws RpcException, ExecutionException, Channel.NotConnectedException;

  /**
   * Invoked on the AWT event dispatch thread with the returned value from {@link #onRpcThread(Rpc.Result)}.
   */
  protected abstract void onUiThread(U result);
}
