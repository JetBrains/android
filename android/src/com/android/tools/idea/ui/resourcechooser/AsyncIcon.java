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
package com.android.tools.idea.ui.resourcechooser;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Icon that loads asynchronously from a given {@link ListenableFuture}
 */
public class AsyncIcon implements Icon {
  @NotNull private Icon myIcon;
  private final int myW;
  private final int myH;

  /**
   * Creates a new {@link AsyncIcon} from the given {@link ListenableFuture}
   * @param futureIcon The {@link ListenableFuture} for the icon that it's been loaded
   * @param placeholderIcon A placeholder icon to be used while futureIcon loads. This must have the same dimensions
   *                        as the futureIcon
   * @param onIconLoad Callback that will be notified when the icon is loaded and the placeholder is not being displayed
   *                   anymore.
   */
  public AsyncIcon(@NotNull ListenableFuture<? extends Icon> futureIcon,
                   @NotNull Icon placeholderIcon,
                   @Nullable Runnable onIconLoad) {
    myIcon = placeholderIcon;
    myW = placeholderIcon.getIconWidth();
    myH = placeholderIcon.getIconHeight();

    Futures.addCallback(futureIcon, new FutureCallback<Icon>() {
      @Override
      public void onSuccess(@Nullable Icon result) {
        if (result != null) {
          myIcon = result;
        }

        if (onIconLoad != null) {
          onIconLoad.run();
        }
      }

      @Override
      public void onFailure(@NotNull Throwable e) {
        Logger.getInstance(AsyncIcon.class).warn("Unable to load AsyncIcon", e);
      }
    });
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Icon icon = myIcon;

    assert icon.getIconWidth() == myW && icon.getIconHeight() == myH;
    icon.paintIcon(c, g, x, y);
  }

  @Override
  public int getIconWidth() {
    return myW;
  }

  @Override
  public int getIconHeight() {
    return myH;
  }
}
