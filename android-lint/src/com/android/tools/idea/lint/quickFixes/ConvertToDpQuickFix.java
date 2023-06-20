/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.quickFixes;

import com.android.resources.Density;
import com.android.tools.idea.lint.AndroidLintBundle;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.ObjectUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConvertToDpQuickFix extends DefaultLintQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.lint.quickFixes.ConvertToDpQuickFix");
  private static final Pattern PX_ATTR_VALUE_PATTERN = Pattern.compile("(\\d+)px");

  private static int ourPrevDpi = Density.DEFAULT_DENSITY;

  public ConvertToDpQuickFix() {
    super(AndroidLintBundle.message("android.lint.fix.convert.to.dp"));
  }

  @Override
  public boolean startInWriteAction() {
    return false; // Cannot start the write action until after the modal dialog is shown.
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    if (context instanceof AndroidQuickfixContexts.BatchContext) {
      return;
    }
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class);
    final List<Density> densities = new ArrayList<>();

    for (Density density : Density.values()) {
      if (density.isValidValueForDevice()) {
        densities.add(density);
      }
    }

    final String[] densityPresentableNames = new String[densities.size()];

    String defaultValue = null;
    String initialValue = null;

    for (int i = 0; i < densities.size(); i++) {
      final Density density = densities.get(i);
      densityPresentableNames[i] = getLabelForDensity(density);

      int dpi = density.getDpiValue();
      if (dpi == ourPrevDpi) {
        initialValue = densityPresentableNames[i];
      }
      else if (dpi == Density.DEFAULT_DENSITY) {
        defaultValue = densityPresentableNames[i];
      }
    }

    if (initialValue == null) {
      initialValue = defaultValue;
    }
    if (initialValue == null) {
      return;
    }

    final int dpi;

    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || context instanceof AndroidQuickfixContexts.EditorPreviewContext) {
      dpi = Density.DEFAULT_DENSITY;
    }
    else {
      final int selectedIndex = Messages
        .showChooseDialog("What is the screen density the current px value works with?", "Choose density", densityPresentableNames,
                          initialValue, null);
      if (selectedIndex < 0) {
        return;
      }
      dpi = densities.get(selectedIndex).getDpiValue();
    }

    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourPrevDpi = dpi;

    Runnable runnable = () -> {
      for (XmlAttribute attribute : tag.getAttributes()) {
        final String value = attribute.getValue();

        if (value != null && value.endsWith("px")) {
          final String newValue = convertToDp(value, dpi);
          if (newValue != null) {
            attribute.setValue(newValue);
          }
        }
      }
      final XmlTagValue tagValueElement = tag.getValue();
      final String tagValue = tagValueElement.getText();

      if (tagValue.endsWith("px")) {
        final String newValue = convertToDp(tagValue, dpi);

        if (newValue != null) {
          tagValueElement.setText(newValue);
        }
      }
    };

    if (context instanceof AndroidQuickfixContexts.EditorPreviewContext) {
      runnable.run();
    } else {
      application.runWriteAction(runnable);
    }
  }

  private static String convertToDp(String value, int dpi) {
    String newValue = null;
    final Matcher matcher = PX_ATTR_VALUE_PATTERN.matcher(value);

    if (matcher.matches()) {
      final String numberString = matcher.group(1);
      try {
        final int px = Integer.parseInt(numberString);
        final int dp = px * 160 / dpi;
        newValue = Integer.toString(dp) + "dp";
      }
      catch (NumberFormatException nufe) {
        LOG.error(nufe);
      }
    }
    return newValue;
  }

  @NotNull
  private static String getLabelForDensity(@NotNull Density density) {
    return String.format(Locale.US, "%1$s (%2$d)", density.getShortDisplayValue(), density.getDpiValue());
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return contextType != AndroidQuickfixContexts.BatchContext.TYPE &&
           PsiTreeUtil.getParentOfType(startElement, XmlTag.class) != null;
  }

  @Override
  public @Nullable IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element != null) {
      apply(element, element, new AndroidQuickfixContexts.EditorPreviewContext(editor, file));
      return IntentionPreviewInfo.DIFF;
    }
    return null;
  }
}
