/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers;

import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.intellij.openapi.keymap.KeymapUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;

public class ProfilerAction implements ContextMenuItem {
  @NotNull private final Supplier<String> myText;
  @NotNull private final Runnable myActionRunnable;
  @NotNull private final BooleanSupplier myEnableBooleanSupplier;
  @NotNull private final KeyStroke[] myKeyStrokes;
  @Nullable private final Supplier<Icon> myIcon;

  private ProfilerAction(Builder builder) {
    myText = builder.myText;
    myActionRunnable = builder.myActionRunnable;
    myEnableBooleanSupplier = builder.myEnableBooleanSupplier;
    myKeyStrokes = builder.myKeyStrokes;
    myIcon = builder.myIcon;
    JComponent containerComponent = builder.myContainerComponent;

    // Put KeyStrokes into InputMap/ActionMap to activate the shortcuts.
    // The shortcuts are active when {@code myContainerComponent} is an ancestor
    // of the focused component or is itself the focused component.
    if (containerComponent == null || myKeyStrokes.length == 0) {
      return;
    }
    InputMap inputMap = containerComponent.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    ActionMap actionMap = containerComponent.getActionMap();
    String label = getDefaultToolTipText();
    for (KeyStroke keyStroke : myKeyStrokes) {
      inputMap.put(keyStroke, label);
    }
    actionMap.put(label, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (isEnabled()) {
          run();
        }
      }
    });
  }

  @NotNull
  @Override
  public String getText() {
    return myText.get();
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return myIcon == null ? null : myIcon.get();
  }

  @Override
  public boolean isEnabled() {
    return myEnableBooleanSupplier.getAsBoolean();
  }

  @Override
  @NotNull
  public KeyStroke[] getKeyStrokes() {
    return myKeyStrokes;
  }

  @Override
  public void run() {
    myActionRunnable.run();
  }

  public String getDefaultToolTipText() {
    String text = myText.get();
    if (myKeyStrokes.length != 0) {
      return text + " (" + KeymapUtil.getKeystrokeText(myKeyStrokes[0]) + ")";
    }
    return text;
  }

  public static class Builder {
    @NotNull private Runnable myActionRunnable;
    @NotNull private BooleanSupplier myEnableBooleanSupplier;
    @NotNull private Supplier<String> myText;
    @NotNull private KeyStroke[] myKeyStrokes;
    @Nullable private Supplier<Icon> myIcon;
    @Nullable private JComponent myContainerComponent;

    /**
     * Convenience constructor for actions with fixed text.
     */
    public Builder(@NotNull String text) {
      this(() -> text);
    }

    public Builder(@NotNull Supplier<String> text) {
      myText = text;
      myActionRunnable = () -> {
      };
      myEnableBooleanSupplier = () -> true;
      myKeyStrokes = new KeyStroke[0];
    }

    public Builder setActionRunnable(@NotNull Runnable actionRunnable) {
      myActionRunnable = actionRunnable;
      return this;
    }

    public Builder setEnableBooleanSupplier(@NotNull BooleanSupplier enable) {
      myEnableBooleanSupplier = enable;
      return this;
    }

    public Builder setKeyStrokes(@NotNull KeyStroke... keyStrokes) {
      myKeyStrokes = keyStrokes;
      return this;
    }

    /**
     * Convenience setter for actions with fixed icon.
     */
    public Builder setIcon(@Nullable Icon icon) {
      return setIcon(() -> icon);
    }

    public Builder setIcon(@Nullable Supplier<Icon> icon) {
      myIcon = icon;
      return this;
    }

    public Builder setContainerComponent(@Nullable JComponent containerComponent) {
      myContainerComponent = containerComponent;
      return this;
    }

    public ProfilerAction build() {
      return new ProfilerAction(this);
    }
  }
}
