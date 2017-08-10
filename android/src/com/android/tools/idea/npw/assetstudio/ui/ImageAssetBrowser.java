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

import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.InvalidationListener;
import com.android.tools.idea.observable.ui.TextProperty;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Panel which wraps a {@link ImageAsset}, allowing the user to browse for an image file to use as
 * an asset.
 */
public final class ImageAssetBrowser extends TextFieldWithBrowseButton implements AssetComponent<ImageAsset> {
  private final ImageAsset myImageAsset = new ImageAsset();
  private final BindingsManager myBindings = new BindingsManager();
  private final List<ActionListener> myListeners = new ArrayList(1);

  public ImageAssetBrowser() {
    addBrowseFolderListener(null, null, null, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());

    TextProperty imagePathText = new TextProperty(getTextField());
    myBindings.bind(imagePathText, myImageAsset.imagePath().transform(file -> file.map(File::getAbsolutePath).orElse("")));
    myBindings.bind(myImageAsset.imagePath(), imagePathText.transform(s -> {
      return StringUtil.isEmptyOrSpaces(s) ? Optional.empty() : Optional.of(new File(s.trim()));
    }));

    InvalidationListener onImageChanged = sender -> {
      ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
      for (ActionListener listener : myListeners) {
        listener.actionPerformed(e);
      }
    };
    myImageAsset.imagePath().addListener(onImageChanged);
  }

  @NotNull
  @Override
  public ImageAsset getAsset() {
    return myImageAsset;
  }

  @Override
  public void addAssetListener(@NotNull ActionListener l) {
    myListeners.add(l);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.clear();
  }
}
