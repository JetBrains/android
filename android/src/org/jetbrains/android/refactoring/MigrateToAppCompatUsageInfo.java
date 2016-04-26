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

import com.android.annotations.NonNull;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.AttributeMigrationEntry;
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.AttributeValueMigrationEntry;
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.ReplaceMethodCallMigrationEntry;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;


abstract class MigrateToAppCompatUsageInfo extends UsageInfo {

  public MigrateToAppCompatUsageInfo(@NotNull PsiReference reference) {
    super(reference);
  }

  public MigrateToAppCompatUsageInfo(@NotNull PsiElement element) {
    super(element);
  }

  public abstract void applyChange(@NonNull PsiMigration migration);

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
    public void applyChange(@NonNull PsiMigration migration) {
      PsiElement element = getElement();
      if (element instanceof PsiReference && isValid()) {
        PsiReference reference = (PsiReference)element;
        String newName = myEntry.myNewMethodName;
        // Handle direct method references example getSupportActionBar()
        PsiElement newRef = reference.handleElementRename(newName);
        JavaCodeStyleManager.getInstance(reference.getElement().getProject()).shortenClassReferences(newRef);
      }
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
    public void applyChange(@NonNull PsiMigration psiMigration) {
      // Here we need to either find or create the class so that imports/class names can be resolved.
      PsiClass aClass = MigrateToAppCompatUtil.findOrCreateClass(getProject(), psiMigration, mapEntry.myNewName);

      PsiElement element = getElement();
      if (element == null || !element.isValid()) {
        return;
      }
      if (element instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
        PsiElement updatedReference = referenceElement.bindToElement(aClass);
        if (!(updatedReference.getParent() instanceof PsiImportStatement)) {
          // Shortening imports does not work if the shortName is the same as the one that is already imported.
          JavaCodeStyleManager.getInstance(updatedReference.getProject())
            .shortenClassReferences(updatedReference);
        }
      }
    }
  }

  /**
   * Change a style attribute or body (within styles.xml)
   */
  static class ChangeStyleUsageInfo extends MigrateToAppCompatUsageInfo {
    protected final String myFromValue;
    protected final String myToValue;

    ChangeStyleUsageInfo(@NotNull PsiElement element, @NonNull String fromValue, @NonNull String toValue) {
      super(element);
      myFromValue = fromValue;
      myToValue = toValue;
    }

    @Override
    public void applyChange(@NonNull PsiMigration migration) {
      // This can either be an attribute <item name="android:windowNoTitle" ..
      // or the body text of the item such as <item ..>?android:itemSelectableBackground</item>
      PsiElement element = getElement();
      XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
      if (tag == null) {
        return;
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
    }
  }

  static class ChangeThemeUsageInfo extends ChangeStyleUsageInfo {

    ChangeThemeUsageInfo(@NonNull XmlAttributeValue element, @NonNull String fromValue, @NonNull String toValue) {
      super(element, fromValue, toValue);
    }

    @Override
    public void applyChange(@NonNull PsiMigration migration) {
      XmlAttributeValue attributeValue = PsiTreeUtil.getParentOfType(getElement(), XmlAttributeValue.class, false);
      if (attributeValue == null) {
        return;
      }
      String value = attributeValue.getValue();

      XmlAttribute attribute = PsiTreeUtil.getParentOfType(getElement(), XmlAttribute.class, true);
      if (attribute != null && value.equals(myFromValue)) {
        attribute.setValue(myToValue);
      }
    }
  }

  static class ChangeCustomViewUsageInfo extends MigrateToAppCompatUsageInfo {

    private final String mySuggestedSuperClass;

    public ChangeCustomViewUsageInfo(@NotNull PsiElement element, @NonNull String suggestedSuperClass) {
      super(element);
      mySuggestedSuperClass = suggestedSuperClass;
    }

    @Override
    public void applyChange(@NonNull PsiMigration migration) {
      PsiClass psiClass = MigrateToAppCompatUtil.findOrCreateClass(getProject(), migration, mySuggestedSuperClass);

      PsiElement element = getElement();
      assert element != null;
      if (!element.isValid()) {
        return;
      }
      PsiReference reference = element.getReference();
      if (reference != null) {
        PsiElement updatedReference = reference.bindToElement(psiClass);
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(getProject());
        styleManager.shortenClassReferences(updatedReference);
        styleManager.optimizeImports(updatedReference.getContainingFile());
      }
    }
  }

  static class ReplaceMethodUsageInfo extends MigrateToAppCompatUsageInfo {
    private final ReplaceMethodCallMigrationEntry myEntry;

    public ReplaceMethodUsageInfo(@NonNull PsiReference element, @NonNull ReplaceMethodCallMigrationEntry entry) {
      super(element);
      myEntry = entry;
    }

    @Override
    public void applyChange(@NonNull PsiMigration psiMigration) {
      PsiElement element = getElement();
      if (!(element instanceof PsiReference) || !isValid()) {
        return;
      }
      PsiReference reference = (PsiReference)element;

      PsiClass psiClass = MigrateToAppCompatUtil.findOrCreateClass(getProject(), psiMigration, myEntry.myNewClassName);
      String methodFragment = myEntry.myNewMethodName;

      if (!(element instanceof PsiReferenceExpression)
          || ((PsiReferenceExpression)element).getQualifierExpression() == null) {
        return;
      }
      PsiMethodCallExpression oldMethodCall = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethodCallExpression.class);
      if (oldMethodCall == null) {
        return;
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

      oldMethodCall.replace(call);
    }
  }

  static class ChangeXmlTagUsageInfo extends MigrateToAppCompatUsageInfo {
    private final AppCompatMigrationEntry.XmlTagMigrationEntry myEntry;

    public ChangeXmlTagUsageInfo(@NotNull PsiElement element,
                                 @NonNull AppCompatMigrationEntry.XmlTagMigrationEntry entry) {
      super(element);
      myEntry = entry;
    }

    @Override
    public void applyChange(@NonNull PsiMigration migration) {
      PsiElement element = getElement();
      XmlTag xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
      if (xmlTag == null || !xmlTag.getLocalName().equals(myEntry.myOldTagName)) {
        return;
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
    }
  }

  static class ChangeXmlAttrUsageInfo extends MigrateToAppCompatUsageInfo {
    private final AttributeMigrationEntry myEntry;

    public ChangeXmlAttrUsageInfo(@NotNull PsiElement element, @NonNull AttributeMigrationEntry entry) {
      super(element);
      myEntry = entry;
    }

    @Override
    public void applyChange(@NonNull PsiMigration migration) {
      PsiElement element = getElement();
      XmlAttribute currentAttr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class, false);
      if (currentAttr == null || !currentAttr.getLocalName().equals(myEntry.myOldAttributeName)) {
        return;
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
    }
  }

  static class ChangeXmlAttrValueUsageInfo extends MigrateToAppCompatUsageInfo {
    private final AttributeValueMigrationEntry myEntry;

    public ChangeXmlAttrValueUsageInfo(@NotNull PsiElement element, @NonNull AttributeValueMigrationEntry entry) {
      super(element);
      myEntry = entry;
    }

    @Override
    public void applyChange(@NonNull PsiMigration migration) {
      // TODO: does it matter when this is done? after or before an XmlAttribute is changed?
      PsiElement element = getElement();
      XmlAttribute currentAttr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class, false);
      if (currentAttr == null) {
        return;
      }
      PsiFile file = element.getContainingFile();
      assert file instanceof XmlFile;
      currentAttr.setValue(myEntry.myNewAttrValue);
    }
  }
}
