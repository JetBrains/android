/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.databinding;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.databinding.index.ViewIdInfo;
import com.android.tools.idea.lang.databinding.DataBindingExpressionSupport;
import com.android.tools.idea.lang.databinding.DataBindingExpressionUtil;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.res.BindingLayoutData;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.res.binding.BindingLayoutInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
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
  private static List<String> VIEW_PACKAGE_ELEMENTS = ImmutableList.of(SdkConstants.VIEW, SdkConstants.VIEW_GROUP,
                                                                       SdkConstants.TEXTURE_VIEW, SdkConstants.SURFACE_VIEW);

  /**
   * Returns the first implementation for this data binding extension point, but will be null if the
   * data binding plugin isn't enabled or no implementation is found.
   */
  @Nullable
  static DataBindingSupport getDataBindingSupport() {
    List<DataBindingSupport> extensionList = DataBindingSupport.EP_NAME.getExtensionList();
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
    DataBindingSupport support = getDataBindingSupport();
    return support == null ? DataBindingMode.NONE : support.getDataBindingMode(facet);
  }

  /**
   * Returns tracker that increases when a facet's data binding enabled value changes,
   * or keeps unchanged if data binding plugin isn't enabled.
   */
  @NotNull
  static public ModificationTracker getDataBindingEnabledTracker() {
    DataBindingSupport support = getDataBindingSupport();
    return support == null ? (() -> 0) : support.getDataBindingEnabledTracker();
  }

  /**
   * Helper method that convert a type from a String value to a {@link PsiType}, returning
   * {@code null} instead of throwing an exception if the result is a reference to an invalid type.
   */
  @Nullable
  public static PsiType parsePsiType(@NotNull String typeStr, @NotNull AndroidFacet facet, @Nullable PsiElement context) {
    return parsePsiType(typeStr, facet.getModule().getProject(), context);
  }

  /**
   * Helper method that convert a type from a String value to a {@link PsiType}, returning
   * {@code null} instead of throwing an exception if the result is a reference to an invalid type.
   */
  @Nullable
  public static PsiType parsePsiType(@NotNull String typeStr, @NotNull Project project, @Nullable PsiElement context) {
    PsiElementFactory instance = PsiElementFactory.getInstance(project);
    try {
      PsiType type = instance.createTypeFromText(typeStr, context);
      if ((type instanceof PsiClassReferenceType) && ((PsiClassReferenceType)type).getClassName() == null) {
        // Ensure that if the type is a reference, it's a reference to a valid type.
        return null;
      }
      return type;
    }
    catch (IncorrectOperationException e) {
      // Class named "text" not found.
      return null;
    }
  }

  /**
   * Convert a view tag (e.g. &lt;TextView... /&gt;) to its PSI type, if possible, or return {@code null}
   * otherwise.
   */
  @Nullable
  public static PsiType resolveViewPsiType(@NotNull ViewIdInfo viewIdInfo, @NotNull AndroidFacet facet) {
    String viewClassName = getViewClassName(viewIdInfo, facet);
    if (StringUtil.isNotEmpty(viewClassName)) {
      return parsePsiType(viewClassName, facet, null);
    }
    return null;
  }

  /**
   * Receives a {@link ViewIdInfo} and returns the name of the View class that is implied by it.
   * May return null if it cannot find anything reasonable (e.g. it is a merge but does not have data binding)
   */
  @Nullable
  private static String getViewClassName(@NotNull ViewIdInfo viewIdInfo, @NotNull AndroidFacet facet) {
    String viewName = viewIdInfo.getViewName();
    if (viewName != null && viewName.indexOf('.') == -1) {
      if (VIEW_PACKAGE_ELEMENTS.contains(viewName)) {
        return SdkConstants.VIEW_PKG_PREFIX + viewName;
      } else if (SdkConstants.WEB_VIEW.equals(viewName)) {
        return SdkConstants.ANDROID_WEBKIT_PKG + viewName;
      } else if (SdkConstants.VIEW_MERGE.equals(viewName)) {
        return getViewClassNameFromMergeTag(viewIdInfo, facet);
      } else if (SdkConstants.VIEW_INCLUDE.equals(viewName)) {
        return getViewClassNameFromIncludeTag(viewIdInfo, facet);
      } else if (SdkConstants.VIEW_STUB.equals(viewName)) {
        DataBindingMode mode = getDataBindingMode(facet);
        return mode.viewStubProxy;
      }
      return SdkConstants.WIDGET_PKG_PREFIX + viewName;
    } else {
      return viewName;
    }
  }

  @NotNull
  private static String getViewClassNameFromIncludeTag(@NotNull ViewIdInfo viewIdInfo, @NotNull AndroidFacet facet) {
    String reference = getViewClassNameFromLayoutAttribute(viewIdInfo.getLayoutName(), facet);
    return reference == null ? SdkConstants.CLASS_VIEW : reference;
  }

  @Nullable
  private static String getViewClassNameFromMergeTag(@NotNull ViewIdInfo viewIdInfo, @NotNull AndroidFacet facet) {
    return getViewClassNameFromLayoutAttribute(viewIdInfo.getLayoutName(), facet);
  }

  @Nullable
  private static String getViewClassNameFromLayoutAttribute(@Nullable String layout, @NotNull AndroidFacet facet) {
    if (layout == null) {
      return null;
    }
    LocalResourceRepository moduleResources = ResourceRepositoryManager.getInstance(facet).getExistingAppResources();
    if (moduleResources == null) {
      return null;
    }
    ResourceUrl resourceUrl = ResourceUrl.parse(layout);
    if (resourceUrl == null || resourceUrl.type != ResourceType.LAYOUT) {
      return null;
    }
    BindingLayoutInfo info = Iterables.getFirst(moduleResources.getBindingLayoutInfo(resourceUrl.name), null);
    if (info == null) {
      return null;
    }
    return info.getQualifiedClassName();
  }

  /**
   * Utility method that implements Data Binding's logic to convert a file name to a Java Class name
   *
   * @param name The name of the file
   * @return The class name that will represent the given file
   */
  @NotNull
  public static String convertToJavaClassName(@NotNull String name) {
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
   * Utility method to convert a variable name into java field name.
   *
   * @param name The variable name.
   * @return The java field name for the given variable name.
   */
  @NotNull
  public static String convertToJavaFieldName(@NotNull String name) {
    int dotIndex = name.indexOf('.');
    if (dotIndex >= 0) {
      name = name.substring(0, dotIndex);
    }

    String[] split = name.split("[_-]");
    StringBuilder out = new StringBuilder();
    boolean first = true;
    for (String section : split) {
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
    return (!returnType.equals(PsiType.VOID)
            && psiMethod.getParameterList().getParametersCount() == 0
            && isPrefixedJavaIdentifier(psiMethod.getName(), "get"));
  }

  public static boolean isBooleanGetter(@NotNull PsiMethod psiMethod) {
    PsiType returnType = psiMethod.getReturnType();
    if (returnType == null) {
      return false; // Null return type indicates a constructor
    }
    return (returnType.equals(PsiType.BOOLEAN)
            && psiMethod.getParameterList().getParametersCount() == 0
            && isPrefixedJavaIdentifier(psiMethod.getName(), "is"));
  }

  public static boolean isSetter(@NotNull PsiMethod psiMethod) {
    PsiType returnType = psiMethod.getReturnType();
    if (returnType == null) {
      return false; // Null return type indicates a constructor
    }
    return (returnType.equals(PsiType.VOID)
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
    // TODO(132629996): Replace this with AndroidManifestUtils.getPackage(facet) once it has been updated to use
    //                  the merged manifest. The deprecated API used here has the potential to block the EDT.
    return MergedManifestManager.getSnapshot(facet).getPackage();
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
   * @param nameOrAlias a fully qualified name, or an alias as declared in an {@code <import>}, or an inner class of an alias
   * @param layoutData layout data containing import statements
   * @param qualifyJavaLang qualify names of java.lang classes
   * @return the qualified name of the class, otherwise, if {@code qualifyJavaLang} is false and {@code nameOrAlias} doesn't match any
   *     imports, the unqualified name of the class, or, if {@code qualifyJavaLang} is true and the class name cannot be resolved, null
   */
  @Nullable
  public static String getQualifiedType(@NotNull Project project,
                                        @NotNull String nameOrAlias,
                                        @NotNull BindingLayoutData layoutData,
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
            className = resolveImport(className, layoutData);
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
   * @param layoutData the layout data that may contain import statements to use for class resolution.
   * @return the fully qualified class name, or the original name if the first segment of {@code className} doesn't match
   *     any import statement.
   */
  @NotNull
  public static String resolveImport(@NotNull String className, @NotNull BindingLayoutData layoutData) {
    int dotOffset = className.indexOf('.');
    String firstSegment = dotOffset >= 0 ? className.substring(0, dotOffset) : className;
    String importedType = layoutData.resolveImport(firstSegment);
    if (importedType == null) {
      return className;
    }
    return dotOffset >= 0 ? importedType + className.substring(dotOffset) : importedType;
  }

  @Nullable
  public static XmlFile findXmlFile(@NotNull Project project, @NotNull BindingLayoutData bindingData) {
    return (XmlFile)PsiManager.getInstance(project).findFile(bindingData.getFile());
  }

  @Nullable
  private static XmlTag findDataTag(@NotNull Project project, @NotNull BindingLayoutData bindingData) {
    XmlFile xmlFile = findXmlFile(project, bindingData);
    if (xmlFile != null) {
      XmlTag rootTag = xmlFile.getRootTag();
      if (rootTag != null && rootTag.getName().equals("layout")) {
        return rootTag.findFirstSubTag("data");
      }
    }

    return null;
  }

  @Nullable
  public static XmlTag findVariableTag(@NotNull Project project, @NotNull BindingLayoutData bindingData, @NotNull String variableName) {
    XmlTag dataTag = findDataTag(project, bindingData);
    if (dataTag != null) {
      for (XmlTag tag : dataTag.getSubTags()) {
        if (tag.getName().equals("variable")) {
          String name = tag.getAttributeValue(SdkConstants.ATTR_NAME);
          if (name != null && StringUtil.unescapeXmlEntities(name).equals(variableName)) {
            return tag;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  public static XmlTag findImportTag(@NotNull Project project, @NotNull BindingLayoutData bindingData, @NotNull String simpleImportedName) {
    XmlTag dataTag = findDataTag(project, bindingData);
    if (dataTag != null) {
      for (XmlTag tag : dataTag.getSubTags()) {
        if (tag.getName().equals("import")) {
          String alias = tag.getAttributeValue(SdkConstants.ATTR_ALIAS);
          if (alias == null) {
            String type = tag.getAttributeValue(SdkConstants.ATTR_TYPE);
            if (type != null &&
                BindingLayoutData.Import.getImportedShortName(StringUtil.unescapeXmlEntities(type), null).equals(simpleImportedName)) {
              return tag;
            }
          } else if (StringUtil.unescapeXmlEntities(alias).equals(simpleImportedName)) {
            return tag;
          }
        }
      }
    }

    return null;
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
