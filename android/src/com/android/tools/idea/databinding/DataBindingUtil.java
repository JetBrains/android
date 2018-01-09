/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tools.idea.databinding.config.DataBindingConfiguration;
import com.android.tools.idea.lang.databinding.DbFile;
import com.android.tools.idea.lang.databinding.psi.DbTokenTypes;
import com.android.tools.idea.lang.databinding.psi.PsiDbConstantValue;
import com.android.tools.idea.lang.databinding.psi.PsiDbDefaults;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.res.DataBindingInfo;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.android.tools.idea.res.PsiDataBindingResourceItem;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.FileContentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.layout.Import;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.ATTR_ALIAS;

/**
 * Utility class that handles the interaction between Data Binding and the IDE.
 * <p/>
 * This class handles adding class finders and short names caches for DataBinding related code
 * completion etc.
 */
public class DataBindingUtil {
  static Logger LOG = Logger.getInstance("databinding");
  public static final String BR = "BR";
  private static AtomicLong ourDataBindingEnabledModificationCount = new AtomicLong(0);

  private static AtomicBoolean ourCreateInMemoryClasses = new AtomicBoolean(false);

  private static List<String> VIEW_PACKAGE_ELEMENTS = Arrays.asList(SdkConstants.VIEW, SdkConstants.VIEW_GROUP,
                                                                    SdkConstants.TEXTURE_VIEW, SdkConstants.SURFACE_VIEW);

  private static AtomicBoolean ourReadInMemoryClassGenerationSettings = new AtomicBoolean(false);

