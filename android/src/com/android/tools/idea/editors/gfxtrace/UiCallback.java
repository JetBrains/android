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

import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.intellij.openapi.application.ApplicationManager;

import java.util.concurrent.ExecutionException;

/**
 * A {@link Rpc.Callback} that will execute part of the callback on the UI thread.
 */
public abstract class UiCallback<T, U> implements Rpc.Callback<T> {
  @Override
  public final void onFinish(Rpc.Result<T> result) throws RpcException, ExecutionException {
    final U value = onRpcThread(result);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        onUiThread(value);
      }
    });
  }

  /**
   * Executed on the executor passed to {@link Rpc}'s listen call. The result is then passed to
   * {@link #onUiThread(Object)} which is run on the AWT event dispatch thread.
   */
  protected abstract U onRpcThread(Rpc.Result<T> result) throws RpcException, ExecutionException;

  /**
   * Invoked on the AWT event dispatch thread with the returned value from {@link #onRpcThread(Rpc.Result)}.
   */
  protected abstract void onUiThread(U result);
}
