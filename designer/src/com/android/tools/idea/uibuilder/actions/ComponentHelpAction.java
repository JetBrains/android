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

import static com.android.SdkConstants.ANDROIDX_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_SUPPORT_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_VIEW_PKG;
import static com.android.SdkConstants.ANDROID_WEBKIT_PKG;
import static com.android.SdkConstants.ANDROID_WIDGET_PREFIX;
import static com.android.SdkConstants.GOOGLE_SUPPORT_ARTIFACT_PREFIX;
import static com.android.SdkConstants.TAG_GROUP;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_MENU;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.actions.ExternalJavaDocAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;
import javax.swing.KeyStroke;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ComponentHelpAction extends AnAction {
  public static final String ANDROID_CAST_ITEM = "android.support.item";
  public static final String ANDROIDX_CAST_ITEM = "androidx.item";

  private static final String ANDROID_REFERENCE_PREFIX = "https://developer.android.com/reference/";
  private static final String GOOGLE_REFERENCE_PREFIX = "https://developers.google.com/android/reference/";

  private final Supplier<String> myTagNameSupplier;

  public ComponentHelpAction(@NotNull Supplier<String> tagNameSupplier) {
    super("Android Documentation");
    myTagNameSupplier = tagNameSupplier;
    setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK)));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) {
      return;
    }
    String tagName = myTagNameSupplier.get();
    if (tagName == null) {
      return;
    }
    String className = findClassName(project, tagName);
    if (className == null) {
      return;
    }
    String reference = computeReferenceFromClassName(className);
    if (reference != null) {
      BrowserUtil.browse(reference);
      return;
    }
    PsiClass psiClass = findClassByClassName(project, className);
    if (psiClass == null) {
      return;
    }
    ExternalJavaDocAction.showExternalJavadoc(psiClass, null, null, event.getDataContext());
  }

  @Nullable
  private String findClassName(@NotNull Project project, @NotNull String tagName) {
    switch (tagName) {
      case TAG_ITEM, ANDROID_CAST_ITEM, ANDROIDX_CAST_ITEM -> {
        return ANDROID_VIEW_PKG + "MenuItem";
      }
      case TAG_GROUP, TAG_MENU -> {
        return ANDROID_VIEW_PKG + "Menu";
      }
    }
    if (tagName.indexOf('.') != -1) {
      return tagName;
    }
    if (findClassByClassName(project, ANDROID_WIDGET_PREFIX + tagName) != null) {
      return ANDROID_WIDGET_PREFIX + tagName;
    }
    if (findClassByClassName(project, ANDROID_VIEW_PKG + tagName) != null) {
      return ANDROID_VIEW_PKG + tagName;
    }
    if (findClassByClassName(project, ANDROID_WEBKIT_PKG + tagName) != null) {
      return ANDROID_WEBKIT_PKG + tagName;
    }
    return null;
  }

  @Nullable
  private PsiClass findClassByClassName(@NotNull Project project, @NotNull String fullyQualifiedClassName) {
    JavaPsiFacade javaFacade = JavaPsiFacade.getInstance(project);
    return javaFacade.findClass(fullyQualifiedClassName, GlobalSearchScope.allScope(project));
  }

  @Nullable
  private static String computeReferenceFromClassName(@NotNull String className) {
    if (className.startsWith(ANDROID_PKG_PREFIX) ||
        className.startsWith(ANDROID_SUPPORT_PKG_PREFIX) ||
        className.startsWith(ANDROIDX_PKG_PREFIX)) {
      return ANDROID_REFERENCE_PREFIX + className.replace('.', '/') + ".html";
    }
    else if (className.startsWith(GOOGLE_SUPPORT_ARTIFACT_PREFIX)) {
      return GOOGLE_REFERENCE_PREFIX + className.replace('.', '/');
    }
    return null;
  }
}
