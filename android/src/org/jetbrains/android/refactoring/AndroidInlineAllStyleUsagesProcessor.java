// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.refactoring;

import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.MultiMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.dom.resources.ResourceNameConverter;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AndroidInlineAllStyleUsagesProcessor extends BaseRefactoringProcessor {
  private final PsiElement myStyleElement;
  private final String myStyleName;
  private final Map<AndroidAttributeInfo, String> myAttributeValues;
  private final StyleRefData myParentStyleRef;
  private final XmlTag myStyleTag;
  private final AndroidInlineTestConfig myTestConfig;

  protected AndroidInlineAllStyleUsagesProcessor(@NotNull Project project,
                                                 @NotNull PsiElement styleElement,
                                                 @NotNull XmlTag styleTag,
                                                 @NotNull String styleName,
                                                 @NotNull Map<AndroidAttributeInfo, String> attributeValues,
                                                 @Nullable StyleRefData parentStyleRef,
                                                 @Nullable AndroidInlineTestConfig config) {
    super(project);
    myStyleElement = styleElement;
    myStyleTag = styleTag;
    myStyleName = styleName;
    myAttributeValues = attributeValues;
    myParentStyleRef = parentStyleRef;
    myTestConfig = config;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new UsageViewDescriptor() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return new PsiElement[]{myStyleElement};
      }

      @Override
      public String getCodeReferencesText(int usagesCount, int filesCount) {
        return "References to be inlined" + UsageViewBundle.getReferencesString(usagesCount, filesCount);
      }

      @Override
      public String getProcessedElementsHeader() {
        return "Style to inline";
      }
    };
  }

  @NotNull
  @Override
  public UsageInfo[] findUsages() {
    final Set<UsageInfo> usages = new HashSet<UsageInfo>();
    AndroidInlineUtil.addReferences(myStyleElement, usages);

    for (PsiField field : IdeResourcesUtil.findResourceFieldsForValueResource(myStyleTag, false)) {
      AndroidInlineUtil.addReferences(field, usages);
    }
    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }


  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    final List<StyleUsageData> inlineInfos = new ArrayList<StyleUsageData>();

    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element == null) continue;

      final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      StyleUsageData usageData = tag != null ? AndroidInlineUtil.getStyleUsageData(tag) : null;

      if (usageData != null && usageData.getReference().computeTargetElements().length == 1) {
        inlineInfos.add(usageData);
      }
    }

    for (StyleUsageData info : inlineInfos) {
      info.inline(myAttributeValues, myParentStyleRef);
    }
    myStyleTag.delete();
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usages = refUsages.get();
    final MultiMap<PsiElement, String> conflicts = detectConflicts(usages);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myTestConfig.setConflicts(conflicts);
      return true;
    }
    return showConflicts(conflicts, usages);
  }

  private static MultiMap<PsiElement, String> detectConflicts(UsageInfo[] usages) {
    final List<PsiElement> nonXmlUsages = new ArrayList<PsiElement>();
    final List<PsiElement> unsupportedUsages = new ArrayList<PsiElement>();
    final List<PsiElement> unambiguousUsages = new ArrayList<PsiElement>();
    final List<PsiElement> implicitlyInherited = new ArrayList<PsiElement>();

    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element == null) continue;

      if (element.getLanguage() != XMLLanguage.INSTANCE) {
        nonXmlUsages.add(element);
        continue;
      }
      final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      StyleUsageData usageData = tag != null ? AndroidInlineUtil.getStyleUsageData(tag) : null;

      if (usageData == null) {
        if (usage.getReference() instanceof ResourceNameConverter.MyParentStyleReference) {
          implicitlyInherited.add(element);
        }
        else {
          unsupportedUsages.add(element);
        }
        continue;
      }

      if (usageData.getReference().computeTargetElements().length > 1) {
        unambiguousUsages.add(element);
      }
    }
    return AndroidInlineUtil.buildConflicts(nonXmlUsages, unambiguousUsages, unsupportedUsages, implicitlyInherited);
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return AndroidBundle.message("android.inline.style.command.name", myStyleName);
  }

  @NotNull
  @Override
  protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
    // do it because the refactoring can be invoked from UI designer
    return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
  }
}