  private static void invalidateJavaCodeOnOpenDataBindingProjects() {
    ourDataBindingEnabledModificationCount.incrementAndGet();
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      DataBindingProjectComponent component = project.getComponent(DataBindingProjectComponent.class);
      if (component == null) {
        continue;
      }
      boolean invalidated = invalidateAllSources(component);
      if (!invalidated) {
        return;
      }
      PsiModificationTracker tracker = PsiManager.getInstance(project).getModificationTracker();
      if (tracker instanceof PsiModificationTrackerImpl) {
        ((PsiModificationTrackerImpl) tracker).incCounter();
      }
      FileContentUtil.reparseFiles(project, Collections.emptyList(), true);

    }
    ourDataBindingEnabledModificationCount.incrementAndGet();
  }

  public static boolean invalidateAllSources(DataBindingProjectComponent component) {
    boolean invalidated = false;
    for (AndroidFacet facet : component.getDataBindingEnabledFacets()) {
      LocalResourceRepository moduleResources = ModuleResourceRepository.getOrCreateInstance(facet);
      Map<String, DataBindingInfo> dataBindingResourceFiles = moduleResources.getDataBindingResourceFiles();
      if (dataBindingResourceFiles == null) {
        continue;
      }
      for (DataBindingInfo info : dataBindingResourceFiles.values()) {
        PsiClass psiClass = info.getPsiClass();
        if (psiClass != null) {
          PsiFile containingFile = psiClass.getContainingFile();
          if (containingFile != null) {
            containingFile.subtreeChanged();
            invalidated = true;
          }
        }
      }
    }
    return invalidated;
  }

  public static boolean inMemoryClassGenerationIsEnabled() {
    if (!ourReadInMemoryClassGenerationSettings.getAndSet(true)) {
      // just calculate, don't notify for the first one since we don't have anything to invalidate
      ourCreateInMemoryClasses.set(calculateEnableInMemoryClasses());
    }
    return ourCreateInMemoryClasses.get();
  }

  public static void recalculateEnableInMemoryClassGeneration() {
    boolean newValue = calculateEnableInMemoryClasses();
    boolean oldValue = ourCreateInMemoryClasses.getAndSet(newValue);
    if (newValue != oldValue) {
      LOG.debug("Data binding in memory completion value change. (old, new)", oldValue, newValue);
      ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(DataBindingUtil::invalidateJavaCodeOnOpenDataBindingProjects));
    }
  }

  private static boolean calculateEnableInMemoryClasses() {
    DataBindingConfiguration config = DataBindingConfiguration.getInstance();
    return config.CODE_NAVIGATION_MODE == DataBindingConfiguration.CodeNavigationMode.XML;
  }

  /**
   * Package private class used by BR class finder and BR short names cache to create a BR file on demand.
   *
   * @param facet The facet for which the BR file is necessary.
   * @return The LightBRClass that belongs to the given AndroidFacet
   */
  static LightBrClass getOrCreateBrClassFor(AndroidFacet facet) {
    ModuleDataBinding dataBinding = ModuleDataBinding.getInstance(facet);
    assert dataBinding != null;

    LightBrClass existing = dataBinding.getLightBrClass();
    if (existing == null) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (facet) {
        existing = dataBinding.getLightBrClass();
        if (existing == null) {
          existing = new LightBrClass(PsiManager.getInstance(facet.getModule().getProject()), facet);
          dataBinding.setLightBrClass(existing);
        }
      }
    }
    return existing;
  }

  static PsiType parsePsiType(String text, AndroidFacet facet, PsiElement context) {
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
        return SdkConstants.DATA_BINDING_VIEW_STUB_PROXY;
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
    LocalResourceRepository moduleResources = ModuleResourceRepository.findExistingInstance(facet);
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

  public static PsiClass getOrCreatePsiClass(DataBindingInfo info) {
    PsiClass psiClass = info.getPsiClass();
    if (psiClass == null) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (info) {
        psiClass = info.getPsiClass();
        if (psiClass == null) {
          psiClass = new LightBindingClass(info.getFacet(), PsiManager.getInstance(info.getProject()), info);
          info.setPsiClass(psiClass);
        }
      }
    }
    return psiClass;
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

  /**
   * Called by the {@linkplain AndroidFacet} to refresh its data binding status.
   *
   * @param facet the {@linkplain AndroidFacet} whose IdeaProject is just set.
   */
  public static void refreshDataBindingStatus(@NotNull AndroidFacet facet) {
    AndroidModel androidModel = facet.getAndroidModel();
    if (androidModel != null) {
      boolean wasEnabled = ModuleDataBinding.getInstance(facet).isEnabled();
      boolean enabled = androidModel.getDataBindingEnabled();
      if (enabled != wasEnabled) {
        ModuleDataBinding.getInstance(facet).setEnabled(enabled);
        ourDataBindingEnabledModificationCount.incrementAndGet();
      }
    }
  }

  @Nullable
  public static String getBindingExprDefault(@NotNull XmlAttribute psiAttribute) {
    XmlAttributeValue attrValue = psiAttribute.getValueElement();
    if (attrValue instanceof PsiLanguageInjectionHost) {
      final Ref<PsiElement> injections = Ref.create();
      InjectedLanguageUtil.enumerate(attrValue, (injectedPsi, places) -> {
        if (injectedPsi instanceof DbFile) {
          injections.set(injectedPsi);
        }
      });
      if (injections.get() != null) {
        PsiDbDefaults defaults = PsiTreeUtil.getChildOfType(injections.get(), PsiDbDefaults.class);
        if (defaults != null) {
          PsiDbConstantValue constantValue = defaults.getConstantValue();
          ASTNode stringLiteral = constantValue.getNode().findChildByType(DbTokenTypes.STRING_LITERAL);
          if (stringLiteral == null) {
            return constantValue.getText();
          } else {
            String text = stringLiteral.getText();
            if (text.length() > 1) {
              return text.substring(1, text.length() - 1);  // return unquoted string literal.
            } else {
              return text;
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * @param exprn Data binding expression enclosed in @{}
   */
  @Nullable
  public static String getBindingExprDefault(@NotNull String exprn) {
    if (!exprn.contains(DbTokenTypes.DEFAULT_KEYWORD.toString())) {
      // A fast check since many expressions would likely not have a default.
      return null;
    }
    Pattern defaultCheck = Pattern.compile(",\\s*default\\s*=\\s*");
    int index = 0;
    Matcher matcher = defaultCheck.matcher(exprn);
    while (matcher.find()) {
      index = matcher.end();
    }
    String def = exprn.substring(index, exprn.length() - 1).trim();  // remove the trailing "}"
    if (def.startsWith("\"") && def.endsWith("\"")) {
      def = def.substring(1, def.length() - 1);       // Unquote the string.
    }
    return def;
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
   * Tracker that changes when a facet's data binding enabled value changes
   */
  public static ModificationTracker DATA_BINDING_ENABLED_TRACKER = () -> ourDataBindingEnabledModificationCount.longValue();
}
