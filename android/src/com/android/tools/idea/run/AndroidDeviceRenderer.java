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
package com.android.tools.idea.run;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBList;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class AndroidDeviceRenderer extends ColoredListCellRenderer<DevicePickerEntry> {
  private static final Logger LOG = Logger.getInstance(AndroidDeviceRenderer.class);
  private final LaunchCompatibilityChecker myCompatibilityChecker;
  private final SpeedSearchBase mySpeedSearch;
  private final Map<AndroidDevice, SettableFuture<LaunchCompatibility>> myCompatibilityCache = new IdentityHashMap<>();

  public AndroidDeviceRenderer(@NotNull LaunchCompatibilityChecker checker, @NotNull SpeedSearchBase speedSearch) {
    myCompatibilityChecker = checker;
    mySpeedSearch = speedSearch;
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends DevicePickerEntry> list,
                                                DevicePickerEntry value,
                                                int index,
                                                boolean selected,
                                                boolean hasFocus) {
    if (value != null && value.isMarker()) {
      String marker = value.getMarker();
      assert marker != null : "device picker marker entry doesn't have a descriptive string";

      if (value == DevicePickerEntry.NONE) {
        return renderEmptyMarker(marker);
      }
      else {
        return renderTitledSeparator(marker);
      }
    }

    return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
  }

  @Override
  protected void customizeCellRenderer(@NotNull JList list, DevicePickerEntry entry, int index, boolean selected, boolean hasFocus) {
    AndroidDevice device = entry.getAndroidDevice();
    assert device != null;

    clear();

    if (shouldShowSerialNumbers(list, device)) {
      append("[" + device.getSerial() + "] ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }

    SettableFuture<LaunchCompatibility> compatibilityFuture = getCachedCompatibilityFuture(device);
    boolean compatibilityIsKnown = compatibilityFuture != null && compatibilityFuture.isDone();
    LaunchCompatibility launchCompatibility = LaunchCompatibility.YES;
    if (compatibilityIsKnown) {
      try {
        launchCompatibility = compatibilityFuture.get();
      } catch (ExecutionException e) {
        // The exception has been logged already.
      } catch (InterruptedException e) {
        assert false;  // Not possible since the future is complete.
      }
    }

    boolean compatible = launchCompatibility.isCompatible() != ThreeState.NO;
    device.renderLabel(this, compatible, mySpeedSearch.getEnteredPrefix());
    if (launchCompatibility.getReason() != null) {
      append(" (" + launchCompatibility.getReason() + ")", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
    }

    if (!compatibilityIsKnown && compatibilityFuture != null) {
      setPaintBusy(list, true);
      // Obtaining device information is potentially a long running operation. Execute it on a background thread.
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        device.prepareToRenderLabel();
        if (!compatibilityFuture.isDone()) {
          try {
            compatibilityFuture.set(myCompatibilityChecker.validate(device));
          } catch (Throwable t) {
            compatibilityFuture.setException(t);
            LOG.warn(t);
          }
        }

        EdtInvocationManager.getInstance().invokeLater(() -> {
          ListModel model = list.getModel();
          if (model instanceof DevicePickerListModel) {
            ((DevicePickerListModel)model).entryContentChanged(entry);
            setPaintBusy(list, false);
          }
        });
      });
    }
  }

  private static void setPaintBusy(JList list, boolean busy) {
    if (list instanceof JBList) {
      ((JBList)list).setPaintBusy(busy);
    }
  }

  private static boolean shouldShowSerialNumbers(@NotNull JList list, @NotNull AndroidDevice device) {
    if (device.isVirtual()) {
      return false;
    }

    ListModel model = list.getModel();
    if (model instanceof DevicePickerListModel) {
      return ((DevicePickerListModel)model).shouldShowSerialNumbers();
    }
    return false;
  }

  private static Component renderTitledSeparator(@NotNull String title) {
    TitledSeparator separator = new TitledSeparator(title);
    separator.setBackground(UIUtil.getListBackground());
    separator.setTitleFont(UIUtil.getLabelFont());
    return separator;
  }

  private static Component renderEmptyMarker(@NotNull String title) {
    return new JLabel(title);
  }

  /**
   * Returns either a completed cached compatibility future for the given device, or a brand new future for which no attempt to set its
   * value has been made. If a cached future for the given device exists prior to calling this method, but the future is not complete,
   * the method returns null.
   */
  private SettableFuture<LaunchCompatibility> getCachedCompatibilityFuture(AndroidDevice device) {
    SettableFuture<LaunchCompatibility> compatibilityFuture = myCompatibilityCache.get(device);
    if (compatibilityFuture == null) {
      compatibilityFuture = SettableFuture.create();
      myCompatibilityCache.put(device, compatibilityFuture);
      return compatibilityFuture;
    }
    return compatibilityFuture.isDone() ? compatibilityFuture : null;
  }

  /**
   * Clears the cached data contained in the renderer.
   */
  public void clearCache() {
    assert ApplicationManager.getApplication().isDispatchThread();
    myCompatibilityCache.clear();
  }
}
