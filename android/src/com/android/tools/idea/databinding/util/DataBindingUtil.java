/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.util;

import com.android.SdkConstants;
import com.android.tools.idea.databinding.DataBindingMode;
import com.android.tools.idea.databinding.LayoutBindingSupport;
import com.android.tools.idea.databinding.index.BindingXmlData;
import com.android.tools.idea.databinding.index.BindingXmlIndex;
import com.android.tools.idea.databinding.index.ImportData;
import com.android.tools.idea.lang.databinding.DataBindingExpressionSupport;
import com.android.tools.idea.lang.databinding.DataBindingExpressionUtil;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.GenericAttributeValue;
import java.util.List;
import org.jetbrains.android.dom.layout.Import;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class that handles the interaction between Data Binding and the IDE.
 */
public final class DataBindingUtil {
  public static final String BR = "BR";

  @NotNull
  private static Logger getLog() { return Logger.getInstance(DataBindingUtil.class); }

  /**
   * Returns the first implementation for this data binding extension point, but will be null if the
   * data binding plugin isn't enabled or no implementation is found.
   */
  @Nullable
  private static LayoutBindingSupport getBindingSupport() {
    List<LayoutBindingSupport> extensionList = LayoutBindingSupport.EP_NAME.getExtensionList();
    return extensionList.isEmpty() ? null : extensionList.get(0);
  }

  /**
   * Returns if data binding is enabled for the facet, or false if data binding plugin isn't enabled.
   */
  static public boolean isDataBindingEnabled(@NotNull AndroidFacet facet) {
    return getDataBindingMode(facet) != DataBindingMode.NONE;
  }

  /**
   * Returns the data binding mode for the facet or NONE if data binding plugin isn't enabled.
   */
  @NotNull
  static public DataBindingMode getDataBindingMode(@NotNull AndroidFacet facet) {
    LayoutBindingSupport support = getBindingSupport();
    return support == null ? DataBindingMode.NONE : support.getDataBindingMode(facet);
  }

  /**
   * Returns tracker that increases when a facet's data binding enabled value changes,
   * or keeps unchanged if data binding plugin isn't enabled.
   */
  @NotNull
  static public ModificationTracker getDataBindingEnabledTracker() {
    LayoutBindingSupport support = getBindingSupport();
    return support == null ? (() -> 0) : support.getDataBindingEnabledTracker();
  }

  /**
   * Returns the qualified path of a class name that should be used for a generated layout binding
   * class, or {@code null} if the current {@link AndroidFacet} doesn't currently provide a valid
   * module package.
   *
   * By default, a layout called "layout.xml" causes a class to get generated with the qualified
   * path "(module-package).databinding.LayoutBinding".
   *
   * This value can be overridden using the {@code <data class=...>} attribute, with a few
   * accepted patterns:
   *
   * "custom.path.CustomBinding"  -- generates --> "custom.path.CustomBinding"
   * "CustomBinding"              -- generates --> "(module-package).databinding.CustomBinding
   * ".custom.path.CustomBinding" -- generates --> "(module-package).custom.path.CustomBinding
   */
  @Nullable
  public static String getQualifiedBindingName(@NotNull AndroidFacet facet, @NotNull BindingXmlIndex.Entry bindingIndexEntry) {
    String modulePackage = ProjectSystemUtil.getModuleSystem(facet).getPackageName();
    if (modulePackage == null) {
      return null;
    }

    String customBindingName = bindingIndexEntry.getData().getCustomBindingName();
    if (customBindingName == null || customBindingName.isEmpty()) {
      return modulePackage + ".databinding." + convertFileNameToJavaClassName(bindingIndexEntry.getFile().getName()) + "Binding";
    }
    else {
      int firstDotIndex = customBindingName.indexOf('.');

      if (firstDotIndex < 0) {
        return modulePackage + ".databinding." + customBindingName;
      }
      else {
        int lastDotIndex = customBindingName.lastIndexOf('.');
        String packageName;
        if (firstDotIndex == 0) {
          // A custom name like ".ExampleBinding" generates a binding class in the module package.
          packageName = modulePackage + customBindingName.substring(0, lastDotIndex);
        }
        else {
          packageName = customBindingName.substring(0, lastDotIndex);
        }
        String simpleClassName = customBindingName.substring(lastDotIndex + 1);
        return packageName + "." + simpleClassName;
      }
    }

  }

  /**
   * Utility method that implements Data Binding's logic to convert a file name to a Java Class name
   *
   * @param name The name of the file
   * @return The class name that will represent the given file
   */
  @NotNull
  public static String convertFileNameToJavaClassName(@NotNull String name) {
    int dotIndex = name.indexOf('.');
    if (dotIndex >= 0) {
      name = name.substring(0, dotIndex);
    }

    String[] split = name.split("[_-]");
    StringBuilder out = new StringBuilder();
    for (String section : split) {
      out.append(StringUtil.capitalize(section));
    }
    return out.toString();
  }

