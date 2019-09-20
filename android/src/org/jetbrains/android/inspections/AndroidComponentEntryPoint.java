// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.inspections;

import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

public class AndroidComponentEntryPoint extends EntryPoint {
  public boolean ADD_ANDROID_COMPONENTS_TO_ENTRIES = true;

  @NotNull
  @Override
  public String getDisplayName() {
    return AndroidBundle.message("android.component.entry.point");
  }

  @Override
  public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
    return isEntryPoint(psiElement);
  }

  @Override
  public boolean isEntryPoint(@NotNull PsiElement psiElement) {
    return ADD_ANDROID_COMPONENTS_TO_ENTRIES &&
           psiElement instanceof PsiClass &&
           AndroidUtils.isAndroidComponent((PsiClass)psiElement);
  }

  @Override
  public boolean isSelected() {
    return ADD_ANDROID_COMPONENTS_TO_ENTRIES;
  }

  @Override
  public void setSelected(boolean selected) {
    ADD_ANDROID_COMPONENTS_TO_ENTRIES = selected;
  }

  @Override
  public void readExternal(@NotNull Element element) {
    XmlSerializer.deserializeInto(element, this);
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    XmlSerializer.serializeObjectInto(this, element);
  }
}
