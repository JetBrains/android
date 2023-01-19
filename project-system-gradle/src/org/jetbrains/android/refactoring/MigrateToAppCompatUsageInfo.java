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
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.google.common.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.*;
import com.android.tools.idea.res.IdeResourcesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

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
    final MethodMigrationEntry myEntry;

    public ChangeMethodUsageInfo(PsiReference ref, @NotNull MethodMigrationEntry entry) {
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
    final ClassMigrationEntry mapEntry;

    public ClassMigrationUsageInfo(@NotNull UsageInfo info, @NotNull ClassMigrationEntry mapEntry) {
      //noinspection ConstantConditions
      super(info.getElement());
      this.mapEntry = mapEntry;
    }

    @Override
    public PsiElement applyChange(@NotNull PsiMigration psiMigration) {
      if (mapEntry.myOldName.equals(mapEntry.myNewName)) {
        // No-op migration rule
        return null;
      }

      // Here we need to either find or create the class so that imports/class names can be resolved.
      PsiClass aClass = AndroidRefactoringUtil.findOrCreateClass(getProject(), psiMigration, mapEntry.myNewName);

      PsiElement element = getElement();
      if (element == null || !element.isValid()) {
        return element;
      }
      if (element instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
        return referenceElement.bindToElement(aClass);
      }
      else if (element instanceof XmlAttributeValue) {
        XmlAttributeValue value = (XmlAttributeValue)element;
        XmlAttribute attribute = (XmlAttribute)value.getParent();
        if (mapEntry.myOldName.equals(value.getValue())) {
          attribute.setValue(mapEntry.myNewName);
        }
      }
      else {
        final TextRange range = getRangeInElement();
        for (PsiReference reference : element.getReferences()) {
          if (reference instanceof JavaClassReference) {
            final JavaClassReference classReference = (JavaClassReference)reference;
            if (classReference.getRangeInElement().equals(range)) {
              return classReference.bindToElement(aClass);
            }
          }
          else if (reference != null && reference.getElement() instanceof XmlTag) {
            XmlTag ref = (XmlTag)reference.getElement();
            String localName = ref.getLocalName();
            if (mapEntry.myOldName.equals(localName)) {
              ref.setName(mapEntry.myNewName);
            }
          }
          else if (reference != null && MigrateToAppCompatUtil.isKotlinSimpleNameReference(reference)) {
            return reference.bindToElement(aClass);
          }
        }
      }
      return null;
    }
  }

  /**
   * UsageInfo specific to migrating a package
   */
  static class PackageMigrationUsageInfo extends MigrateToAppCompatUsageInfo {
    final PackageMigrationEntry mapEntry;

    public PackageMigrationUsageInfo(@NotNull UsageInfo info, @NotNull PackageMigrationEntry mapEntry) {
      //noinspection ConstantConditions
      super(info.getElement());
      this.mapEntry = mapEntry;
    }

    @Override
    public PsiElement applyChange(@NotNull PsiMigration migration) {
      if (mapEntry.myOldName.equals(mapEntry.myNewName)) {
        // No-op migration rule
        return null;
      }

      PsiPackage aPackage = AndroidRefactoringUtil.findOrCreatePackage(getProject(), migration, mapEntry.myNewName);
      PsiElement element = getElement();
      if (element == null || !element.isValid()) {
        return element;
      }
      if (element instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
        return referenceElement.bindToElement(aPackage);
      }
      else {
        final TextRange range = getRangeInElement();
        for (PsiReference reference : element.getReferences()) {
          if (reference == null) {
            continue;
          }
          if (reference instanceof JavaClassReference) {
            final JavaClassReference classReference = (JavaClassReference)reference;
            if (classReference.getRangeInElement().equals(range)) {
              return classReference.bindToElement(aPackage);
            }
          }
          else if (reference.getElement() instanceof XmlTag) {
            XmlTag ref = (XmlTag)reference.getElement();
            String localName = ref.getLocalName();
            if (localName.startsWith(mapEntry.myOldName)) {
              // Binding to element implemented in AndroidXmlReferenceProvider incorrectly adds the package
              // as a prefix to the existing package, so use the local name replacement instead.
              // reference.bindToElement(bindTo);
              String newName = localName.replace(mapEntry.myOldName, mapEntry.myNewName);
              ref.setName(newName);
            }
          }
          else if (reference.getElement() instanceof XmlAttributeValue) {
            PsiElement parent = reference.getElement().getParent();
            if (parent instanceof XmlAttribute) {
              XmlAttribute attribute = (XmlAttribute)parent;
              String oldValue = StringUtil.notNullize(attribute.getValue());
              if (oldValue.startsWith(mapEntry.myOldName)) {
                attribute.setValue(oldValue.replace(mapEntry.myOldName, mapEntry.myNewName));
              }
            }
          }
          else if (MigrateToAppCompatUtil.isKotlinSimpleNameReference(reference) &&
                   element.getParent() != null &&
                   element.getParent().getText().equals(mapEntry.myOldName)) {
            // Before changing the package, we verify that this is actually the correct refactoring by seeing
            // if the element matches the old package name. This is a workaround for b/78800780
            return reference.bindToElement(aPackage);
          }
        }
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
      PsiClass psiClass = AndroidRefactoringUtil.findOrCreateClass(getProject(), migration, mySuggestedSuperClass);

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

      PsiClass psiClass = AndroidRefactoringUtil.findOrCreateClass(getProject(), psiMigration, myEntry.myNewClassName);
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
    private final XmlTagMigrationEntry myEntry;

    public ChangeXmlTagUsageInfo(@NotNull PsiElement element,
                                 @NotNull XmlTagMigrationEntry entry) {
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
        String prefixUsed = IdeResourcesUtil.ensureNamespaceImported((XmlFile)file, myEntry.myNewNamespace, null);
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
        String prefixUsed = IdeResourcesUtil.ensureNamespaceImported((XmlFile)file, myEntry.myNewNamespace, null);
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

  abstract static class GradleUsageInfo extends MigrateToAppCompatUsageInfo {
    public GradleUsageInfo(@NotNull PsiElement element) {
      super(element);
    }
  }

  static class GradleDependencyUsageInfo extends GradleUsageInfo {
    // (groupName, artifactName, defaultVersion) -> version
    @NotNull
    private final TriFunction<String, String, String, String> myGetLibraryRevisionFunction;
    private final GradleMigrationEntry mapEntry;
    private final ArtifactDependencyModel myModel;
    private final ProjectBuildModel myBuildModel;

    public GradleDependencyUsageInfo(@NotNull PsiElement element,
                                     @NotNull ProjectBuildModel buildModel,
                                     @NotNull ArtifactDependencyModel model,
                                     @NonNull GradleMigrationEntry entry,
                                     @NotNull TriFunction<String, String, String, String> versionProvider) {
      super(element);
      mapEntry = entry;
      myGetLibraryRevisionFunction = versionProvider;
      myModel = model;
      myBuildModel = buildModel;
    }

    @Nullable
    @Override
    public PsiElement applyChange(@NotNull PsiMigration migration) {
      PsiElement element = getElement();
      if (element == null || !element.isValid() || !element.isWritable()) {
        return null;
      }

      // See if we can use the model to update the dependency.
      if (myModel != null && myBuildModel != null) {
        PsiElement modelPsi =  myModel.getPsiElement();
        if (modelPsi != null && modelPsi.isValid()) {
          // Follow references
          myModel.enableSetThrough();
          if (mapEntry instanceof GradleDependencyMigrationEntry) {
            GradleDependencyMigrationEntry depEntry = (GradleDependencyMigrationEntry)mapEntry;
            String group = myModel.group().toString();
            String name = myModel.name().toString();
            if (group != null && group.equals(depEntry.getOldGroupName())) {
              myModel.group().getResultModel().setValue(depEntry.getNewGroupName());
            }
            if (name != null && name.equals(depEntry.getOldArtifactName())) {
              myModel.name().getResultModel().setValue(depEntry.getNewArtifactName());
            }
          }
          String version = myModel.version().toString();
          String newVersion;
          if (mapEntry instanceof UpdateGradleDependencyVersionMigrationEntry) {
            // For version upgrades get the highest of the existing one and the old one
            newVersion = getHighestVersion(version, mapEntry.getNewBaseVersion());
          }
          else {
            newVersion = mapEntry.getNewBaseVersion();
          }
          if (newVersion != null) {
            myModel.version().getResultModel().setValue(newVersion);
          }
        }

        ApplicationManager.getApplication().runReadAction(myBuildModel::applyChanges);
        return null;
      }
      // Otherwise fallback to editing the Psi elements directly.

      // This handles the case where the dependency was declared using the map notation
      // e.g: implementation group: 'com.android.support', name:'appcompat-v7', version: '27.0.2'
      if (element instanceof GrArgumentList) {
        GrArgumentList list = (GrArgumentList)element;
        GroovyPsiElement[] expressions = list.getAllArguments();
        PsiElement group = null, name = null, version = null;
        for (GroovyPsiElement expression : expressions) {
          if (expression instanceof GrNamedArgument) {
            GrNamedArgument namedArgument = (GrNamedArgument)expression;
            String labelName = namedArgument.getLabelName();
            if (labelName == null) {
              continue;
            }
            PsiElement val = PsiTreeUtil.findChildOfType(namedArgument, GrLiteral.class);
            switch (labelName) {
              case "group":
                group = val;
                break;
              case "name":
                name = val;
                break;
              case "version":
                version = val;
                break;
            }
          }
        }
        if (group == null || name == null || version == null) {
          return null;
        }

        if (mapEntry instanceof GradleDependencyMigrationEntry) {
          GradleDependencyMigrationEntry depEntry = (GradleDependencyMigrationEntry)mapEntry;
          renameElement(group, depEntry.getOldGroupName(), depEntry.getNewGroupName());
          renameElement(name, depEntry.getOldArtifactName(), depEntry.getNewArtifactName());
        }
        if (version.getReference() != null) {
          String newVersion;
          if (mapEntry instanceof UpdateGradleDependencyVersionMigrationEntry) {
            // For version upgrades get the highest of the existing one and the old one
            newVersion = getHighestVersion(version.getText(), mapEntry.getNewBaseVersion());

            if (newVersion == null) {
              // In version upgrades, if we can not check the version, leave as it is
              return null;
            }
          }
          else {
            newVersion = mapEntry.getNewBaseVersion();
          }
          version.getReference().handleElementRename(
            myGetLibraryRevisionFunction.apply(mapEntry.getNewGroupName(), mapEntry.getNewArtifactName(), newVersion));
        }
      }
      else if (element.getReference() != null) {
        String newVersion;
        if (mapEntry instanceof UpdateGradleDependencyVersionMigrationEntry) {
          GradleCoordinate existingCoordinate = GradleCoordinate.parseCoordinateString(element.getReference().getCanonicalText());
          // For version upgrades get the highest of the existing one and the old one
          newVersion = getHighestVersion(existingCoordinate, mapEntry.getNewBaseVersion());

          if (newVersion == null) {
            // In version upgrades, if we can not check the version, leave as it is
            return null;
          }
        }
        else {
          newVersion = mapEntry.getNewBaseVersion();
        }


        PsiElement parent = element.getParent();
        if (element instanceof GrReferenceExpression && parent != null) {
          // This is likely to be an expression that resolves to the artifact, replace it with a literal
          GrLiteral newLiteral = GroovyPsiElementFactory
            .getInstance(getProject())
            .createLiteralFromValue(mapEntry.toCompactNotation(
              myGetLibraryRevisionFunction.apply(mapEntry.getNewGroupName(), mapEntry.getNewArtifactName(), newVersion)));
          parent.replace(newLiteral);
        }
        else {
          // this was declared as a string literal for example
          // implementation 'com.android.support.constraint:constraint-layout:1.0.2'
          element.getReference().handleElementRename(mapEntry.toCompactNotation(
            myGetLibraryRevisionFunction.apply(mapEntry.getNewGroupName(), mapEntry.getNewArtifactName(), newVersion)));
        }
      } else if (element instanceof GrString) {
        String newVersion;
        if (mapEntry instanceof UpdateGradleDependencyVersionMigrationEntry) {
          GradleCoordinate existingCoordinate = GradleCoordinate.parseCoordinateString(GrStringUtil.removeQuotes(element.getText()));

          // For version upgrades get the highest of the existing one and the old one
          newVersion = getHighestVersion(existingCoordinate, mapEntry.getNewBaseVersion());
          if (newVersion == null) {
            // In version upgrades, if we can not check the version, leave as it is
            return null;
          }
        }
        else {
          newVersion = mapEntry.getNewBaseVersion();
        }

        // This is just a literal string, replace it
        GrLiteral newLiteral = GroovyPsiElementFactory
          .getInstance(getProject())
          .createLiteralFromValue(
            mapEntry.toCompactNotation(
              myGetLibraryRevisionFunction.apply(mapEntry.getNewGroupName(), mapEntry.getNewArtifactName(), newVersion)));
        element.replace(newLiteral);
      }
      return null;
    }

    /**
     * Returns the highest of two gradle coordinates. The first one might be null or a variable like "$var"
     */
    @VisibleForTesting
    @Nullable
    static String getHighestVersion(@Nullable String a, @NotNull String defaultVersion) {
      if (a == null) {
        return defaultVersion;
      }

      if (a.startsWith("$")) {
        // This is a variable, can not compute the highest version
        return null;
      }

      GradleCoordinate versionA = GradleCoordinate.parseVersionOnly(a);
      GradleCoordinate versionB = GradleCoordinate.parseVersionOnly(defaultVersion);
      if (GradleCoordinate.COMPARE_PLUS_HIGHER.compare(versionA, versionB) >= 0) {
        return a;
      }

      return defaultVersion;
    }

    /**
     * Returns the highest of two gradle coordinates. The first one is a full version, the second one
     * just the version to compare.
     */
    @VisibleForTesting
    @Nullable
    static String getHighestVersion(@Nullable GradleCoordinate coordinate, @NotNull String defaultVersion) {
      if (coordinate == null) {
        return defaultVersion;
      }

      return coordinate.getLowerBoundVersion() != null ?
             getHighestVersion(coordinate.getLowerBoundVersion().toString(), defaultVersion) :
             defaultVersion;
    }

    private static void renameElement(PsiElement current, String oldName, String newName) {
      String elementText = StringUtil.trimStart(current.getText(), "'");
      elementText = StringUtil.trimEnd(elementText, "'");
      elementText = StringUtil.trimStart(elementText, "\"");
      elementText = StringUtil.trimEnd(elementText, "\"");
      if (elementText.equals(oldName) && current.getReference() != null) {
        current.getReference().handleElementRename(newName);
      }
    }
  }

  static class GradleStringUsageInfo extends GradleUsageInfo {

    @NotNull private final String myNewValue;
    private final GradleBuildModel myBuildModel;

    public GradleStringUsageInfo(@NotNull PsiElement element, @NotNull String newValue, @NotNull GradleBuildModel buildModel) {
      super(element);
      myNewValue = newValue;
      myBuildModel = buildModel;
    }

    @Nullable
    @Override
    public PsiElement applyChange(@NotNull PsiMigration migration) {
      if (myBuildModel.isModified()) {
        myBuildModel.applyChanges();
        return getElement();
      }

      PsiElement element = getElement();
      if (element == null) {
        return null;
      }

      PsiReference reference = element.getReference();
      if (reference != null) {
        reference.handleElementRename(myNewValue);
      }

      return element;
    }
  }

  static class AddGoogleRepositoryUsageInfo extends GradleUsageInfo {
    private final RepositoriesModel myRepositoriesModel;
    private final ProjectBuildModel myProjectBuildModel;

    public AddGoogleRepositoryUsageInfo(@NotNull ProjectBuildModel projectBuildModel,
                                        @NotNull RepositoriesModel repositoriesModel,
                                        @NotNull PsiElement repositoriesModelPsiElement) {
      super(repositoriesModelPsiElement);
      myProjectBuildModel = projectBuildModel;
      myRepositoriesModel = repositoriesModel;
    }

    @Nullable
    @Override
    public PsiElement applyChange(@NotNull PsiMigration migration) {
      myRepositoriesModel.addGoogleMavenRepository();
      myProjectBuildModel.applyChanges();
      return getElement();
    }
  }

  private interface TriFunction<A, B, C, R> {
    R apply(A a, B b, C c);
  }
}
