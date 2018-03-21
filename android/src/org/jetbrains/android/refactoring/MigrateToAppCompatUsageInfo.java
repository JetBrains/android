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
package org.jetbrains.android.refactoring;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.AttributeMigrationEntry;
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.AttributeValueMigrationEntry;
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.ReplaceMethodCallMigrationEntry;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class MigrateToAppCompatUsageInfo extends UsageInfo {

  public MigrateToAppCompatUsageInfo(@NotNull PsiReference reference) {
    super(reference);
  }

  public MigrateToAppCompatUsageInfo(@NotNull PsiElement element) {
    super(element);
  }

  /**
   * Migrate the current reference to the new usage.
   *
   * @param migration PsiMigration to lookup classes and packages.
   * @return the modified reference after a migration or null.
   */
  @Nullable
  public abstract PsiElement applyChange(@NotNull PsiMigration migration);

  /**
   * UsageInfo specific for changing a method usage
   * e.g. Activity#getFragmentManager to AppCompatActivity#getSupportFragmentManager
   */
  static class ChangeMethodUsageInfo extends MigrateToAppCompatUsageInfo {
    final AppCompatMigrationEntry.MethodMigrationEntry myEntry;

    public ChangeMethodUsageInfo(PsiReference ref, @NotNull AppCompatMigrationEntry.MethodMigrationEntry entry) {
      super(ref);
      myEntry = entry;
    }

    @Override
    public PsiElement applyChange(@NotNull PsiMigration migration) {
      PsiElement element = getElement();
      if (element instanceof PsiReference && isValid()) {
        PsiReference reference = (PsiReference)element;
        String newName = myEntry.myNewMethodName;
        // Handle direct method references example getSupportActionBar()
        return reference.handleElementRename(newName);
      }
      return null;
    }
  }

  /**
   * UsageInfo specific for migrating a class
   */
  static class ClassMigrationUsageInfo extends MigrateToAppCompatUsageInfo {
    final AppCompatMigrationEntry.ClassMigrationEntry mapEntry;

    public ClassMigrationUsageInfo(@NotNull UsageInfo info, @NotNull AppCompatMigrationEntry.ClassMigrationEntry mapEntry) {
      //noinspection ConstantConditions
      super(info.getElement());
      this.mapEntry = mapEntry;
    }

    @Override
    public PsiElement applyChange(@NotNull PsiMigration psiMigration) {
      // Here we need to either find or create the class so that imports/class names can be resolved.
      PsiClass aClass = MigrateToAppCompatUtil.findOrCreateClass(getProject(), psiMigration, mapEntry.myNewName);

      PsiElement element = getElement();
      if (element == null || !element.isValid()) {
        return element;
      }
      if (element instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
        return referenceElement.bindToElement(aClass);
      }
      return null;
    }
  }

  /**
   * Change a style attribute or body (within styles.xml)
   */
  static class ChangeStyleUsageInfo extends MigrateToAppCompatUsageInfo {
    protected final String myFromValue;
    protected final String myToValue;

    ChangeStyleUsageInfo(@NotNull PsiElement element, @NotNull String fromValue, @NotNull String toValue) {
      super(element);
      myFromValue = fromValue;
      myToValue = toValue;
    }

    @Override
    public PsiElement applyChange(@NotNull PsiMigration migration) {
      // This can either be an attribute <item name="android:windowNoTitle" ..
      // or the body text of the item such as <item ..>?android:itemSelectableBackground</item>
      PsiElement element = getElement();
      XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
      if (tag == null) {
        return null;
      }

      XmlAttribute attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class, false);
      XmlAttributeValue attributeValue = attribute == null ? null : attribute.getValueElement();
      if (attribute != null && attributeValue != null) {
        tag.setAttribute(attribute.getName(), myToValue);
      }
      else {
        XmlTagValue tagValue = tag.getValue();
        tagValue.setText(myToValue);
      }
      return null;
    }
  }

  static class ChangeThemeUsageInfo extends ChangeStyleUsageInfo {

    ChangeThemeUsageInfo(@NotNull XmlAttributeValue element, @NotNull String fromValue, @NotNull String toValue) {
      super(element, fromValue, toValue);
    }

    @Override
    public PsiElement applyChange(@NotNull PsiMigration migration) {
      XmlAttributeValue attributeValue = PsiTreeUtil.getParentOfType(getElement(), XmlAttributeValue.class, false);
      if (attributeValue == null) {
        return null;
      }
      String value = attributeValue.getValue();

      XmlAttribute attribute = PsiTreeUtil.getParentOfType(getElement(), XmlAttribute.class, true);
      if (attribute != null && value != null && value.equals(myFromValue)) {
        attribute.setValue(myToValue);
      }
      return null;
    }
  }

  static class ChangeCustomViewUsageInfo extends MigrateToAppCompatUsageInfo {

    private final String mySuggestedSuperClass;

    public ChangeCustomViewUsageInfo(@NotNull PsiElement element, @NotNull String suggestedSuperClass) {
      super(element);
      mySuggestedSuperClass = suggestedSuperClass;
    }

    @Override
    public PsiElement applyChange(@NotNull PsiMigration migration) {
      PsiClass psiClass = MigrateToAppCompatUtil.findOrCreateClass(getProject(), migration, mySuggestedSuperClass);

      PsiElement element = getElement();
      assert element != null;
      if (!element.isValid()) {
        return null;
      }
      PsiReference reference = element.getReference();
      if (reference != null) {
        return reference.bindToElement(psiClass);
      }
      return null;
    }
  }

  static class ReplaceMethodUsageInfo extends MigrateToAppCompatUsageInfo {
    private final ReplaceMethodCallMigrationEntry myEntry;

    public ReplaceMethodUsageInfo(@NotNull PsiReference element, @NotNull ReplaceMethodCallMigrationEntry entry) {
      super(element);
      myEntry = entry;
    }

    @Override
    public PsiElement applyChange(@NotNull PsiMigration psiMigration) {
      PsiElement element = getElement();
      if (!(element instanceof PsiReference) || !isValid()) {
        return null;
      }
      PsiReference reference = (PsiReference)element;

      PsiClass psiClass = MigrateToAppCompatUtil.findOrCreateClass(getProject(), psiMigration, myEntry.myNewClassName);
      String methodFragment = myEntry.myNewMethodName;

      if (!(element instanceof PsiReferenceExpression)
          || ((PsiReferenceExpression)element).getQualifierExpression() == null) {
        return null;
      }
      PsiMethodCallExpression oldMethodCall = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethodCallExpression.class);
      if (oldMethodCall == null) {
        return null;
      }
      // Get the argument list of the old call.
      PsiExpressionList argList = oldMethodCall.getArgumentList();
      PsiExpression qualifierExpression = ((PsiReferenceExpression)element).getQualifierExpression();
      PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
      String methodCallText = "Foo." + methodFragment + "()";
      PsiMethodCallExpression call = (PsiMethodCallExpression)factory.createExpressionFromText(methodCallText, null);
      PsiExpressionList newArgList = call.getArgumentList();
      PsiExpression[] prevExpressions = argList.getExpressions();
      if (prevExpressions.length == 0) {
        //noinspection ConstantConditions
        newArgList.add(qualifierExpression);
      }
      else {
        for (int i = 0; i < prevExpressions.length; i++) {
          PsiExpression arg = prevExpressions[i];
          if (myEntry.myQualifierParamIndex == i) {
            //noinspection ConstantConditions qualifierExpression is already checked above.
            newArgList.add(qualifierExpression);
          }
          newArgList.add(arg);
        }
      }

      //noinspection ConstantConditions
      ((PsiReferenceExpression)call.getMethodExpression().getQualifierExpression()).bindToElement(psiClass);

      return oldMethodCall.replace(call);
    }
  }

  static class ChangeXmlTagUsageInfo extends MigrateToAppCompatUsageInfo {
    private final AppCompatMigrationEntry.XmlTagMigrationEntry myEntry;

    public ChangeXmlTagUsageInfo(@NotNull PsiElement element,
                                 @NotNull AppCompatMigrationEntry.XmlTagMigrationEntry entry) {
      super(element);
      myEntry = entry;
    }

    @Override
    public PsiElement applyChange(@NotNull PsiMigration migration) {
      PsiElement element = getElement();
      XmlTag xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
      if (xmlTag == null || !xmlTag.getLocalName().equals(myEntry.myOldTagName)) {
        return element;
      }
      PsiFile file = element.getContainingFile();
      assert file instanceof XmlFile;
      if (!StringUtil.isEmpty(myEntry.myNewNamespace)) {
        String prefixUsed = AndroidResourceUtil.ensureNamespaceImported((XmlFile)file, myEntry.myNewNamespace, null);
        xmlTag.setName(prefixUsed + ":" + myEntry.myNewTagName);
      }
      else {
        xmlTag.setName(myEntry.myNewTagName);
      }
      return null;
    }
  }

  static class ChangeXmlAttrUsageInfo extends MigrateToAppCompatUsageInfo {
    private final AttributeMigrationEntry myEntry;

    public ChangeXmlAttrUsageInfo(@NotNull PsiElement element, @NotNull AttributeMigrationEntry entry) {
      super(element);
      myEntry = entry;
    }

    @Override
    public PsiElement applyChange(@NotNull PsiMigration migration) {
      PsiElement element = getElement();
      XmlAttribute currentAttr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class, false);
      if (currentAttr == null || !currentAttr.getLocalName().equals(myEntry.myOldAttributeName)) {
        return element;
      }
      PsiFile file = element.getContainingFile();
      assert file instanceof XmlFile;
      if (StringUtil.isEmpty(myEntry.myNewNamespace)) {
        currentAttr.setName(myEntry.myNewAttributeName);
      }
      else {
        String prefixUsed = AndroidResourceUtil.ensureNamespaceImported((XmlFile)file, myEntry.myNewNamespace, null);
        currentAttr.setName(prefixUsed + ":" + myEntry.myNewAttributeName);
      }
      return null;
    }
  }

  static class ChangeXmlAttrValueUsageInfo extends MigrateToAppCompatUsageInfo {
    private final AttributeValueMigrationEntry myEntry;

    public ChangeXmlAttrValueUsageInfo(@NotNull PsiElement element, @NotNull AttributeValueMigrationEntry entry) {
      super(element);
      myEntry = entry;
    }

    @Override
    public PsiElement applyChange(@NotNull PsiMigration migration) {
      // TODO: does it matter when this is done? after or before an XmlAttribute is changed?
      PsiElement element = getElement();
      XmlAttribute currentAttr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class, false);
      if (currentAttr == null) {
        return null;
      }
      PsiFile file = element.getContainingFile();
      assert file instanceof XmlFile;
      currentAttr.setValue(myEntry.myNewAttrValue);
      return null;
    }
  }
}
