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

import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

/**
 * Text field with browse button which wraps a {@link VectorAsset}, which allows the user to
 * specify a path to an SVG file.
 */
public final class VectorAssetBrowser extends TextFieldWithBrowseButton implements AssetComponent<VectorAsset>, Disposable {
  private final VectorAsset mySvgAsset = new VectorAsset(VectorAsset.FileType.SVG);
  private final BindingsManager myBindings = new BindingsManager();

  private final List<ActionListener> myAssetListeners = Lists.newArrayListWithExpectedSize(1);

  public VectorAssetBrowser() {
    addBrowseFolderListener(null, null, null, FileChooserDescriptorFactory.createSingleFileDescriptor("svg"));

    final StringProperty svgAbsolutePath = new TextProperty(getTextField());
    myBindings.bind(svgAbsolutePath, mySvgAsset.path().transform(File::getAbsolutePath));
    myBindings.bind(mySvgAsset.path(), svgAbsolutePath.transform(File::new));

    mySvgAsset.path().addListener(new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        ActionEvent e = new ActionEvent(VectorAssetBrowser.this, ActionEvent.ACTION_PERFORMED, null);
        for (ActionListener listener : myAssetListeners) {
          listener.actionPerformed(e);
        }
      }
    });
  }

  @NotNull
  @Override
  public VectorAsset getAsset() {
    return mySvgAsset;
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
