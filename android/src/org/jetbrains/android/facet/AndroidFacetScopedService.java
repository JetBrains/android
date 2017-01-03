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
package org.jetbrains.android.facet;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.GuardedBy;

public abstract class AndroidFacetScopedService implements Disposable {
  private final Object myLock = new Object();

  @GuardedBy("myLock")
  private AndroidFacet myFacet;

  @GuardedBy("myLock")
  private boolean myDisposed;

  protected AndroidFacetScopedService(@NotNull AndroidFacet facet) {
    myFacet = facet;
    Disposer.register(facet, this);
  }

  @Override
  public final void dispose() {
    onDispose();
    synchronized (myLock) {
      onServiceDisposal(myFacet);
      myFacet = null;
      myDisposed = true;
    }
  }

  protected void onDispose() {
  }

  public final boolean isDisposed() {
    synchronized (myLock) {
      return myDisposed;
    }
  }

  @NotNull
  protected final Module getModule() {
    return getFacet().getModule();
  }

  @NotNull
  public final AndroidFacet getFacet() {
    AndroidFacet facet;
    synchronized (myLock) {
      if (myDisposed) {
        throw new IllegalStateException(getClass().getName() + " is disposed");
      }
      assert myFacet != null && !myFacet.isDisposed();
      facet = myFacet;
    }
    return facet;
  }

  protected abstract void onServiceDisposal(@NotNull AndroidFacet facet);
}
