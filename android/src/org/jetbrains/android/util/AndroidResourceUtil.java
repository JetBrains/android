/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.util;

import static com.android.SdkConstants.AAPT_PREFIX;
import static com.android.SdkConstants.AAPT_URI;
import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.APP_PREFIX;
import static com.android.SdkConstants.ATTR_COLOR;
import static com.android.SdkConstants.ATTR_DRAWABLE;
import static com.android.SdkConstants.ATTR_FORMAT;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.CLASS_R;
import static com.android.SdkConstants.CONSTRAINT_REFERENCED_IDS;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.TAG_ATTR;
import static com.android.SdkConstants.TAG_DECLARE_STYLEABLE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_LAYOUT;
import static com.android.SdkConstants.TAG_STRING;
import static com.android.SdkConstants.TOOLS_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VIEW_MERGE;
import static com.android.SdkConstants.XMLNS_PREFIX;
import static com.android.builder.model.AaptOptions.Namespacing;
import static com.android.resources.ResourceType.ARRAY;
import static com.android.resources.ResourceType.ATTR;
import static com.android.resources.ResourceType.BOOL;
import static com.android.resources.ResourceType.COLOR;
import static com.android.resources.ResourceType.DIMEN;
import static com.android.resources.ResourceType.DRAWABLE;
import static com.android.resources.ResourceType.FRACTION;
import static com.android.resources.ResourceType.ID;
import static com.android.resources.ResourceType.INTEGER;
import static com.android.resources.ResourceType.LAYOUT;
import static com.android.resources.ResourceType.NAVIGATION;
import static com.android.resources.ResourceType.PLURALS;
import static com.android.resources.ResourceType.STRING;
import static com.android.resources.ResourceType.STYLE;
import static com.android.resources.ResourceType.STYLEABLE;
import static com.android.resources.ResourceType.fromXmlTag;
import static com.android.tools.lint.detector.api.Lint.stripIdPrefix;
import static com.intellij.openapi.command.WriteCommandAction.writeCommandAction;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.FileResourceNameValidator;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ValueXmlHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.apk.viewer.ApkFileSystem;
import com.android.tools.idea.kotlin.AndroidKtPsiUtilsKt;
import com.android.tools.idea.projectsystem.LightResourceClassService;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.res.AndroidInternalRClassFinder;
import com.android.tools.idea.res.AndroidRClassBase;
import com.android.tools.idea.res.PsiResourceItem;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.res.StateList;
import com.android.tools.idea.res.StateListState;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.jetbrains.android.AndroidFileTemplateProvider;
import org.jetbrains.android.actions.CreateTypedResourceFileAction;
import org.jetbrains.android.augment.ManifestClass;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.dom.color.ColorSelector;
import org.jetbrains.android.dom.drawable.DrawableSelector;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.Item;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.references.ReferenceUtilKt;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtSimpleNameExpression;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourceUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.util.AndroidResourceUtil");

  public static final Set<ResourceType> VALUE_RESOURCE_TYPES = EnumSet.of(DRAWABLE, COLOR, DIMEN,
                                                                          STRING, STYLE, ARRAY,
                                                                          PLURALS, ID, BOOL,
                                                                          INTEGER, FRACTION,
                                                                          // For aliases only
                                                                          LAYOUT);

  public static final Set<ResourceType> ALL_VALUE_RESOURCE_TYPES = EnumSet.noneOf(ResourceType.class);

  static final String ROOT_TAG_PROPERTY = "ROOT_TAG";
  static final String LAYOUT_WIDTH_PROPERTY = "LAYOUT_WIDTH";
  static final String LAYOUT_HEIGHT_PROPERTY = "LAYOUT_HEIGHT";

  private static final String RESOURCE_CLASS_SUFFIX = "." + AndroidUtils.R_CLASS_NAME;

  /**
   * Comparator which orders {@link PsiElement} items into a priority order most suitable for presentation
   * to the user; for example, it prefers base resource folders such as {@code values/} over resource
   * folders such as {@code values-en-rUS}
   */
  public static final Comparator<PsiElement> RESOURCE_ELEMENT_COMPARATOR = (e1, e2) -> {
    if (e1 instanceof LazyValueResourceElementWrapper && e2 instanceof LazyValueResourceElementWrapper) {
      return ((LazyValueResourceElementWrapper)e1).compareTo((LazyValueResourceElementWrapper)e2);
    }

    PsiFile file1 = e1.getContainingFile();
    PsiFile file2 = e2.getContainingFile();
    int delta = compareResourceFiles(file1, file2);
    if (delta != 0) {
      return delta;
    }
    return e1.getTextOffset() - e2.getTextOffset();
  };

  /**
   * Comparator for {@link ResolveResult} using {@link #RESOURCE_ELEMENT_COMPARATOR} on the result PSI element.
   */
  public static final Comparator<ResolveResult> RESOLVE_RESULT_COMPARATOR =
    Comparator.nullsLast(Comparator.comparing(ResolveResult::getElement, RESOURCE_ELEMENT_COMPARATOR));

  private AndroidResourceUtil() {
  }

  @NotNull
  public static String normalizeXmlResourceValue(@NotNull String value) {
    return ValueXmlHelper.escapeResourceString(value, false);
  }

  static {
    ALL_VALUE_RESOURCE_TYPES.addAll(VALUE_RESOURCE_TYPES);
    ALL_VALUE_RESOURCE_TYPES.add(ATTR);
    ALL_VALUE_RESOURCE_TYPES.add(STYLEABLE);
  }

  public static String packageToRClass(@NotNull String packageName) {
    return packageName + RESOURCE_CLASS_SUFFIX;
  }

  @NotNull
  public static PsiField[] findResourceFields(@NotNull AndroidFacet facet,
                                              @NotNull String resClassName,
                                              @NotNull String resourceName,
                                              boolean onlyInOwnPackages) {
    return findResourceFields(facet, resClassName, Collections.singleton(resourceName), onlyInOwnPackages);
  }

  /**
   * Like {@link #findResourceFields(AndroidFacet, String, String, boolean)} but
   * can match than more than a single field name
   */
  @NotNull
  public static PsiField[] findResourceFields(@NotNull AndroidFacet facet,
                                              @NotNull String resClassName,
                                              @NotNull Collection<String> resourceNames,
                                              boolean onlyInOwnPackages) {
    final List<PsiField> result = new ArrayList<>();
    for (PsiClass rClass : findRJavaClasses(facet, onlyInOwnPackages)) {
      findResourceFieldsFromClass(rClass, resClassName, resourceNames, result);
    }
    return result.toArray(PsiField.EMPTY_ARRAY);
  }

  private static void findResourceFieldsFromClass(@NotNull PsiClass rClass,
      @NotNull String resClassName, @NotNull Collection<String> resourceNames,
      @NotNull List<PsiField> result) {
    final PsiClass resourceTypeClass = rClass.findInnerClassByName(resClassName, false);

    if (resourceTypeClass != null) {
      for (String resourceName : resourceNames) {
        String fieldName = getRJavaFieldName(resourceName);
        final PsiField field = resourceTypeClass.findFieldByName(fieldName, false);

        if (field != null) {
          result.add(field);
        }
      }
    }
  }

  /**
   * Finds all R classes that contain fields for resources from the given module.
   *
   * @param facet {@link AndroidFacet} of the module to find classes for
   * @param onlyInOwnPackages whether to limit results to "canonical" R classes, that is classes defined in the same module as the module
   *                          they describe. When sync-time R.java generation is used, sources for the same fully-qualified R class are
   *                          generated both in the owning module and all modules depending on it.
   * @return
   */
  @NotNull
  private static Collection<? extends PsiClass> findRJavaClasses(@NotNull AndroidFacet facet, boolean onlyInOwnPackages) {
    final Module module = facet.getModule();
    final Project project = module.getProject();
    if (Manifest.getMainManifest(facet) == null) {
      return Collections.emptySet();
    }

    LightResourceClassService resourceClassService =
      ProjectSystemUtil.getProjectSystem(facet.getModule().getProject()).getLightResourceClassService();

    return resourceClassService.getLightRClassesContainingModuleResources(module);
  }

  @NotNull
  public static PsiField[] findResourceFieldsForFileResource(@NotNull PsiFile file, boolean onlyInOwnPackages) {
    final AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return PsiField.EMPTY_ARRAY;
    }

    final String resourceType = ModuleResourceManagers.getInstance(facet).getLocalResourceManager().getFileResourceType(file);
    if (resourceType == null) {
      return PsiField.EMPTY_ARRAY;
    }

    final String resourceName = AndroidBuildCommonUtils.getResourceName(resourceType, file.getName());
    return findResourceFields(facet, resourceType, resourceName, onlyInOwnPackages);
  }

  @NotNull
  public static PsiField[] findResourceFieldsForValueResource(XmlTag tag, boolean onlyInOwnPackages) {
    final AndroidFacet facet = AndroidFacet.getInstance(tag);
    if (facet == null) {
      return PsiField.EMPTY_ARRAY;
    }

    ResourceFolderType fileResType = ResourceHelper.getFolderType(tag.getContainingFile());
    final ResourceType resourceType = fileResType == ResourceFolderType.VALUES
                                ? getResourceTypeForResourceTag(tag)
                                : null;
    if (resourceType == null) {
      return PsiField.EMPTY_ARRAY;
    }

    String name = tag.getAttributeValue(ATTR_NAME);
    if (name == null) {
      return PsiField.EMPTY_ARRAY;
    }

    return findResourceFields(facet, resourceType.getName(), name, onlyInOwnPackages);
  }

  @NotNull
  public static PsiField[] findStyleableAttributeFields(XmlTag tag, boolean onlyInOwnPackages) {
    String tagName = tag.getName();
    if (TAG_DECLARE_STYLEABLE.equals(tagName)) {
      String styleableName = tag.getAttributeValue(ATTR_NAME);
      if (styleableName == null) {
        return PsiField.EMPTY_ARRAY;
      }
      AndroidFacet facet = AndroidFacet.getInstance(tag);
      if (facet == null) {
        return PsiField.EMPTY_ARRAY;
      }
      Set<String> names = Sets.newHashSet();
      for (XmlTag attr : tag.getSubTags()) {
        if (TAG_ATTR.equals(attr.getName())) {
          String attrName = attr.getAttributeValue(ATTR_NAME);
          if (attrName != null) {
            names.add(styleableName + '_' + attrName);
          }
        }
      }
      if (!names.isEmpty()) {
        return findResourceFields(facet, STYLEABLE.getName(), names, onlyInOwnPackages);
      }
    } else if (TAG_ATTR.equals(tagName)) {
      XmlTag parentTag = tag.getParentTag();
      if (parentTag != null && TAG_DECLARE_STYLEABLE.equals(parentTag.getName())) {
        String styleName = parentTag.getAttributeValue(ATTR_NAME);
        String attributeName = tag.getAttributeValue(ATTR_NAME);
        AndroidFacet facet = AndroidFacet.getInstance(tag);
        if (facet != null && styleName != null && attributeName != null) {
          return findResourceFields(facet, STYLEABLE.getName(), styleName + '_' + attributeName, onlyInOwnPackages);
        }
      }
    }

    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public static String getRJavaFieldName(@NotNull String resourceName) {
    if (resourceName.indexOf('.') == -1) {
      return resourceName;
    }
    final String[] identifiers = resourceName.split("\\.");
    final StringBuilder result = new StringBuilder(resourceName.length());

    for (int i = 0, n = identifiers.length; i < n; i++) {
      result.append(identifiers[i]);
      if (i < n - 1) {
        result.append('_');
      }
    }
    return result.toString();
  }

  public static boolean isCorrectAndroidResourceName(@NotNull String resourceName) {
    // TODO: No, we need to check per resource folder type here. There is a validator for this!
    if (resourceName.isEmpty()) {
      return false;
    }
    if (resourceName.startsWith(".") || resourceName.endsWith(".")) {
      return false;
    }
    final String[] identifiers = resourceName.split("\\.");

    for (String identifier : identifiers) {
      if (!StringUtil.isJavaIdentifier(identifier)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public static ResourceType getResourceTypeForResourceTag(@NotNull XmlTag tag) {
    return fromXmlTag(tag, XmlTag::getName, XmlTag::getAttributeValue);
  }

  @Nullable
  public static String getResourceClassName(@NotNull PsiField field) {
    final PsiClass resourceClass = field.getContainingClass();

    if (resourceClass != null) {
      final PsiClass parentClass = resourceClass.getContainingClass();

      if (parentClass != null &&
          AndroidUtils.R_CLASS_NAME.equals(parentClass.getName()) &&
          parentClass.getContainingClass() == null) {
        return resourceClass.getName();
      }
    }
    return null;
  }

  // result contains XmlAttributeValue or PsiFile
  @NotNull
  public static List<PsiElement> findResourcesByField(@NotNull PsiField field) {
    final AndroidFacet facet = AndroidFacet.getInstance(field);
    return facet != null
           ? ModuleResourceManagers.getInstance(facet).getLocalResourceManager().findResourcesByField(field)
           : Collections.emptyList();
  }

  public static boolean isResourceField(@NotNull PsiField field) {
    PsiClass rClass = field.getContainingClass();
    if (rClass == null) return false;
    rClass = rClass.getContainingClass();
    if (rClass != null && AndroidUtils.R_CLASS_NAME.equals(rClass.getName())) {
      AndroidFacet facet = AndroidFacet.getInstance(field);
      if (facet != null) {
        if (isRJavaClass(rClass)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isStringResource(@NotNull XmlTag tag) {
    return tag.getName().equals(TAG_STRING) && tag.getAttribute(ATTR_NAME) != null;
  }

  @NotNull
  public static PsiField[] findIdFields(@NotNull XmlAttributeValue value) {
    if (value.getParent() instanceof XmlAttribute) {
      return findIdFields((XmlAttribute)value.getParent());
    }
    return PsiField.EMPTY_ARRAY;
  }

  public static boolean isIdDeclaration(@Nullable String attrValue) {
    return attrValue != null && attrValue.startsWith(NEW_ID_PREFIX);
  }

  public static boolean isIdReference(@Nullable String attrValue) {
    return attrValue != null && attrValue.startsWith(ID_PREFIX);
  }

  public static boolean isIdDeclaration(@NotNull XmlAttributeValue value) {
    return isIdDeclaration(value.getValue());
  }

  public static boolean isConstraintReferencedIds(@Nullable String nsURI, @Nullable String nsPrefix, @Nullable String key) {
    return AUTO_URI.equals(nsURI) && APP_PREFIX.equals(nsPrefix) && CONSTRAINT_REFERENCED_IDS.equals(key);
  }

  public static boolean isConstraintReferencedIds(@NotNull XmlAttributeValue value) {
    PsiElement parent = value.getParent();
    if (parent instanceof XmlAttribute) {
      XmlAttribute xmlAttribute = (XmlAttribute) parent;

      String nsURI = xmlAttribute.getNamespace();
      String nsPrefix = xmlAttribute.getNamespacePrefix();
      String key = xmlAttribute.getLocalName();

      return isConstraintReferencedIds(nsURI, nsPrefix, key);
    }
    return false;
  }

  @NotNull
  public static PsiField[] findIdFields(@NotNull XmlAttribute attribute) {
    XmlAttributeValue valueElement = attribute.getValueElement();
    String value = attribute.getValue();

    if (valueElement != null && value != null && isIdDeclaration(valueElement)) {
      final String id = getResourceNameByReferenceText(value);

      if (id != null) {
        final AndroidFacet facet = AndroidFacet.getInstance(attribute);

        if (facet != null) {
          return findResourceFields(facet, ID.getName(), id, false);
        }
      }
    }
    return PsiField.EMPTY_ARRAY;
  }

  /**
   * Generate an extension-less file name based on a passed string, that should pass
   * validation as a resource file name by Gradle plugin.
   * <p/>
   * For names validation in the Gradle plugin, see {@link FileResourceNameValidator}
   */
  @NotNull
  public static String getValidResourceFileName(@NotNull String base) {
    return base.replace('-', '_').replace(' ', '_').toLowerCase(Locale.US);
  }

  @Nullable
  public static String getResourceNameByReferenceText(@NotNull String text) {
    int i = text.indexOf('/');
    if (i < text.length() - 1) {
      return text.substring(i + 1);
    }
    return null;
  }

  @NotNull
  public static ResourceElement addValueResource(@NotNull final ResourceType resType, @NotNull final Resources resources,
                                                 @Nullable final String value) {
    switch (resType) {
      case STRING:
        return resources.addString();
      case PLURALS:
        return resources.addPlurals();
      case DIMEN:
        if (value != null && value.trim().endsWith("%")) {
          // Deals with dimension values in the form of percentages, e.g. "65%"
          final Item item = resources.addItem();
          item.getType().setStringValue(DIMEN.getName());
          return item;
        }
        if (value != null && value.indexOf('.') > 0) {
          // Deals with dimension values in the form of floating-point numbers, e.g. "0.24"
          final Item item = resources.addItem();
          item.getType().setStringValue(DIMEN.getName());
          item.getFormat().setStringValue("float");
          return item;
        }
        return resources.addDimen();
      case COLOR:
        return resources.addColor();
      case DRAWABLE:
        return resources.addDrawable();
      case STYLE:
        return resources.addStyle();
      case ARRAY:
        // todo: choose among string-array, integer-array and array
        return resources.addStringArray();
      case INTEGER:
        return resources.addInteger();
      case FRACTION:
        return resources.addFraction();
      case BOOL:
        return resources.addBool();
      case ID:
        final Item item = resources.addItem();
        item.getType().setValue(ID.getName());
        return item;
      case STYLEABLE:
        return resources.addDeclareStyleable();
      default:
        throw new IllegalArgumentException("Incorrect resource type");
    }
  }

  @NotNull
  public static List<VirtualFile> getResourceSubdirs(@NotNull ResourceFolderType resourceType,
                                                     @NotNull Collection<VirtualFile> resourceDirs) {
    final List<VirtualFile> dirs = new ArrayList<>();

    for (VirtualFile resourcesDir : resourceDirs) {
      if (resourcesDir == null || !resourcesDir.isValid()) {
        continue;
      }
      for (VirtualFile child : resourcesDir.getChildren()) {
        ResourceFolderType type = ResourceFolderType.getFolderType(child.getName());
        if (resourceType.equals(type)) dirs.add(child);
      }
    }
    return dirs;
  }

  @Nullable
  public static String getDefaultResourceFileName(@NotNull ResourceType type) {
    if (PLURALS == type || STRING == type) {
      return "strings.xml";
    }
    if (VALUE_RESOURCE_TYPES.contains(type)) {

      if (type == LAYOUT
          // Lots of unit tests assume drawable aliases are written in "drawables.xml" but going
          // forward lets combine both layouts and drawables in refs.xml as is done in the templates:
          || type == DRAWABLE && !ApplicationManager.getApplication().isUnitTestMode()) {
        return "refs.xml";
      }

      return type.getName() + "s.xml";
    }
    if (ATTR == type ||
        STYLEABLE == type) {
      return "attrs.xml";
    }
    return null;
  }

  @NotNull
  public static List<ResourceElement> getValueResourcesFromElement(@NotNull ResourceType resourceType, @NotNull Resources resources) {
    final List<ResourceElement> result = new ArrayList<>();

    //noinspection EnumSwitchStatementWhichMissesCases
    switch (resourceType) {
      case STRING:
        result.addAll(resources.getStrings());
        break;
      case PLURALS:
        result.addAll(resources.getPluralses());
        break;
      case DRAWABLE:
        result.addAll(resources.getDrawables());
        break;
      case COLOR:
        result.addAll(resources.getColors());
        break;
      case DIMEN:
        result.addAll(resources.getDimens());
        break;
      case STYLE:
        result.addAll(resources.getStyles());
        break;
      case ARRAY:
        result.addAll(resources.getStringArrays());
        result.addAll(resources.getIntegerArrays());
        result.addAll(resources.getArrays());
        break;
      case INTEGER:
        result.addAll(resources.getIntegers());
        break;
      case FRACTION:
        result.addAll(resources.getFractions());
        break;
      case BOOL:
        result.addAll(resources.getBools());
        break;
      default:
        break;
    }

    for (Item item : resources.getItems()) {
      String type = item.getType().getValue();
      if (resourceType.getName().equals(type)) {
        result.add(item);
      }
    }
    return result;
  }

  public static boolean isInResourceSubdirectory(@NotNull PsiFile file, @Nullable String resourceType) {
    file = file.getOriginalFile();
    PsiDirectory dir = file.getContainingDirectory();
    if (dir == null) return false;
    return isResourceSubdirectory(dir, resourceType);
  }

  public static boolean isResourceSubdirectory(@NotNull PsiDirectory directory, @Nullable String resourceType) {
    PsiDirectory dir = directory;

    String dirName = dir.getName();
    if (resourceType != null) {
      int typeLength = resourceType.length();
      int dirLength = dirName.length();
      if (dirLength < typeLength || !dirName.startsWith(resourceType) || dirLength > typeLength && dirName.charAt(typeLength) != '-') {
        return false;
      }
    }
    dir = dir.getParent();

    if (dir == null) {
      return false;
    }
    if ("default".equals(dir.getName())) {
      dir = dir.getParentDirectory();
    }
    return dir != null && isResourceDirectory(dir);
  }

  public static boolean isLocalResourceDirectory(@NotNull VirtualFile dir, @NotNull Project project) {
    final Module module = ModuleUtilCore.findModuleForFile(dir, project);

    if (module != null) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      return facet != null && ModuleResourceManagers.getInstance(facet).getLocalResourceManager().isResourceDir(dir);
    }
    return false;
  }

  public static boolean isResourceFile(@NotNull VirtualFile file, @NotNull AndroidFacet facet) {
    final VirtualFile parent = file.getParent();
    final VirtualFile resDir = parent != null ? parent.getParent() : null;
    return resDir != null && ModuleResourceManagers.getInstance(facet).getLocalResourceManager().isResourceDir(resDir);
  }

  public static boolean isResourceDirectory(@NotNull PsiDirectory directory) {
    PsiDirectory dir = directory;
    // check facet settings
    VirtualFile vf = dir.getVirtualFile();

    if (isLocalResourceDirectory(vf, dir.getProject())) {
      return true;
    }

    if (!FD_RES.equals(dir.getName())) return false;
    dir = dir.getParent();
    if (dir != null) {
      String protocol = vf.getFileSystem().getProtocol();
      // TODO: Figure out a better way to check if a directory belongs to proto AAR resources.
      if (protocol.equals(JarFileSystem.PROTOCOL) || protocol.equals(ApkFileSystem.PROTOCOL)) {
          return true; // The file belongs either to res.apk or a source attachment JAR of a library.
      }

      if (dir.findFile(FN_ANDROID_MANIFEST_XML) != null) {
        return true;
      }
      // The method can be invoked for a framework resource directory, so we should check it.
      dir = dir.getParent();
      if (dir != null) {
        if (containsAndroidJar(dir)) return true;
        dir = dir.getParent();
        if (dir != null) {
          return containsAndroidJar(dir);
        }
      }
    }
    return false;
  }

  private static boolean containsAndroidJar(@NotNull PsiDirectory psiDirectory) {
    return psiDirectory.findFile(FN_FRAMEWORK_LIBRARY) != null;
  }

  public static boolean isRJavaClass(@NotNull PsiClass psiClass) {
    return psiClass instanceof AndroidRClassBase;
  }

  public static boolean isManifestClass(@NotNull PsiClass psiClass) {
    return psiClass instanceof ManifestClass;
  }

  public static boolean createValueResource(@NotNull Project project,
                                            @NotNull VirtualFile resDir,
                                            @NotNull String resourceName,
                                            @Nullable String resourceValue,
                                            @NotNull final ResourceType resourceType,
                                            @NotNull String fileName,
                                            @NotNull List<String> dirNames,
                                            @NotNull Processor<ResourceElement> afterAddedProcessor) {
    try {
      return addValueResource(project, resDir, resourceName, resourceType, fileName, dirNames, resourceValue, afterAddedProcessor);
    }
    catch (Exception e) {
      final String message = CreateElementActionBase.filterMessage(e.getMessage());

      if (message == null || message.isEmpty()) {
        LOG.error(e);
      }
      else {
        LOG.info(e);
        AndroidUtils.reportError(project, message);
      }
      return false;
    }
  }

  public static boolean createValueResource(@NotNull Project project,
                                            @NotNull VirtualFile resDir,
                                            @NotNull String resourceName,
                                            @NotNull final ResourceType resourceType,
                                            @NotNull String fileName,
                                            @NotNull List<String> dirNames,
                                            @NotNull final String value) {
    return createValueResource(project, resDir, resourceName, resourceType, fileName, dirNames, value, null);
  }

  public static boolean createValueResource(@NotNull Project project,
                                            @NotNull VirtualFile resDir,
                                            @NotNull String resourceName,
                                            @NotNull final ResourceType resourceType,
                                            @NotNull String fileName,
                                            @NotNull List<String> dirNames,
                                            @NotNull final String value,
                                            @Nullable final List<ResourceElement> outTags) {
    return createValueResource(project, resDir, resourceName, value, resourceType, fileName, dirNames, element -> {
      if (!value.isEmpty()) {
        final String s = resourceType == STRING ? normalizeXmlResourceValue(value) : value;
        element.setStringValue(s);
      }
      else if (resourceType == STYLEABLE || resourceType == STYLE) {
        element.setStringValue("value");
        element.getXmlTag().getValue().setText("");
      }

      if (outTags != null) {
        outTags.add(element);
      }
      return true;
    });
  }

  private static boolean addValueResource(@NotNull Project project,
                                          @NotNull VirtualFile resDir,
                                          @NotNull final String resourceName,
                                          @NotNull final ResourceType resourceType,
                                          @NotNull String fileName,
                                          @NotNull List<String> dirNames,
                                          @Nullable final String resourceValue,
                                          @NotNull final Processor<ResourceElement> afterAddedProcessor) throws Exception {
    if (dirNames.isEmpty()) {
      return false;
    }
    final VirtualFile[] resFiles = new VirtualFile[dirNames.size()];

    for (int i = 0, n = dirNames.size(); i < n; i++) {
      String dirName = dirNames.get(i);
      resFiles[i] = WriteAction.compute(() -> findOrCreateResourceFile(project, resDir, fileName, dirName));
      if (resFiles[i] == null) {
        return false;
      }
    }

    if (!ReadonlyStatusHandler.ensureFilesWritable(project, resFiles)) {
      return false;
    }
    final Resources[] resourcesElements = new Resources[resFiles.length];

    for (int i = 0; i < resFiles.length; i++) {
      final Resources resources = AndroidUtils.loadDomElement(project, resFiles[i], Resources.class);
      if (resources == null) {
        AndroidUtils.reportError(project, AndroidBundle.message("not.resource.file.error", fileName));
        return false;
      }
      resourcesElements[i] = resources;
    }

    List<PsiFile> psiFiles = Lists.newArrayListWithExpectedSize(resFiles.length);
    PsiManager manager = PsiManager.getInstance(project);
    for (VirtualFile file : resFiles) {
      PsiFile psiFile = manager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }

    writeCommandAction(project, psiFiles.toArray(PsiFile.EMPTY_ARRAY)).withName("Add Resource").run(() -> {
      for (Resources resources : resourcesElements) {
        if (resourceType.equals(ATTR)) {
          resources.addAttr().getName().setValue(ResourceReference.attr(ResourceNamespace.TODO(), resourceName));
        } else {
          final ResourceElement element = addValueResource(resourceType, resources, resourceValue);
          element.getName().setValue(resourceName);
          afterAddedProcessor.process(element);
        }
      }
    });

    return true;
  }

  /**
   * Sets a new value for a resource.
   * @param project the project containing the resource
   * @param resDir the res/ directory containing the resource
   * @param name the name of the resource to be modified
   * @param newValue the new resource value
   * @param fileName the resource values file name
   * @param dirNames list of values directories where the resource should be changed
   * @param useGlobalCommand if true, the undo will be registered globally. This allows the command to be undone from anywhere in the IDE
   *                         and not only the XML editor
   * @return true if the resource value was changed
   */
  public static boolean changeValueResource(@NotNull final Project project,
                                            @NotNull VirtualFile resDir,
                                            @NotNull final String name,
                                            @NotNull final ResourceType resourceType,
                                            @NotNull final String newValue,
                                            @NotNull String fileName,
                                            @NotNull List<String> dirNames,
                                            final boolean useGlobalCommand) {
    if (dirNames.isEmpty()) {
      return false;
    }
    ArrayList<VirtualFile> resFiles = Lists.newArrayListWithExpectedSize(dirNames.size());

    for (String dirName : dirNames) {
      final VirtualFile resFile = findResourceFile(resDir, fileName, dirName);
      if (resFile != null) {
        resFiles.add(resFile);
      }
    }

    if (!ensureFilesWritable(project, resFiles)) {
      return false;
    }
    final Resources[] resourcesElements = new Resources[resFiles.size()];

    for (int i = 0; i < resFiles.size(); i++) {
      final Resources resources = AndroidUtils.loadDomElement(project, resFiles.get(i), Resources.class);
      if (resources == null) {
        AndroidUtils.reportError(project, AndroidBundle.message("not.resource.file.error", fileName));
        return false;
      }
      resourcesElements[i] = resources;
    }

    List<PsiFile> psiFiles = Lists.newArrayListWithExpectedSize(resFiles.size());
    PsiManager manager = PsiManager.getInstance(project);
    for (VirtualFile file : resFiles) {
      PsiFile psiFile = manager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }
    PsiFile[] files = psiFiles.toArray(PsiFile.EMPTY_ARRAY);
    WriteCommandAction<Boolean> action = new WriteCommandAction<Boolean>(project, "Change " + resourceType.getName() + " Resource", files) {
      @Override
      protected void run(@NotNull Result<Boolean> result) {
        if (useGlobalCommand) {
          CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
        }

        result.setResult(false);
        for (Resources resources : resourcesElements) {
          for (ResourceElement element : getValueResourcesFromElement(resourceType, resources)) {
            String value = element.getName().getStringValue();
            if (name.equals(value)) {
              element.setStringValue(newValue);
              result.setResult(true);
            }
          }
        }
      }
    };

    return action.execute().getResultObject();
  }

  @Nullable
  private static VirtualFile findResourceFile(@NotNull VirtualFile resDir,
                                              @NotNull final String fileName,
                                              @NotNull String dirName) {
    VirtualFile dir = resDir.findChild(dirName);
    if (dir == null) {
      return null;
    }
    return dir.findChild(fileName);
  }

  @Nullable
  private static VirtualFile findOrCreateResourceFile(@NotNull Project project,
                                                      @NotNull VirtualFile resDir,
                                                      @NotNull final String fileName,
                                                      @NotNull String dirName) throws Exception {
    final VirtualFile dir = AndroidUtils.createChildDirectoryIfNotExist(project, resDir, dirName);
    final String dirPath = FileUtil.toSystemDependentName(resDir.getPath() + '/' + dirName);

    if (dir == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("android.cannot.create.dir.error", dirPath));
      return null;
    }

    final VirtualFile file = dir.findChild(fileName);
    if (file != null) {
      return file;
    }

    AndroidFileTemplateProvider
      .createFromTemplate(project, dir, AndroidFileTemplateProvider.VALUE_RESOURCE_FILE_TEMPLATE, fileName);
    final VirtualFile result = dir.findChild(fileName);
    if (result == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("android.cannot.create.file.error", dirPath + File.separatorChar + fileName));
    }
    return result;
  }

  @Nullable
  public static MyReferredResourceFieldInfo getReferredResourceOrManifestField(@NotNull AndroidFacet facet,
                                                                               @NotNull KtSimpleNameExpression exp,
                                                                               @Nullable String className,
                                                                               boolean localOnly) {
    String resFieldName = exp.getReferencedName();
    if (resFieldName.isEmpty()) {
      return null;
    }
    KtExpression resClassReference = AndroidKtPsiUtilsKt.getPreviousInQualifiedChain(exp);
    if (!(resClassReference instanceof KtSimpleNameExpression)) {
      return null;
    }
    String resClassName = ((KtSimpleNameExpression)resClassReference).getReferencedName();
    if (resClassName.isEmpty() || className != null && !className.equals(resClassName)) {
      return null;
    }

    KtExpression rClassReference = AndroidKtPsiUtilsKt.getPreviousInQualifiedChain(resClassReference);
    if (!(rClassReference instanceof KtSimpleNameExpression)) {
      return null;
    }
    PsiElement resolvedElement = ReferenceUtilKt.getMainReference((KtSimpleNameExpression)rClassReference).resolve();
    if (!(resolvedElement instanceof PsiClass)) {
      return null;
    }

    PsiClass aClass = (PsiClass)resolvedElement;
    String classShortName = aClass.getName();
    boolean fromManifest = AndroidUtils.MANIFEST_CLASS_NAME.equals(classShortName);

    if (!fromManifest && !isRJavaClass(aClass)) {
      return null;
    }
    String qName = aClass.getQualifiedName();
    if (qName == null) {
      return null;
    }

    Module resolvedModule = ModuleUtilCore.findModuleForPsiElement(resolvedElement);
    if (!localOnly) {
      if (CLASS_R.equals(qName) || AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME.equals(qName)) {
        return new MyReferredResourceFieldInfo(resClassName, resFieldName, resolvedModule, ResourceNamespace.ANDROID, false);
      }
    }

    if (fromManifest ? !isManifestClass(aClass) : !isRJavaClass(aClass)) {
      return null;
    }

    return new MyReferredResourceFieldInfo(resClassName, resFieldName, resolvedModule, getRClassNamespace(facet, qName), fromManifest);
  }

  @Nullable
  public static MyReferredResourceFieldInfo getReferredResourceOrManifestField(@NotNull AndroidFacet facet,
                                                                               @NotNull PsiReferenceExpression exp,
                                                                               boolean localOnly) {
    return getReferredResourceOrManifestField(facet, exp, null, localOnly);
  }

  @Nullable
  public static MyReferredResourceFieldInfo getReferredResourceOrManifestField(@NotNull AndroidFacet facet,
                                                                               @NotNull PsiReferenceExpression exp,
                                                                               @Nullable String className,
                                                                               boolean localOnly) {
    String resFieldName = exp.getReferenceName();
    if (resFieldName == null || resFieldName.isEmpty()) {
      return null;
    }

    PsiExpression qExp = exp.getQualifierExpression();
    if (!(qExp instanceof PsiReferenceExpression)) {
      return null;
    }
    PsiReferenceExpression resClassReference = (PsiReferenceExpression)qExp;

    String resClassName = resClassReference.getReferenceName();
    if (resClassName == null || resClassName.isEmpty() ||
        className != null && !className.equals(resClassName)) {
      return null;
    }

    qExp = resClassReference.getQualifierExpression();
    if (!(qExp instanceof PsiReferenceExpression)) {
      return null;
    }

    PsiElement resolvedElement = ((PsiReferenceExpression)qExp).resolve();
    if (!(resolvedElement instanceof PsiClass)) {
      return null;
    }
    Module resolvedModule = ModuleUtilCore.findModuleForPsiElement(resolvedElement);
    PsiClass aClass = (PsiClass)resolvedElement;
    String classShortName = aClass.getName();
    boolean fromManifest = AndroidUtils.MANIFEST_CLASS_NAME.equals(classShortName);

    if (!fromManifest && !AndroidUtils.R_CLASS_NAME.equals(classShortName)) {
      return null;
    }
    String qName = aClass.getQualifiedName();
    if (qName == null) {
      return null;
    }

    if (!localOnly) {
      if (CLASS_R.equals(qName) || AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME.equals(qName)) {
        return new MyReferredResourceFieldInfo(resClassName, resFieldName, resolvedModule, ResourceNamespace.ANDROID, false);
      }
    }

    if (fromManifest ? !isManifestClass(aClass) : !isRJavaClass(aClass)) {
      return null;
    }

    return new MyReferredResourceFieldInfo(resClassName, resFieldName, resolvedModule, getRClassNamespace(facet, qName), fromManifest);
  }

  @NotNull
  public static ResourceNamespace getRClassNamespace(@NotNull AndroidFacet facet, String qName) {
    ResourceNamespace resourceNamespace;
    if (ResourceRepositoryManager.getInstance(facet).getNamespacing() == Namespacing.DISABLED) {
      resourceNamespace = ResourceNamespace.RES_AUTO;
    } else {
      resourceNamespace = ResourceNamespace.fromPackageName(StringUtil.getPackageName(qName));
    }
    return resourceNamespace;
  }

  /**
   * Utility method suitable for Comparator implementations which order resource files,
   * which will sort files by base folder followed by alphabetical configurations. Prioritizes
   * XML files higher than non-XML files.
   */
  public static int compareResourceFiles(@Nullable VirtualFile file1, @Nullable VirtualFile file2) {
    //noinspection UseVirtualFileEquals
    if (file1 != null && file1.equals(file2) || file1 == file2) {
      return 0;
    }
    else if (file1 != null && file2 != null) {
      boolean xml1 = file1.getFileType() == StdFileTypes.XML;
      boolean xml2 = file2.getFileType() == StdFileTypes.XML;
      if (xml1 != xml2) {
        return xml1 ? -1 : 1;
      }
      VirtualFile parent1 = file1.getParent();
      VirtualFile parent2 = file2.getParent();
      if (parent1 != null && parent2 != null && !parent1.equals(parent2)) {
        String parentName1 = parent1.getName();
        String parentName2 = parent2.getName();
        boolean qualifier1 = parentName1.indexOf('-') != -1;
        boolean qualifier2 = parentName2.indexOf('-') != -1;
        if (qualifier1 != qualifier2) {
          return qualifier1 ? 1 : -1;
        }

        if (qualifier1) {
          // Sort in FolderConfiguration order
          FolderConfiguration config1 = FolderConfiguration.getConfigForFolder(parentName1);
          FolderConfiguration config2 = FolderConfiguration.getConfigForFolder(parentName2);
          if (config1 != null && config2 != null) {
            return config1.compareTo(config2);
          } else if (config1 != null) {
            return -1;
          } else if (config2 != null) {
            return 1;
          }

          int delta = parentName1.compareTo(parentName2);
          if (delta != 0) {
            return delta;
          }
        }
      }

      return file1.getPath().compareTo(file2.getPath());
    }
    else if (file1 != null) {
      return -1;
    }
    else {
      return 1;
    }
  }

  /**
   * Utility method suitable for Comparator implementations which order resource files,
   * which will sort files by base folder followed by alphabetical configurations. Prioritizes
   * XML files higher than non-XML files. (Resource file folders are sorted by folder configuration
   * order.)
   */
  public static int compareResourceFiles(@Nullable PsiFile file1, @Nullable PsiFile file2) {
    if (file1 == file2) {
      return 0;
    }
    else if (file1 != null && file2 != null) {
      boolean xml1 = file1.getFileType() == StdFileTypes.XML;
      boolean xml2 = file2.getFileType() == StdFileTypes.XML;
      if (xml1 != xml2) {
        return xml1 ? -1 : 1;
      }
      PsiDirectory parent1 = file1.getParent();
      PsiDirectory parent2 = file2.getParent();
      if (parent1 != null && parent2 != null && parent1 != parent2) {
        String parentName1 = parent1.getName();
        String parentName2 = parent2.getName();
        boolean qualifier1 = parentName1.indexOf('-') != -1;
        boolean qualifier2 = parentName2.indexOf('-') != -1;

        if (qualifier1 != qualifier2) {
          return qualifier1 ? 1 : -1;
        }

        if (qualifier1) {
          // Sort in FolderConfiguration order
          FolderConfiguration config1 = FolderConfiguration.getConfigForFolder(parentName1);
          FolderConfiguration config2 = FolderConfiguration.getConfigForFolder(parentName2);
          if (config1 != null && config2 != null) {
            return config1.compareTo(config2);
          } else if (config1 != null) {
            return -1;
          } else if (config2 != null) {
            return 1;
          }

          int delta = parentName1.compareTo(parentName2);
          if (delta != 0) {
            return delta;
          }
        }
      }

      return file1.getName().compareTo(file2.getName());
    }
    else if (file1 != null) {
      return -1;
    }
    else {
      return 1;
    }
  }

  public static boolean ensureFilesWritable(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
    return !ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files).hasReadonlyFiles();
  }

  /**
   * Grabs resource directories from the given facets and pairs the directory with an arbitrary
   * AndroidFacet which happens to depend on the directory.
   *
   * @param facets set of facets which may have resource directories
   */
  @NotNull
  public static Map<VirtualFile, AndroidFacet> getResourceDirectoriesForFacets(@NotNull List<AndroidFacet> facets) {
    Map<VirtualFile, AndroidFacet> resDirectories = new HashMap<>();
    for (AndroidFacet facet : facets) {
      for (VirtualFile resourceDir : ResourceFolderManager.getInstance(facet).getFolders()) {
        if (!resDirectories.containsKey(resourceDir)) {
          resDirectories.put(resourceDir, facet);
        }
      }
    }
    return resDirectories;
  }

  /** Returns the {@link PsiFile} corresponding to the source of the given resource item, if possible. */
  @Nullable
  public static PsiFile getItemPsiFile(@NotNull Project project, @NotNull ResourceItem item) {
    if (project.isDisposed()) {
      return null;
    }

    if (item instanceof PsiResourceItem) {
      PsiResourceItem psiResourceItem = (PsiResourceItem)item;
      return psiResourceItem.getPsiFile();
    }

    VirtualFile virtualFile = ResourceHelper.getSourceAsVirtualFile(item);
    if (virtualFile != null) {
      PsiManager psiManager = PsiManager.getInstance(project);
      return psiManager.findFile(virtualFile);
    }

    return null;
  }

  /**
   * Returns the XML attribute containing declaration of the given ID resource.
   *
   * @param project the project containing the resource
   * @param idResource the ID resource
   * @return
   */
  @Nullable
  public static XmlAttribute getIdDeclarationAttribute(@NotNull Project project, @NotNull ResourceItem idResource) {
    assert idResource.getType() == ID;
    PsiFile psiFile = getItemPsiFile(project, idResource);
    if (!(psiFile instanceof XmlFile)) {
      return null;
    }

    XmlFile xmlFile = (XmlFile)psiFile;
    String resourceName = idResource.getName();

    // TODO(b/113646219): find the right one, if there are multiple, not the first one.
    PsiElementProcessor.FindFilteredElement processor = new PsiElementProcessor.FindFilteredElement(element -> {
      if (element instanceof XmlAttribute) {
        XmlAttribute attr = (XmlAttribute)element;
        String attrValue = attr.getValue();
        if (isIdDeclaration(attrValue)) {
          ResourceUrl resourceUrl = ResourceUrl.parse(attrValue);
          if (resourceUrl != null && resourceUrl.name.equals(resourceName)) {
            return true;
          }
        }
      }
      return false;
    });
    PsiTreeUtil.processElements(xmlFile, processor);

    return (XmlAttribute)processor.getFoundElement();
  }

  /**
   * Returns the {@link XmlAttributeValue} defining the given resource item. This is only defined for resource items which are not file
   * based.
   *
   * <p>{@link org.jetbrains.android.AndroidFindUsagesHandlerFactory#createFindUsagesHandler} assumes references to value resources
   * resolve to the "name" {@link XmlAttributeValue}, that's how they are found when looking for usages of a resource.
   *
   * TODO(b/113646219): store enough information in {@link ResourceItem} to find the attribute and get the tag from there, not the other
   * way around.
   *
   * @see ResourceItem#isFileBased()
   * @see org.jetbrains.android.AndroidFindUsagesHandlerFactory#createFindUsagesHandler
   */
  @Nullable
  public static XmlAttributeValue getDeclaringAttributeValue(@NotNull Project project, @NotNull ResourceItem item) {
    if (item.isFileBased()) {
      return null;
    }

    XmlAttribute attribute;
    if (ResourceHelper.isInlineIdDeclaration(item)) {
      attribute = getIdDeclarationAttribute(project, item);
    } else {
      XmlTag tag = getItemTag(project, item);
      attribute = tag == null ? null : tag.getAttribute(ATTR_NAME);
    }

    return attribute == null ? null : attribute.getValueElement();
  }

  /**
   * Returns the {@link XmlTag} corresponding to the given resource item. This is only defined for resource items in value files.
   *
   * @see #getDeclaringAttributeValue(Project, ResourceItem)
   */
  @Nullable
  public static XmlTag getItemTag(@NotNull Project project, @NotNull ResourceItem item) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (item.isFileBased()) {
      return null;
    }

    if (item instanceof PsiResourceItem) {
      PsiResourceItem psiResourceItem = (PsiResourceItem)item;
      return psiResourceItem.getTag();
    }

    PsiFile psiFile = getItemPsiFile(project, item);
    if (!(psiFile instanceof XmlFile)) {
      return null;
    }

    XmlFile xmlFile = (XmlFile)psiFile;
    XmlTag rootTag = xmlFile.getRootTag();
    if (rootTag == null || !rootTag.isValid() || !rootTag.getName().equals(SdkConstants.TAG_RESOURCES)) {
      return null;
    }

    for (XmlTag tag : rootTag.getSubTags()) {
      ProgressManager.checkCanceled();
      if (!tag.isValid()) {
        continue;
      }

      ResourceType tagResourceType = getResourceTypeForResourceTag(tag);
      if (item.getType() == tagResourceType && item.getName().equals(tag.getAttributeValue(ATTR_NAME))) {
        return tag;
      }

      // Consider children of declare-styleable.
      if (item.getType() == ATTR && tagResourceType == STYLEABLE) {
        XmlTag[] attrs = tag.getSubTags();
        for (XmlTag attr : attrs) {
          if (!attr.isValid()) {
            continue;
          }

          if (item.getName().equals(attr.getAttributeValue(ATTR_NAME))
              && (attr.getAttribute(ATTR_FORMAT) != null || attr.getSubTags().length > 0)) {
            return attr;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  public static String getViewTag(@NotNull ResourceItem item) {
    if (item instanceof PsiResourceItem) {
      PsiResourceItem psiItem = (PsiResourceItem)item;
      XmlTag tag = psiItem.getTag();

      final String id = item.getName();

      if (tag != null && tag.isValid()
          // Make sure that the id attribute we're searching for is actually
          // defined for this tag, not just referenced from this tag.
          // For example, we could have
          //    <Button a:alignLeft="@+id/target" a:id="@+id/something ...>
          // and this should *not* return "Button" as the view tag for
          // @+id/target!
          && id.equals(stripIdPrefix(tag.getAttributeValue(ATTR_ID, ANDROID_URI)))) {
        return tag.getName();
      }


      PsiFile file = psiItem.getPsiFile();
      if (file instanceof XmlFile && file.isValid()) {
        XmlFile xmlFile = (XmlFile)file;
        XmlTag rootTag = xmlFile.getRootTag();
        if (rootTag != null && rootTag.isValid()) {
          return findViewTag(rootTag, id);
        }
      }
    }

    return null;
  }

  @Nullable
  private static String findViewTag(XmlTag tag, String target) {
    String id = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
    if (id != null && id.endsWith(target) && target.equals(stripIdPrefix(id))) {
      return tag.getName();
    }

    for (XmlTag sub : tag.getSubTags()) {
      if (sub.isValid()) {
        String found = findViewTag(sub, target);
        if (found != null) {
          return found;
        }
      }
    }

    return null;
  }

  /**
   * Data gathered from a reference to field of an aapt-generated class: R or Manifest.
   */
  public static class MyReferredResourceFieldInfo {
    @NotNull private final String myClassName;
    @NotNull private final String myFieldName;
    @Nullable private final Module myResolvedModule;
    @NotNull private final ResourceNamespace myNamespace;
    private final boolean myFromManifest;

    public MyReferredResourceFieldInfo(@NotNull String className, @NotNull String fieldName, @Nullable Module resolvedModule,
                                       @NotNull ResourceNamespace namespace, boolean fromManifest) {
      myClassName = className;
      myFieldName = fieldName;
      myNamespace = namespace;
      myResolvedModule = resolvedModule;
      myFromManifest = fromManifest;
    }

    @NotNull
    public String getClassName() {
      return myClassName;
    }

    @NotNull
    public String getFieldName() {
      return myFieldName;
    }

    @NotNull
    public ResourceNamespace getNamespace() {
      return myNamespace;
    }

    @Nullable
    public Module getResolvedModule() {
      return myResolvedModule;
    }

    public boolean isFromManifest() {
      return myFromManifest;
    }
  }

  @NotNull
  public static XmlFile createFileResource(@NotNull String fileName,
                                           @NotNull PsiDirectory resSubdir,
                                           @NotNull String rootTagName,
                                           @NotNull String resourceType,
                                           boolean valuesResourceFile) throws Exception {
    FileTemplateManager manager = FileTemplateManager.getInstance(resSubdir.getProject());
    String templateName = getTemplateName(resourceType, valuesResourceFile, rootTagName);
    FileTemplate template = manager.getJ2eeTemplate(templateName);
    Properties properties = new Properties();
    if (!valuesResourceFile) {
      properties.setProperty(ROOT_TAG_PROPERTY, rootTagName);
    }

    if (LAYOUT.getName().equals(resourceType)) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(resSubdir);
      final AndroidPlatform platform = module != null ? AndroidPlatform.getInstance(module) : null;
      final int apiLevel = platform != null ? platform.getApiLevel() : -1;

      final String value = apiLevel == -1 || apiLevel >= 8
                           ? "match_parent" : "fill_parent";
      properties.setProperty(LAYOUT_WIDTH_PROPERTY, value);
      properties.setProperty(LAYOUT_HEIGHT_PROPERTY, value);
    }
    PsiElement createdElement = FileTemplateUtil.createFromTemplate(template, fileName, properties, resSubdir);
    assert createdElement instanceof XmlFile;
    return (XmlFile)createdElement;
  }

  private static String getTemplateName(@NotNull String resourceType, boolean valuesResourceFile, @NotNull String rootTagName) {
    if (valuesResourceFile) {
      return AndroidFileTemplateProvider.VALUE_RESOURCE_FILE_TEMPLATE;
    }
    if (LAYOUT.getName().equals(resourceType) && !TAG_LAYOUT.equals(rootTagName) && !VIEW_MERGE.equals(rootTagName)) {
      return AndroidUtils.TAG_LINEAR_LAYOUT.equals(rootTagName)
             ? AndroidFileTemplateProvider.LAYOUT_RESOURCE_VERTICAL_FILE_TEMPLATE
             : AndroidFileTemplateProvider.LAYOUT_RESOURCE_FILE_TEMPLATE;
    }
    if (NAVIGATION.getName().equals(resourceType)) {
      return AndroidFileTemplateProvider.NAVIGATION_RESOURCE_FILE_TEMPLATE;
    }
    return AndroidFileTemplateProvider.RESOURCE_FILE_TEMPLATE;
  }

  @NotNull
  public static String getFieldNameByResourceName(@NotNull String styleName) {
    for (int i = 0, n = styleName.length(); i < n; i++) {
      char c = styleName.charAt(i);
      if (c == '.' || c == '-' || c == ':') {
        return styleName.replace('.', '_').replace('-', '_').replace(':', '_');
      }
    }

    return styleName;
  }

  /**
   * Finds and returns the resource files named stateListName in the directories listed in dirNames.
   * If some of the directories do not contain a file with that name, creates such a resource file.
   * @param project the project
   * @param resDir the res/ dir containing the directories under investigation
   * @param folderType Type of the directories under investigation
   * @param resourceType Type of the resource file to create if necessary
   * @param stateListName Name of the resource files to be returned
   * @param dirNames List of directory names to look into
   * @return List of found and created files
   */
  @Nullable
  public static List<VirtualFile> findOrCreateStateListFiles(@NotNull final Project project,
                                                             @NotNull final VirtualFile resDir,
                                                             @NotNull final ResourceFolderType folderType,
                                                             @NotNull final ResourceType resourceType,
                                                             @NotNull final String stateListName,
                                                             @NotNull final List<String> dirNames) {
    final PsiManager manager = PsiManager.getInstance(project);
    final List<VirtualFile> files = Lists.newArrayListWithCapacity(dirNames.size());
    boolean foundFiles = new WriteCommandAction<Boolean>(project, "Find statelists files") {
      @Override
      protected void run(@NotNull Result<Boolean> result) {
        result.setResult(true);
        try {
          String fileName = stateListName;
          if (!stateListName.endsWith(DOT_XML)) {
            fileName += DOT_XML;
          }

          for (String dirName : dirNames) {
            String dirPath = FileUtil.toSystemDependentName(resDir.getPath() + '/' + dirName);
            final VirtualFile dir;

            dir = AndroidUtils.createChildDirectoryIfNotExist(project, resDir, dirName);
            if (dir == null) {
              throw new IOException("cannot make " + resDir + File.separatorChar + dirName);
            }

            VirtualFile file = dir.findChild(fileName);
            if (file != null) {
              files.add(file);
              continue;
            }

            PsiDirectory directory = manager.findDirectory(dir);
            if (directory == null) {
              throw new IOException("cannot find " + resDir + File.separatorChar + dirName);
            }

            createFileResource(fileName, directory, CreateTypedResourceFileAction.getDefaultRootTagByResourceType(folderType),
                               resourceType.getName(), false);

            file = dir.findChild(fileName);
            if (file == null) {
              throw new IOException("cannot find " + Joiner.on(File.separatorChar).join(resDir, dirPath, fileName));
            }
            files.add(file);
          }
        }
        catch (Exception e) {
          LOG.error(e.getMessage());
          result.setResult(false);
        }
      }
    }.execute().getResultObject();

    return foundFiles ? files : null;
  }

  public static void updateStateList(@NotNull Project project, final @NotNull StateList stateList,
                                     @NotNull List<VirtualFile> files) {
    if (!ensureFilesWritable(project, files)) {
      return;
    }

    List<PsiFile> psiFiles = Lists.newArrayListWithCapacity(files.size());
    PsiManager manager = PsiManager.getInstance(project);
    for (VirtualFile file : files) {
      PsiFile psiFile = manager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }

    final List<AndroidDomElement> selectors = Lists.newArrayListWithCapacity(files.size());

    Class<? extends AndroidDomElement> selectorClass;

    if (stateList.getFolderType() == ResourceFolderType.COLOR) {
      selectorClass = ColorSelector.class;
    }
    else {
      selectorClass = DrawableSelector.class;
    }
    for (VirtualFile file : files) {
      final AndroidDomElement selector = AndroidUtils.loadDomElement(project, file, selectorClass);
      if (selector == null) {
        AndroidUtils.reportError(project, file.getName() + " is not a statelist file");
        return;
      }
      selectors.add(selector);
    }

    new WriteCommandAction.Simple(project, "Change State List", psiFiles.toArray(PsiFile.EMPTY_ARRAY)) {
      @Override
      protected void run() {
        for (AndroidDomElement selector : selectors) {
          XmlTag tag = selector.getXmlTag();
          for (XmlTag subtag : tag.getSubTags()) {
            subtag.delete();
          }
          for (StateListState state : stateList.getStates()) {
            XmlTag child = tag.createChildTag(TAG_ITEM, tag.getNamespace(), null, false);
            child = tag.addSubTag(child, false);

            Map<String, Boolean> attributes = state.getAttributes();
            for (String attributeName : attributes.keySet()) {
              child.setAttribute(attributeName, ANDROID_URI, attributes.get(attributeName).toString());
            }

            if (!StringUtil.isEmpty(state.getAlpha())) {
              child.setAttribute("alpha", ANDROID_URI, state.getAlpha());
            }

            if (selector instanceof ColorSelector) {
              child.setAttribute(ATTR_COLOR, ANDROID_URI, state.getValue());
            }
            else if (selector instanceof DrawableSelector) {
              child.setAttribute(ATTR_DRAWABLE, ANDROID_URI, state.getValue());
            }
          }
        }

        // The following is necessary since layoutlib will look on disk for the color state list file.
        // So as soon as a color state list is modified, the change needs to be saved on disk
        // for the correct values to be used in the theme editor preview.
        // TODO: Remove this once layoutlib can get color state lists from PSI instead of disk
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    }.execute();
  }


  /**
   * Ensures that the given namespace is imported in the given XML document.
   */
  @NotNull
  public static String ensureNamespaceImported(@NotNull XmlFile file, @NotNull String namespaceUri, @Nullable String suggestedPrefix) {
    final XmlTag rootTag = file.getRootTag();

    assert rootTag != null;
    final XmlElementFactory elementFactory = XmlElementFactory.getInstance(file.getProject());

    if (StringUtil.isEmpty(namespaceUri)) {
      // The style attribute has an empty namespaceUri:
      return "";
    }

    String prefix = rootTag.getPrefixByNamespace(namespaceUri);
    if (prefix != null) {
      return prefix;
    }

    ApplicationManager.getApplication().assertWriteAccessAllowed();

    if (suggestedPrefix != null) {
      prefix = suggestedPrefix;
    }
    else if (TOOLS_URI.equals(namespaceUri)) {
      prefix = TOOLS_PREFIX;
    }
    else if (ANDROID_URI.equals(namespaceUri)) {
      prefix = ANDROID_NS_NAME;
    }
    else if (AAPT_URI.equals(namespaceUri)) {
      prefix = AAPT_PREFIX;
    }
    else {
      prefix = APP_PREFIX;
    }
    if (rootTag.getAttribute(XMLNS_PREFIX + prefix) != null) {
      String base = prefix;
      for (int i = 2; ; i++) {
        prefix = base + Integer.toString(i);
        if (rootTag.getAttribute(XMLNS_PREFIX + prefix) == null) {
          break;
        }
      }
    }
    String name = XMLNS_PREFIX + prefix;
    final XmlAttribute xmlnsAttr = elementFactory.createXmlAttribute(name, namespaceUri);
    final XmlAttribute[] attributes = rootTag.getAttributes();
    XmlAttribute next = attributes.length > 0 ? attributes[0] : null;
    for (XmlAttribute attribute : attributes) {
      String attributeName = attribute.getName();
      if (!attributeName.startsWith(XMLNS_PREFIX) || attributeName.compareTo(name) > 0) {
        next = attribute;
        break;
      }
    }
    if (next != null) {
      rootTag.addBefore(xmlnsAttr, next);
    }
    else {
      rootTag.add(xmlnsAttr);
    }

    return prefix;
  }
}