  /**
   * Utility method to convert an 'android:id' value into a java field name.
   *
   * Note that, though uncommon in use, the android:id format technically supports putting a '.' in
   * its value, which should get treated like a '_' in the final result. Therefore, we treat any
   * dots present in the passed-in {@code name} as underscores.
   */
  public static String convertAndroidIdToJavaFieldName(@NotNull String name) {
    return convertVariableNameToJavaFieldName(name.replace('.', '_'));
  }

  /**
   * Utility method to convert a variable name (declared in XML, so it might contain underscores)
   * into a java field name.
   *
   * For example, "test_id" to "testId".
   */
  @NotNull
  public static String convertVariableNameToJavaFieldName(@NotNull String name) {
    // Split on any character that's not US alphanumeric, e.g. underscores and accented characters.
    // This matches the behavior of the data binding compiler, which strips aggressively because non-US
    // locales can have extremely complex rules about capitalization that are much easier to sidestep.
    // See also: https://issuetracker.google.com/37077964
    String[] split = name.split("[^a-zA-Z0-9]");
    StringBuilder out = new StringBuilder();
    boolean first = true;
    for (String section : split) {
      if (section.isEmpty()) continue;
      if (first) {
        first = false;
        out.append(section);
      }
      else {
        out.append(StringUtil.capitalize(section));
      }
    }
    return out.toString();
  }

  public static boolean isGetter(@NotNull PsiMethod psiMethod) {
    PsiType returnType = psiMethod.getReturnType();
    if (returnType == null) {
      return false; // Null return type indicates a constructor
    }
    return (!returnType.equals(PsiTypes.voidType())
            && psiMethod.getParameterList().getParametersCount() == 0
            && isPrefixedJavaIdentifier(psiMethod.getName(), "get"));
  }

  public static boolean isBooleanGetter(@NotNull PsiMethod psiMethod) {
    PsiType returnType = psiMethod.getReturnType();
    if (returnType == null) {
      return false; // Null return type indicates a constructor
    }
    return (returnType.equals(PsiTypes.booleanType())
            && psiMethod.getParameterList().getParametersCount() == 0
            && isPrefixedJavaIdentifier(psiMethod.getName(), "is"));
  }

  public static boolean isSetter(@NotNull PsiMethod psiMethod) {
    PsiType returnType = psiMethod.getReturnType();
    if (returnType == null) {
      return false; // Null return type indicates a constructor
    }
    return (returnType.equals(PsiTypes.voidType())
            && psiMethod.getParameterList().getParametersCount() == 1
            && isPrefixedJavaIdentifier(psiMethod.getName(), "set"));
  }

  private static boolean isPrefixedJavaIdentifier(@NotNull String name, @NotNull String prefix) {
    return isPrefix(name, prefix) && Character.isJavaIdentifierStart(name.charAt(prefix.length()));
  }

  /**
   * Given a getter or setter method, returns its name with the prefix stripped.
   * Otherwise, just return the original name.
   */
  @NotNull
  public static String stripPrefixFromMethod(@NotNull PsiMethod method) {
    String methodName = method.getName();
    if (isGetter(method)) {
      return StringUtil.decapitalize(methodName.substring("get".length()));
    }

    if (isBooleanGetter(method)) {
      return StringUtil.decapitalize(methodName.substring("is".length()));
    }

    if (isSetter(method)) {
      return StringUtil.decapitalize(methodName.substring("set".length()));
    }

    return methodName;
  }

  @NotNull
  public static String stripPrefixFromField(@NotNull PsiField psiField) {
    String fieldName = psiField.getName();
    assert fieldName != null;
    return stripPrefixFromField(fieldName);
  }

  private static boolean isPrefix(@NotNull CharSequence sequence, @NotNull String prefix) {
    boolean prefixes = false;
    if (sequence.length() > prefix.length()) {
      int count = prefix.length();
      prefixes = true;
      for (int i = 0; i < count; i++) {
        if (sequence.charAt(i) != prefix.charAt(i)) {
          prefixes = false;
          break;
        }
      }
    }
    return prefixes;
  }

  /**
   * Given an Android field of the format "m_field", "m_Field", "mField" or
   * "_field", return "field". Otherwise, just return the name itself back.
   */
  @NotNull
  private static String stripPrefixFromField(@NotNull String name) {
    if (name.length() >= 2) {
      char firstChar = name.charAt(0);
      char secondChar = name.charAt(1);
      if (name.length() > 2 && firstChar == 'm' && secondChar == '_') {
        char thirdChar = name.charAt(2);
        if (Character.isJavaIdentifierStart(thirdChar)) {
          return String.valueOf(Character.toLowerCase(thirdChar)) + name.subSequence(3, name.length());
        }
      } else if ((firstChar == 'm' && Character.isUpperCase(secondChar)) ||
                 (firstChar == '_' && Character.isJavaIdentifierStart(secondChar))) {
        return String.valueOf(Character.toLowerCase(secondChar)) + name.subSequence(2, name.length());
      }
    }
    return name;
  }

