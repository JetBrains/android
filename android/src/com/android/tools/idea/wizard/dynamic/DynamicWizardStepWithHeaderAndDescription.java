/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard.dynamic;

import com.android.tools.idea.wizard.WizardConstants;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

import static com.android.tools.idea.wizard.WizardConstants.STUDIO_WIZARD_INSET_SIZE;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

/**
 * Base class for wizard pages with title and description labels underneath
 * the standard wizard banner.
 */
public abstract class DynamicWizardStepWithHeaderAndDescription extends DynamicWizardStepWithDescription implements Disposable {
  protected static final ScopedStateStore.Key<String> KEY_TITLE =
    createKey(DynamicWizardStepWithHeaderAndDescription.class + ".title", ScopedStateStore.Scope.STEP, String.class);
  protected static final ScopedStateStore.Key<String> KEY_MESSAGE =
    createKey(DynamicWizardStepWithHeaderAndDescription.class + ".message", ScopedStateStore.Scope.STEP, String.class);
  @NotNull private final String myTitle;
  @Nullable private final String myMessage;
  private JBLabel myTitleLabel;
  private JBLabel myMessageLabel;
  private JPanel myHeaderPane;

  public DynamicWizardStepWithHeaderAndDescription(@NotNull String title, @Nullable String message, @Nullable Disposable parentDisposable) {
    super(parentDisposable);
    myTitle = title;
    myMessage = message;
    int fontHeight = myMessageLabel.getFont().getSize();
    myTitleLabel.setBorder(BorderFactory.createEmptyBorder(fontHeight, 0, fontHeight, 0));
    Insets topSegmentInsets = new Insets(WizardConstants.STUDIO_WIZARD_TOP_INSET, STUDIO_WIZARD_INSET_SIZE, 0, STUDIO_WIZARD_INSET_SIZE);
    myHeaderPane.setBorder(new EmptyBorder(topSegmentInsets));
    Font font = myTitleLabel.getFont();
    if (font == null) {
      font = UIUtil.getLabelFont();
    }
    font = new Font(font.getName(), font.getStyle() | Font.BOLD, font.getSize() + 4);
    myTitleLabel.setFont(font);
  }

  @NotNull
  protected String getTitle() {
    return myTitle;
  }

  @NotNull
  @Override
  protected JPanel createStepBody() {
    JPanel body = super.createStepBody();
    body.add(myHeaderPane, BorderLayout.NORTH);
    return body;
  }

  @Override
  public void init() {
    super.init();
    myState.put(KEY_TITLE, myTitle);
    myState.put(KEY_MESSAGE, myMessage);
    register(KEY_TITLE, myTitleLabel, new ComponentBinding<String, JBLabel>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull JBLabel component) {
        component.setText(newValue);
      }
    });
    register(KEY_MESSAGE, myMessageLabel, new ComponentBinding<String, JLabel>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull JLabel component) {
        component.setVisible(!StringUtil.isEmpty(newValue));
        component.setText(toHtml(newValue));
      }
    });
  }

  @NotNull
  protected WizardStepHeaderSettings getStepHeader() {
    return WizardStepHeaderSettings.createProductHeader(myTitle);
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return getStepHeader().title;
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return getStepHeader().description;
  }

  @NotNull
  @Override
  protected JBColor getHeaderColor() {
    JBColor color = getStepHeader().color;
    return color == null ? super.getHeaderColor() : color;
  }

  @Nullable
  @Override
  protected Icon getStepIcon() {
    return getStepHeader().stepIcon;
  }

  public static final class WizardStepHeaderSettings {
    public static final String PRODUCT_DESCRIPTION = "Android Studio";

    @NotNull public final String title;
    @Nullable public final String description;
    @Nullable public final Icon stepIcon;
    @Nullable public final JBColor color;

    private WizardStepHeaderSettings(@NotNull String title, @Nullable String description, @Nullable Icon stepIcon, @Nullable JBColor color) {
      this.title = title;
      this.description = description;
      this.stepIcon = stepIcon;
      this.color = color;
    }

    @NotNull
    public static WizardStepHeaderSettings createCustomColorHeader(@NotNull JBColor color, @NotNull String title) {
      return new WizardStepHeaderSettings(title, PRODUCT_DESCRIPTION, null, color);
    }

    @NotNull
    public static WizardStepHeaderSettings createProductHeader(@NotNull String title) {
      return new WizardStepHeaderSettings(title, PRODUCT_DESCRIPTION, null, null);
    }

    @NotNull
    public static WizardStepHeaderSettings createTitleOnlyHeader(@NotNull String title) {
      return new WizardStepHeaderSettings(title, null, null, null);
    }

    @NotNull
    public static WizardStepHeaderSettings createTitleAndIconHeader(@NotNull String title, @NotNull Icon icon) {
      return new WizardStepHeaderSettings(title, null, icon, null);
    }
  }
}
