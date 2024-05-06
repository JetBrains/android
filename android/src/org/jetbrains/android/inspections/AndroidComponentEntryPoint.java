// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.inspections;

import static com.android.SdkConstants.CLASS_ACTION_PROVIDER;
import static com.android.SdkConstants.CLASS_ACTIVITY;
import static com.android.SdkConstants.CLASS_APPLICATION;
import static com.android.SdkConstants.CLASS_BACKUP_AGENT;
import static com.android.SdkConstants.CLASS_BROADCASTRECEIVER;
import static com.android.SdkConstants.CLASS_CONTENTPROVIDER;
import static com.android.SdkConstants.CLASS_CONTEXT;
import static com.android.SdkConstants.CLASS_FRAGMENT;
import static com.android.SdkConstants.CLASS_PARCELABLE;
import static com.android.SdkConstants.CLASS_SERVICE;
import static com.android.AndroidXConstants.CLASS_V4_FRAGMENT;
import static com.android.SdkConstants.CLASS_VIEW;

import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.InheritanceUtil;
import java.util.Arrays;
import org.jdom.Element;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Certain classes (e.g. Activities, Fragments) are instantiated by the Android framework via reflection.
 * This extension marks those classes as "entry points", thereby suppressing both AccessCanBeTightenedInspection and
 * UnusedDeclarationInspection.
 *
 * TODO(b/203560143): Avoid suppressing UnusedDeclarationInspection (instead check for references from XML).
 */
public class AndroidComponentEntryPoint extends EntryPoint {
  public boolean ADD_ANDROID_COMPONENTS_TO_ENTRIES = true;

  /** Common base classes which are instantiated reflectively by the Android framework. Not an authoritative list. */
  private final static String[] ANDROID_ENTRY_CLASSES = {
    CLASS_APPLICATION,
    CLASS_ACTIVITY,
    CLASS_SERVICE,
    CLASS_FRAGMENT,
    CLASS_V4_FRAGMENT.oldName(),
    CLASS_V4_FRAGMENT.newName(),
    CLASS_CONTENTPROVIDER,
    CLASS_BROADCASTRECEIVER,
    CLASS_VIEW,
    CLASS_ACTION_PROVIDER,
    CLASS_PARCELABLE,
    CLASS_BACKUP_AGENT,
    CLASS_CONTEXT
  };

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
           Arrays.stream(ANDROID_ENTRY_CLASSES).anyMatch(baseClass -> InheritanceUtil.isInheritor((PsiClass)psiElement, baseClass));
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
