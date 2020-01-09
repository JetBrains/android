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
package com.android.tools.idea.npw.assetstudio.ui;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.idea.npw.assetstudio.assets.TextAsset;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.InvalidationListener;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.ArrayUtil;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import org.jetbrains.annotations.NotNull;

/**
 * Panel which wraps a {@link TextAsset}, allowing the user to enter multi-line text and choose a font
 * from a pulldown.
 */
public final class MultiLineTextAssetEditor extends JPanel implements AssetComponent<TextAsset>  {
  private final TextAsset myTextAsset = new TextAsset();
  private final BindingsManager myBindings = new BindingsManager();
  private final List<ActionListener> myListeners = new ArrayList<>(1);

  public MultiLineTextAssetEditor() {
    super(new TabularLayout("4px,120px,*"));

    JPanel spacer = new JPanel();
    JTextPane textPane = new JTextPane();
    textPane.setBackground(JBColor.WHITE);
    textPane.setBorder(new CustomLineBorder(JBColor.border(), 1, 1, 1, 1));
    List<String> fontFamilies = TextAsset.getAllFontFamilies();
    ComboBox<String> fontCombo = new ComboBox<>(ArrayUtil.toStringArray(fontFamilies));

    add(spacer, new TabularLayout.Constraint(0, 0));
    add(textPane, new TabularLayout.Constraint(0, 1));
    add(fontCombo, new TabularLayout.Constraint(0, 2));

    myBindings.bindTwoWay(new TextProperty(textPane), myTextAsset.text());

    SelectedItemProperty<String> selectedFont = new SelectedItemProperty<>(fontCombo);
    myBindings.bindTwoWay(ObjectProperty.wrap(selectedFont), myTextAsset.fontFamily());

    InvalidationListener onTextChanged = () -> {
      ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
      for (ActionListener listener : myListeners) {
        listener.actionPerformed(e);
      }
    };

    myTextAsset.text().addListener(onTextChanged);
    myTextAsset.fontFamily().addListener(onTextChanged);
  }

  @Override
  @NotNull
  public TextAsset getAsset() {
    return myTextAsset;
  }

  @Override
  public void addAssetListener(@NotNull ActionListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.clear();
  }
}
