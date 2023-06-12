/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.actions;

import com.android.annotations.NonNull;
import com.android.tools.concurrency.AndroidIoManager;
import com.android.utils.concurrency.CachedAsyncSupplier;
import com.google.common.collect.Maps;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import java.awt.Component;
import java.awt.Graphics;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/**
 * OpenProjectFileChooserDescriptorWithAsyncIcon is a customized open project file chooser with an
 * icon cache and async icon loading. The icon is chosen by a file type (extension, file, or
 * directory) by {@link IconUtil#getIcon(VirtualFile, int, Project)} then asynchronously be updated
 * to {@link OpenProjectFileChooserDescriptor#getIcon(VirtualFile)}.
 * <p>This class is a workaround solution for the issue b/37099520. Once the issue is addressed in
 * the upstream (IntelliJ open API), this class can be removed.
 */
public class OpenProjectFileChooserDescriptorWithAsyncIcon extends OpenProjectFileChooserDescriptor
  implements Disposable {
  private final Map<VirtualFile, Icon> myIconCache = Maps.newConcurrentMap();

  public OpenProjectFileChooserDescriptorWithAsyncIcon() {
    super(true);
  }

  @Override
  public Icon getIcon(VirtualFile file) {
    return myIconCache.computeIfAbsent(file, key -> {
      // Use file type based icon (file or directory) first and may update it to android project
      // icon later asynchronously. Determining if a directory is an android project directory
      // is very expensive because we need to get a list of files of the directory. See b/37099520
      // for details.
      Icon placeholder = dressIcon(key, IconUtil.getIcon(key, Iconable.ICON_FLAG_READ_STATUS, null));
      CachedAsyncSupplier<Icon> asyncIconSupplier = new CachedAsyncSupplier<>(
        () -> Disposer.isDisposed(this) ? placeholder : super.getIcon(key),
        AndroidIoManager.getInstance().getBackgroundDiskIoExecutor());
      AsyncIcon icon = new AsyncIcon(placeholder, asyncIconSupplier);
      return icon;
    });
  }

  @Override
  public void dispose() {
    myIconCache.clear();
  }

  /**
   * AsyncIcon displays a {@code placeholderIcon} at the beginning and swaps it with the real icon
   * after the one is computed.
   */
  private static class AsyncIcon implements Icon {
    @NotNull private final Icon myPlaceholderIcon;
    @NotNull private final CachedAsyncSupplier<Icon> myIconAsyncSupplier;

    AsyncIcon(@NotNull Icon placeholderIcon, @NotNull CachedAsyncSupplier<Icon> iconAsyncSupplier) {
      myPlaceholderIcon = placeholderIcon;
      myIconAsyncSupplier = iconAsyncSupplier;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      getIcon().paintIcon(c, g, x, y);
    }

    @Override
    public int getIconWidth() {
      return getIcon().getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return getIcon().getIconHeight();
    }

    @NonNull
    private final Icon getIcon() {
      Icon icon = myIconAsyncSupplier.getNow();
      return icon != null ? icon : myPlaceholderIcon;
    }
  }
}
