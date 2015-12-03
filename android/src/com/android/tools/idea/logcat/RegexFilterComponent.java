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
package com.android.tools.idea.logcat;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.LightColors;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.EventListener;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class RegexFilterComponent extends FilterComponent {
  public interface Listener extends EventListener {
    void filterChanged(RegexFilterComponent filter);
  }

  public static final class Compiled {
    @Nullable private Pattern pattern;
    private String error = null;

    public Compiled() {
    }

    public Compiled(String filter, boolean isRegex) {
      compile(filter, isRegex);
    }

    public final void compile(String filter, boolean isRegex) {
      pattern = null;
      error = null;
      if (StringUtil.isNotEmpty(filter)) {
        int flags = Pattern.CASE_INSENSITIVE;
        if (StringUtil.hasUpperCaseChar(filter) && StringUtil.hasLowerCaseChar(filter)) {
          // mixed case, so do the match case sensitive
          flags = 0;
        }
        if (isRegex) {
          try {
            pattern = Pattern.compile(filter, flags);
          }
          catch (PatternSyntaxException e) {
            error = e.getMessage();
            assert (error != null);
          }
        }
        if (pattern == null) {
          pattern = Pattern.compile(Pattern.quote(filter), flags);
        }
      }
    }
  }

  private static final String REGEX = "Regex";

  @NotNull private final EventDispatcher<Listener> myMulticaster = EventDispatcher.create(Listener.class);
  @NotNull private final Compiled myCompiled = new Compiled();
  @NotNull private final JCheckBox myCheckBox;
  private boolean myIsRegex = true;

  public RegexFilterComponent(@NonNls String propertyName, int historySize) {
    super(propertyName, historySize, true);
    final CheckboxAction regexToggle = new CheckboxAction(REGEX) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return isRegex();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        setIsRegex(state);
      }
    };

    Component component = regexToggle.createCustomComponent(regexToggle.getTemplatePresentation());
    assert (component instanceof JCheckBox);
    myCheckBox = (JCheckBox)component;
    add(myCheckBox, BorderLayout.EAST);
    myCheckBox.setSelected(myIsRegex);
  }

  @Nullable
  public String getParseError() {
    return myCompiled.error;
  }

  public boolean isRegex() {
    return myIsRegex;
  }

  public void setIsRegex(boolean value) {
    myIsRegex = value;
    myCheckBox.setSelected(myIsRegex);
    filter();
  }

  public Pattern getPattern() {
    return myCompiled.pattern;
  }

  public void addRegexListener(Listener listener) {
    myMulticaster.addListener(listener);
  }

  public static Compiled compile(String filter, boolean isRegex) {
    return new Compiled(filter, isRegex);
  }

  public static Pattern pattern(String filter, boolean isRegex) {
    return new Compiled(filter, isRegex).pattern;
  }

  @Override
  public void filter() {
    myCompiled.compile(getFilter(), isRegex());
    getTextEditor().setBackground(myCompiled.error != null ? LightColors.RED : UIUtil.getTextFieldBackground());
    myMulticaster.getMulticaster().filterChanged(this);
  }
}
