/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions;

import com.android.tools.idea.uibuilder.util.JavaDocViewer;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;

import static com.android.SdkConstants.*;

public class ComponentHelpAction extends AnAction {
  private static final String DEFAULT_ANDROID_REFERENCE_PREFIX = "https://developer.android.com/reference/";

  private final Project myProject;
  private final Supplier<String> myTagNameSupplier;

  public ComponentHelpAction(@NotNull Project project,
                             @NotNull Supplier<String> tagNameSupplier) {
    super("Android Documentation");
    myProject = project;
    myTagNameSupplier = tagNameSupplier;
    setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK)));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    String tagName = myTagNameSupplier.get();
    if (tagName == null) {
      return;
    }
    String className = findClassName(tagName);
    if (className == null) {
      return;
    }
    String reference = computeReferenceFromClassName(className);
    if (reference != null) {
      BrowserUtil.browse(reference);
      return;
    }
    PsiClass psiClass = findClassByClassName(className);
    if (psiClass == null) {
      return;
    }
    JavaDocViewer.getInstance().showExternalJavaDoc(psiClass, event.getDataContext());
  }

  @Nullable
  private String findClassName(@NotNull String tagName) {
    if (tagName.indexOf('.') != -1) {
      return tagName;
    }
    if (findClassByClassName(ANDROID_WIDGET_PREFIX + tagName) != null) {
      return ANDROID_WIDGET_PREFIX + tagName;
    }
    if (findClassByClassName(ANDROID_VIEW_PKG + tagName) != null) {
      return ANDROID_VIEW_PKG + tagName;
    }
    if (findClassByClassName(ANDROID_WEBKIT_PKG + tagName) != null) {
      return ANDROID_WEBKIT_PKG + tagName;
    }
    return null;
  }

  @Nullable
  private PsiClass findClassByClassName(@NotNull String fullyQualifiedClassName) {
    JavaPsiFacade javaFacade = JavaPsiFacade.getInstance(myProject);
    return javaFacade.findClass(fullyQualifiedClassName, GlobalSearchScope.allScope(myProject));
  }

  @Nullable
  private static String computeReferenceFromClassName(@NotNull String className) {
    if (className.startsWith(ANDROID_PKG_PREFIX) ||
        className.startsWith(ANDROID_SUPPORT_PKG_PREFIX) ||
        className.startsWith(ANDROIDX_PKG_PREFIX) ||
        className.startsWith(GOOGLE_SUPPORT_ARTIFACT_PREFIX)) {
      return DEFAULT_ANDROID_REFERENCE_PREFIX + StringUtil.replaceChar(className, '.', '/') + ".html";
    }
    return null;
  }
}
