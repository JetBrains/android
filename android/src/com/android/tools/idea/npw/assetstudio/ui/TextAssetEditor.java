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

import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.TextAsset;
import com.android.tools.idea.ui.ProportionalLayout;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.expressions.value.AsValueExpression;
import com.android.tools.idea.ui.properties.swing.SelectedItemProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Panel which wraps a {@link TextAsset}, allowing the user to enter text value and choose a font
 * from a pulldown.
 */
public final class TextAssetEditor extends JPanel implements AssetComponent {
  private final TextAsset myTextAsset = new TextAsset();
  private final BindingsManager myBindings = new BindingsManager();
  private final List<ActionListener> myListeners = Lists.newArrayListWithExpectedSize(1);

  private final JTextField myTextField;
  private final JComboBox myFontCombo;

  public TextAssetEditor() {
    super(ProportionalLayout.fromString("50px,180px"));

    myTextField = new JTextField();

    // TODO: JDK8 - We currently rely on JComboBox because of JDK6 restrictions
    //noinspection UndesirableClassUsage
    myFontCombo = new JComboBox();

    add(myTextField, new ProportionalLayout.Constraint(0, 0));
    add(myFontCombo, new ProportionalLayout.Constraint(0, 1));

    myBindings.bindTwoWay(new TextProperty(myTextField), myTextAsset.text());

    List<String> fontFamilies = TextAsset.getAllFontFamilies();
    for (String fontFamily : fontFamilies) {
      // We are on JDK6 and have no choice but to use the unchecked JComboBox
      //noinspection unchecked
      myFontCombo.addItem(fontFamily);
    }
    final String initialFont = myTextAsset.fontFamily().get();
    myFontCombo.setSelectedItem(initialFont);

    SelectedItemProperty<String> selectedFont = new SelectedItemProperty<String>(myFontCombo);
    myBindings.bind(myTextAsset.fontFamily(), new AsValueExpression<String>(selectedFont));

    InvalidationListener onTextChanged = new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        ActionEvent e = new ActionEvent(TextAssetEditor.this, ActionEvent.ACTION_PERFORMED, null);
        for (ActionListener listener : myListeners) {
          listener.actionPerformed(e);
        }
      }
    };

    myTextAsset.text().addListener(onTextChanged);
    myTextAsset.fontFamily().addListener(onTextChanged);
  }

  @NotNull
  @Override
  public BaseAsset getAsset() {
    return myTextAsset;
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
