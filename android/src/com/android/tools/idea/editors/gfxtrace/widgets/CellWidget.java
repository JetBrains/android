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
package com.android.tools.idea.editors.gfxtrace.widgets;

import com.android.tools.idea.editors.gfxtrace.renderers.CellRenderer;
import com.android.tools.rpclib.futures.SingleInFlight;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Displays a list of loadable cells.
 */
public abstract class CellWidget<T extends CellWidget.Data, C extends JComponent> extends JPanel {
  public static class Data {
    public final SingleInFlight controller = new SingleInFlight(new SingleInFlight.Listener() {
      @Override
      public void onIdleToWorking() {
        startLoading();
      }

      @Override
      public void onWorkingToIdle() {
        stopLoading();
      }
    });

    protected LoadingState loadingState = LoadingState.NOT_LOADED;

    public boolean isSelected = false;

    public boolean requiresLoading() {
      return loadingState == LoadingState.NOT_LOADED;
    }

    public boolean isLoading() {
      return loadingState == LoadingState.LOADING;
    }

    public boolean isLoaded() {
      return loadingState == LoadingState.LOADED;
    }

    public void startLoading() {
      loadingState = LoadingState.LOADING;
    }

    public void stopLoading() {
      loadingState = LoadingState.LOADED;
    }

    protected enum LoadingState {
      NOT_LOADED, LOADING, LOADED;
    }
  }

  @NotNull protected final C myComponent;
  @NotNull protected final CellRenderer<T> myRenderer;
  @NotNull private List<T> myData = Collections.emptyList();
  @NotNull private AtomicBoolean myFireSelectionEvents = new AtomicBoolean(true);

  public CellWidget(C component, CellRenderer.CellLoader<T> loader) {
    super(new BorderLayout());
    myComponent = component;
    myRenderer = createCellRenderer(loader);
    add(myComponent, BorderLayout.CENTER);
  }

  protected abstract CellRenderer<T> createCellRenderer(CellRenderer.CellLoader<T> loader);

  public CellRenderer<T> getRenderer() {
    return myRenderer;
  }

  public void addSelectionListener(final SelectionListener listener) {
    addSelectionListener(myComponent, new SelectionListener<T>() {
      @Override
      public void selected(T item) {
        if (myFireSelectionEvents.get()) {
          listener.selected(item);
        }
      }
    });
  }

  protected abstract void addSelectionListener(C component, SelectionListener<T> selectionListener);

  public Iterable<T> items() {
    return myData;
  }

  public boolean isEmpty() {
    return myData.isEmpty();
  }

  public void setData(@NotNull List<T> data) {
    myData = data;
  }

  public abstract int getSelectedItem();

  public void selectItem(int index, boolean fireEvents) {
    boolean previousValue = myFireSelectionEvents.getAndSet(fireEvents);
    try {
      setSelectedIndex(myComponent, index);
    } finally {
      myFireSelectionEvents.set(previousValue);
    }
  }

  protected abstract void setSelectedIndex(C component, int index);

  public interface SelectionListener<T> {
    void selected(T item);
  }
}