  /**
   * Returns the qualified name for the BR file for the given Facet.
   *
   * @param facet The {@link AndroidFacet} to check.
   * @return The qualified name for the BR class of the given Android Facet, or null if it could not be determined.
   */
  @Nullable
  public static String getBrQualifiedName(@NotNull AndroidFacet facet) {
    String packageName = getGeneratedPackageName(facet);
    return packageName == null ? null : packageName + "." + BR;
  }

  /**
   * Returns the package name that will be use to generate R file or BR file.
   *
   * @param facet The {@link AndroidFacet} to check.
   * @return The package name that can be used to generate R and BR classes, or null if it could not be determined.
   */
  @Nullable
  public static String getGeneratedPackageName(@NotNull AndroidFacet facet) {
    return ProjectSystemUtil.getModuleSystem(facet).getPackageName();
  }

  /**
   * Returns the default value, if specified, for a data binding expression associated with the target
   * {@link XmlAttribute}, or {@code null} otherwise.
   */
  @Nullable
  public static String getBindingExprDefault(@NotNull XmlAttribute psiAttribute) {
    DataBindingExpressionSupport expressionSupport = DataBindingExpressionUtil.getDataBindingExpressionSupport();
    if (expressionSupport != null) {
      return expressionSupport.getBindingExprDefault(psiAttribute);
    }
    return null;
  }

  /**
   * Returns the default value, if specified, for the target expression.
   *
   * @param expression Data binding expression enclosed in @{}
   */
  @Nullable
  public static String getBindingExprDefault(@NotNull String expression) {
    DataBindingExpressionSupport expressionSupport = DataBindingExpressionUtil.getDataBindingExpressionSupport();
    if (expressionSupport != null) {
      return expressionSupport.getBindingExprDefault(expression);
    }
    return null;
  }

  public static boolean isBindingExpression(@NotNull String string) {
    return string.startsWith(SdkConstants.PREFIX_BINDING_EXPR) || string.startsWith(SdkConstants.PREFIX_TWOWAY_BINDING_EXPR);
  }

  public static boolean isTwoWayBindingExpression(@NotNull String string) {
    return string.startsWith(SdkConstants.PREFIX_TWOWAY_BINDING_EXPR);
  }

  /**
   * The &lt;import&gt; tag supports an optional alias tag in addition to the required type tag.
   * This method fetches a final type, which is either set to the alias (if present) or the simple
   * type name from the type attribute.
   *
   * Could possibly return {@code null} if the type string is not valid.
   *
   * See also <a href=https://developer.android.com/topic/libraries/data-binding/expressions#imports>import docs</a>.
   */
  @Nullable
  public static String getAlias(@NotNull Import anImport) {
    String aliasValue = null;
    String typeValue = null;
    GenericAttributeValue<String> alias = anImport.getAlias();
    if (alias != null && alias.getXmlAttributeValue() != null) {
      aliasValue = alias.getXmlAttributeValue().getValue();
    }
    GenericAttributeValue<PsiElement> type = anImport.getType();
    if (type != null) {
      XmlAttributeValue value = type.getXmlAttributeValue();
      if (value != null) {
        typeValue = value.getValue();
      }
    }
    return getAlias(typeValue, aliasValue);
  }

  /**
   * Delegate method for other {@code getAlias} methods to call. Returns the alias directly or
   * extracts the simple type name from the {@code type} variable. Can return {@code null} if
   * the passed-in type is invalid, e.g. ends with "." or "$"
   */
  @Nullable
  private static String getAlias(@Nullable String type, @Nullable String alias) {
    if (alias != null || type == null) {
      return alias;
    }
    int i = type.lastIndexOf('.');
    int d = type.lastIndexOf('$'); // Catch inner-class types, e.g. a.b.c$D
    i = Math.max(i, d);
    if (i < 0) {
      return type;
    }
    // Return null in case of an invalid type.
    return type.length() > i + 1 ? type.substring(i + 1) : null;
  }

