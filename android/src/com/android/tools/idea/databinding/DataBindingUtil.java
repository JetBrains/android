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

import static com.android.SdkConstants.ATTR_ALIAS;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.lang.databinding.DataBindingExpressionProvider;
import com.android.tools.idea.lang.databinding.DataBindingExpressionUtil;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.res.DataBindingInfo;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.PsiDataBindingResourceItem;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.GenericAttributeValue;
import java.util.List;
import org.jetbrains.android.dom.layout.Import;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point for supporting Data Binding methods.
 */
interface DataBindingSupport {
  @NotNull
  DataBindingMode getDataBindingMode(@NotNull AndroidFacet facet);

  @NotNull
  ModificationTracker getDataBindingEnabledTracker();
}

/**
 * Utility class that handles the interaction between Data Binding and the IDE.
 */
public class DataBindingUtil {
  public static final String BR = "BR";
  private static List<String> VIEW_PACKAGE_ELEMENTS = ImmutableList.of(SdkConstants.VIEW, SdkConstants.VIEW_GROUP,
                                                                       SdkConstants.TEXTURE_VIEW, SdkConstants.SURFACE_VIEW);

  /**
   * Returns the first implementation for this data binding extension point, but will be null if the
   * data binding plugin isn't enabled or no implementation is found.
   */
  @Nullable
  static DataBindingSupport getDataBindingSupport() {
    List<DataBindingSupport> extensionList =
      new ExtensionPointName<DataBindingSupport>("com.android.tools.idea.databinding.dataBindingSupport").getExtensionList();
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

  @Nullable
  static PsiType parsePsiType(@NotNull String text, @NotNull AndroidFacet facet, @Nullable PsiElement context) {
    PsiElementFactory instance = PsiElementFactory.SERVICE.getInstance(facet.getModule().getProject());
    try {
      PsiType type = instance.createTypeFromText(text, context);
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

  public static PsiType resolveViewPsiType(DataBindingInfo.ViewWithId viewWithId, AndroidFacet facet) {
    String viewClassName = getViewClassName(viewWithId.tag, facet);
    if (StringUtil.isNotEmpty(viewClassName)) {
      return parsePsiType(viewClassName, facet, null);
    }
    return null;
  }

  /**
   * Receives an {@linkplain XmlTag} and returns the View class that is represented by the tag.
   * May return null if it cannot find anything reasonable (e.g. it is a merge but does not have data binding)
   *
   * @param tag The {@linkplain XmlTag} that represents the View
   */
  @Nullable
  private static String getViewClassName(XmlTag tag, AndroidFacet facet) {
    final String elementName = getViewName(tag);
    if (elementName != null && elementName.indexOf('.') == -1) {
      if (VIEW_PACKAGE_ELEMENTS.contains(elementName)) {
        return SdkConstants.VIEW_PKG_PREFIX + elementName;
      } else if (SdkConstants.WEB_VIEW.equals(elementName)) {
        return SdkConstants.ANDROID_WEBKIT_PKG + elementName;
      } else if (SdkConstants.VIEW_MERGE.equals(elementName)) {
        return getViewClassNameFromMerge(tag, facet);
      } else if (SdkConstants.VIEW_INCLUDE.equals(elementName)) {
        return getViewClassNameFromInclude(tag, facet);
      } else if (SdkConstants.VIEW_STUB.equals(elementName)) {
        DataBindingMode mode = getDataBindingMode(facet);
        return mode.viewStubProxy;
      }
      return SdkConstants.WIDGET_PKG_PREFIX + elementName;
    } else {
      return elementName;
    }
  }

  private static String getViewClassNameFromInclude(XmlTag tag, AndroidFacet facet) {
    String reference = getViewClassNameFromLayoutReferenceTag(tag, facet);
    return reference == null ? SdkConstants.CLASS_VIEW : reference;
  }

  private static String getViewClassNameFromMerge(XmlTag tag, AndroidFacet facet) {
    return getViewClassNameFromLayoutReferenceTag(tag, facet);
  }

  private static String getViewClassNameFromLayoutReferenceTag(XmlTag tag, AndroidFacet facet) {
    String layout = tag.getAttributeValue(SdkConstants.ATTR_LAYOUT);
    if (layout == null) {
      return null;
    }
    LocalResourceRepository moduleResources = ResourceRepositoryManager.getOrCreateInstance(facet).getModuleResources(false);
    if (moduleResources == null) {
      return null;
    }
    ResourceUrl resourceUrl = ResourceUrl.parse(layout);
    if (resourceUrl == null || resourceUrl.type != ResourceType.LAYOUT) {
      return null;
    }
    DataBindingInfo info = moduleResources.getDataBindingInfoForLayout(resourceUrl.name);
    if (info == null) {
      return null;
    }
    return info.getQualifiedName();
  }

  @Nullable // when passed <view/>
  private static String getViewName(XmlTag tag) {
    String viewName = tag.getName();
    if (SdkConstants.VIEW_TAG.equals(viewName)) {
      viewName = tag.getAttributeValue(SdkConstants.ATTR_CLASS, SdkConstants.ANDROID_URI);
    }
    return viewName;
  }

  /**
   * Utility method that implements Data Binding's logic to convert a file name to a Java Class name
   *
   * @param name The name of the file
   * @return The class name that will represent the given file
   */
  public static String convertToJavaClassName(String name) {
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
  public static String convertToJavaFieldName(String name) {
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

  /**
   * Returns the qualified name for the BR file for the given Facet.
   *
   * @param facet The {@linkplain AndroidFacet} to check.
   * @return The qualified name for the BR class of the given Android Facet.
   */
  public static String getBrQualifiedName(AndroidFacet facet) {
    return getGeneratedPackageName(facet) + "." + BR;
  }

  /**
   * Returns the package name that will be use to generate R file or BR file.
   *
   * @param facet The {@linkplain AndroidFacet} to check.
   * @return The package name that can be used to generate R and BR classes.
   */
  public static String getGeneratedPackageName(AndroidFacet facet) {
    return MergedManifest.get(facet).getPackage();
  }

  @Nullable
  public static String getBindingExprDefault(@NotNull XmlAttribute psiAttribute) {
    DataBindingExpressionProvider expressionProvider = DataBindingExpressionUtil.getDataBindingExpressionProvider();
    if (expressionProvider != null) {
      return expressionProvider.getBindingExprDefault(psiAttribute);
    }
    return null;
  }

  /**
   * @param exprn Data binding expression enclosed in @{}
   */
  @Nullable
  public static String getBindingExprDefault(@NotNull String exprn) {
    DataBindingExpressionProvider expressionProvider = DataBindingExpressionUtil.getDataBindingExpressionProvider();
    if (expressionProvider != null) {
      return expressionProvider.getBindingExprDefault(exprn);
    }
    return null;
  }

  public static boolean isBindingExpression(@NotNull String string) {
    return string.startsWith(SdkConstants.PREFIX_BINDING_EXPR) || string.startsWith(SdkConstants.PREFIX_TWOWAY_BINDING_EXPR);
  }

  @Nullable/*invalid type*/
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

  @Nullable
  public static String getAlias(@NotNull PsiDataBindingResourceItem anImport) {
    return getAlias(anImport.getTypeDeclaration(), anImport.getExtra(ATTR_ALIAS));
  }

  private static String getAlias(@Nullable String type, @Nullable String alias) {
    if (alias != null || type == null) {
      return alias;
    }
    int i = type.lastIndexOf('.');
    int d = type.lastIndexOf('$');
    i = i > d ? i : d;
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
   * @param dataBindingInfo for getting the list of {@code <import>} tags
   * @param qualifyJavaLang qualify names of java.lang classes
   * @return the qualified name of the class, otherwise, if {@code qualifyJavaLang} is false and {@code nameOrAlias} doesn't match any
   *     imports, the unqualified name of the class, or, if {@code qualifyJavaLang} is true and the class name cannot be resolved, null
   */
  @Nullable
  public static String getQualifiedType(@Nullable String nameOrAlias, @Nullable DataBindingInfo dataBindingInfo, boolean qualifyJavaLang) {
    if (nameOrAlias == null || dataBindingInfo == null) {
      return nameOrAlias;
    }

    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(dataBindingInfo.getProject());
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
            className = resolveImport(className, dataBindingInfo);
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
   * @param dataBindingInfo the data binding information containing the import statements to use for class resolution.
   * @return the fully qualified class name, or the original name if the first segment of {@code className} doesn't match
   *     any import statement.
   */
  @NotNull
  public static String resolveImport(@NotNull String className, @Nullable DataBindingInfo dataBindingInfo) {
    if (dataBindingInfo != null) {
      int dotOffset = className.indexOf('.');
      String firstSegment = dotOffset >= 0 ? className.substring(0, dotOffset) : className;
      String importedType = dataBindingInfo.resolveImport(firstSegment);
      if (importedType != null) {
        return dotOffset >= 0 ? importedType + className.substring(dotOffset) : importedType;
      }
    }
    return className;
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
