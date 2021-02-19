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
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ui.TextProperty;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * A text field with a browse button which wraps a {@link VectorAsset}, which allows the user
 * to specify a path to an SVG or PSD file.
 */
public final class VectorAssetBrowser extends TextFieldWithBrowseButton implements AssetComponent<VectorAsset>, Disposable {
  @NotNull private final VectorAsset myAsset = new VectorAsset();
  @NotNull private final BindingsManager myBindings = new BindingsManager();

  @NotNull private final List<ActionListener> myAssetListeners = new ArrayList<>(1);

  public VectorAssetBrowser() {
    addBrowseFolderListener(null, null, null, createFileDescriptor("svg", "psd"));

    TextProperty imagePathText = new TextProperty(getTextField());
    myBindings.bind(imagePathText, myAsset.path().transform(file -> file.map(File::getAbsolutePath).orElse("")));
    myBindings.bind(myAsset.path(),
                    imagePathText.transform(s -> StringUtil.isEmptyOrSpaces(s) ? Optional.empty() : Optional.of(new File(s.trim()))));

    myAsset.path().addListener(() -> {
      ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
      for (ActionListener listener : myAssetListeners) {
        listener.actionPerformed(e);
      }
    });
  }

  @Override
  @NotNull
  public VectorAsset getAsset() {
    return myAsset;
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

  private static FileChooserDescriptor createFileDescriptor(@NotNull String... extensions) {
    return FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withFileFilter(
        file -> Arrays.stream(extensions).anyMatch(e -> Comparing.equal(file.getExtension(), e, file.isCaseSensitive())));
  }
}