  /**
   * Returns the fully qualified name of the class referenced by {@code nameOrAlias}.
   * <p>
   * It is not guaranteed that the class will exist. The name returned here uses '.' for inner classes (like import declarations) and
   * not '$' as used by JVM.
   *
   * @param nameOrAlias a fully qualified name, or an alias as declared in an {@code <import>}, or an inner class of an alias.
   * @param qualifyJavaLang qualify names of java.lang classes.
   * @return the qualified name of the class, otherwise, if {@code qualifyJavaLang} is false and {@code nameOrAlias} doesn't match any
   *     imports, the unqualified name of the class, or, if {@code qualifyJavaLang} is true and the class name cannot be resolved, null.
   */
  @Nullable
  public static String getQualifiedType(@NotNull Project project,
                                        @NotNull String nameOrAlias,
                                        @NotNull BindingXmlData bindingData,
                                        boolean qualifyJavaLang) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiJavaParserFacade parser = psiFacade.getParserFacade();
    PsiType psiType;
    try {
      psiType = parser.createTypeFromText(nameOrAlias, null);
    } catch (IncorrectOperationException e) {
      return null;
    }

    if (psiType instanceof PsiPrimitiveType) {
      return nameOrAlias;
    }

    class UnresolvedClassNameException extends RuntimeException {}

    StringBuilder result = new StringBuilder();
    int[] offset = new int[1];
    try {
      psiType.accept(new ClassReferenceVisitor() {
        @Override
        public void visitClassReference(@NotNull PsiClassReferenceType classReference) {
          PsiJavaCodeReferenceElement reference = classReference.getReference();
          int nameOffset = reference.getTextRange().getStartOffset();
          // Copy text preceding the class name.
          while (offset[0] < nameOffset) {
            result.append(nameOrAlias.charAt(offset[0]++));
          }
          String className = reference.isQualified() ? reference.getQualifiedName() : reference.getReferenceName();
          if (className != null) {
            int nameLength = className.length();
            className = resolveImport(className, bindingData);
            if (qualifyJavaLang && className.indexOf('.') < 0) {
              className = qualifyClassName(className, parser);
              if (className == null) {
                throw new UnresolvedClassNameException();
              }
            }
            // Copy the resolved class name.
            result.append(className);
            offset[0] += nameLength;
          }
        }
      });
    } catch (UnresolvedClassNameException e) {
      return null;
    }

    // Copy text after the last class name.
    while (offset[0] < nameOrAlias.length()) {
      result.append(nameOrAlias.charAt(offset[0]++));
    }
    return result.toString();
  }

  /**
   * Given a class name as a String, return its fully qualified name. This may return {@code null}
   * if the associated {@link PsiClassType} cannot be resolved.
   */
  @Nullable
  private static String qualifyClassName(@NotNull String className, @NotNull PsiJavaParserFacade parser) {
    PsiType psiType = parser.createTypeFromText(className, null);
    if (psiType instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType)psiType).resolve();
      if (psiClass == null) {
        return null;
      }
      String name = psiClass.getQualifiedName();
      if (name != null) {
        return name;
      }
    }
    return className;
  }

  /**
   * Resolves a class name using import statements in the data binding information.
   *
   * @param className the class name, possibly not qualified. The class name may contain dots if it corresponds to a nested class.
   * @return the fully qualified class name, or the original name if the first segment of {@code className} doesn't match
   *     any import statement.
   */
  @NotNull
  public static String resolveImport(@NotNull String className, @NotNull BindingXmlData bindingData) {
    int dotOffset = className.indexOf('.');
    String firstSegment = dotOffset >= 0 ? className.substring(0, dotOffset) : className;
    ImportData anImport = bindingData.findImport(firstSegment);
    String importedType = anImport != null ? anImport.getType() : null;
    if (importedType == null) {
      return className;
    }
    return dotOffset >= 0 ? importedType + className.substring(dotOffset) : importedType;
  }

  @Nullable
  public static XmlFile findXmlFile(@NotNull Project project, @NotNull VirtualFile layoutFile) {
    if (!layoutFile.isValid()) {
      // PsiManager.findfile below will return null anyway in this case but also log an error. For
      // databinding, we don't want to log an error - if the file is invalid, just abort.
      getLog().info("findXmlFile aborted for invalid file: " + layoutFile);
      return null;
    }
    return (XmlFile)PsiManager.getInstance(project).findFile(layoutFile);
  }

  /**
   * Visits all class type references contained in a type.
   */
  public abstract static class ClassReferenceVisitor extends PsiTypeVisitor<Void> {
    @Override
    @Nullable
    public final Void visitClassType(@NotNull PsiClassType classType) {
      if (classType instanceof PsiClassReferenceType) {
        visitClassReference((PsiClassReferenceType)classType);
      }

      PsiType[] parameters = classType.getParameters();
      for (PsiType parameter : parameters) {
        parameter.accept(this);
      }
      return null;
    }

    @Override
    @Nullable
    public final Void visitArrayType(@NotNull PsiArrayType arrayType) {
      PsiType type = arrayType.getComponentType();
      type.accept(this);
      return null;
    }

    /** Visits a class reference. The referenced class may or may not exist. */
    public abstract void visitClassReference(@NotNull PsiClassReferenceType classReference);
  }
}
