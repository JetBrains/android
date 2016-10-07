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
package com.android.tools.idea.npw.assetstudio.ui;

import com.android.ide.common.vectordrawable.VdIcon;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Button which wraps a {@link VectorAsset}, allowing the user to browse a list of icons each
 * associated with a XML file representing a vector asset.
 */
public final class VectorIconButton extends JButton implements AssetComponent<VectorAsset>, Disposable {
  private final VectorAsset myXmlAsset = new VectorAsset(VectorAsset.FileType.VECTOR_DRAWABLE);
  private final BindingsManager myBindings = new BindingsManager();
  private final List<ActionListener> myAssetListeners = Lists.newArrayListWithExpectedSize(1);

  @Nullable private VdIcon myIcon;

  public VectorIconButton() {
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        IconPickerDialog iconPicker = new IconPickerDialog(myIcon);
        if (iconPicker.showAndGet()) {
          VdIcon selectedIcon = iconPicker.getSelectedIcon();
          assert selectedIcon != null; // Not null if user pressed OK
          updateIcon(selectedIcon);
        }
      }
    });

    myXmlAsset.path().addListener(new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        ActionEvent e = new ActionEvent(VectorIconButton.this, ActionEvent.ACTION_PERFORMED, null);
        for (ActionListener listener : myAssetListeners) {
          listener.actionPerformed(e);
        }
      }
    });

    updateIcon(IconPickerDialog.getDefaultIcon());
  }

  private void updateIcon(@NotNull VdIcon selectedIcon) {
    myIcon = null;
    setIcon(null);
    try {
      // The VectorAsset class works with files, but IconPicker returns resources from a jar. We
      // adapt by wrapping the resource into a temporary file.
      File iconFile = new File(FileUtil.getTempDirectory(), selectedIcon.getName());
      InputStream iconStream = selectedIcon.getURL().openStream();
      FileOutputStream outputStream = new FileOutputStream(iconFile);
      FileUtil.copy(iconStream, outputStream);
      myXmlAsset.path().set(iconFile);
      // Our icons are always square, so although parse() expects width, we can pass in height
      int h = getHeight() - getInsets().top - getInsets().bottom;
      VectorAsset.ParseResult result = myXmlAsset.parse(h);
      setIcon(new ImageIcon(result.getImage()));
      myIcon = selectedIcon;
    }
    catch (IOException ignored) {
    }
  }

  @NotNull
  @Override
  public VectorAsset getAsset() {
    return myXmlAsset;
  }

  @Override
  public void addAssetListener(@NotNull ActionListener l) {
    myAssetListeners.add(l);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myAssetListeners.clear();
  }
}
