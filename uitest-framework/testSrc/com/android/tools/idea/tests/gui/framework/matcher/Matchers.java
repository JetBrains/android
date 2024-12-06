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
package com.android.tools.idea.tests.gui.framework.matcher;

import com.android.tools.adtui.TextAccessors;
import com.intellij.BundleBase;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.ui.TextAccessor;
import java.awt.Component;
import java.awt.Dialog;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Utility methods for {@link org.fest.swing.core.ComponentMatcher matchers}. */
public final class Matchers {
  private Matchers() {}

  @NotNull
  public static <T extends JComponent> FluentMatcher<T> byText(Class<T> componentType, @NotNull String text) {
    return new FluentMatcher<T>(componentType) {
      @Override
      protected boolean isMatching(@NotNull T component) {
        // Appearance of MNEMONIC can be platform-dependent, so be careful modifying this.
        TextAccessor textAccessor = TextAccessors.getTextAccessor(component);
        String componentText = textAccessor == null ? "" : textAccessor.getText();
        componentText = componentText == null ? "" : componentText.replaceAll(Character.toString(BundleBase.MNEMONIC), "");
        return text.equals(componentText);
      }
    };
  }

  @NotNull
  public static <T extends Dialog> FluentMatcher<T> byTitle(Class<T> dialogType, @NotNull String title) {
    return new FluentMatcher<T>(dialogType) {
      @Override
      protected boolean isMatching(@NotNull T dialog) {
        return title.equals(dialog.getTitle());
      }
    };
  }

  @NotNull
  public static <T extends Component> FluentMatcher<T> byType(Class<T> componentType) {
    return new FluentMatcher<T>(componentType) {
      @Override
      protected boolean isMatching(@NotNull T component) {
        return true;
      }
    };
  }

  @NotNull
  public static <T extends Component> FluentMatcher<T> byName(Class<T> componentType, @NotNull String name) {
    return new FluentMatcher<T>(componentType) {
      @Override
      protected boolean isMatching(@NotNull T component) {
        return name.equals(component.getName());
      }
    };
  }

  @NotNull
  public static <T extends JLabel> FluentMatcher<T> byIcon(Class<T> componentType, @Nullable Icon icon) {
    return new FluentMatcher<T>(componentType) {
      @Override
      protected boolean isMatching(@NotNull T component) {
        return Objects.equals(icon, component.getIcon());
      }
    };
  }

  @NotNull
  public static FluentMatcher<ActionButton> buttonWithIcon(@Nullable Icon icon) {
    return new FluentMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton component) {
        return Objects.equals(icon, component.getIcon());
      }
    };
  }

  @NotNull
  public static <T extends JComponent> FluentMatcher<T> byTooltip(Class<T> componentType, @NotNull String tooltip) {
    return new FluentMatcher<T>(componentType) {
      @Override
      protected boolean isMatching(@NotNull T component) {
        return tooltip.equals(component.getToolTipText());
      }
    };
  }

  @NotNull
  public static <T extends JComponent> FluentMatcher<T> byClientProperty(Class<T> componentType,
                                                                         @NotNull Object key,
                                                                         @NotNull Class<?> valueClass) {
    return new FluentMatcher<T>(componentType) {
      @Override
      protected boolean isMatching(@NotNull T component) {
        return valueClass.isInstance(component.getClientProperty(key));
      }
    };
  }
}
