/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.datamodels;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.ThemeResolver;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.ProjectResourceRepository;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Wrapper for style configurations that allows modifying attributes directly in the XML file.
 */
public class ThemeEditorStyle {
  private static final Logger LOG = Logger.getInstance(ThemeEditorStyle.class);

  private final @NotNull StyleResourceValue myStyleResourceValue;
  private final @NotNull Configuration myConfiguration;
  private final Project myProject;

  /**
   * Source module of the theme, set to null if the theme comes from external libraries or the framework.
   * For currently edited theme stored in {@link ThemeEditorContext#getCurrentContextModule()}.
   */
  private final @Nullable Module mySourceModule;

  public ThemeEditorStyle(final @NotNull Configuration configuration,
                          final @NotNull StyleResourceValue styleResourceValue,
                          final @Nullable Module sourceModule) {
    myStyleResourceValue = styleResourceValue;
    myConfiguration = configuration;
    myProject = configuration.getModule().getProject();
    mySourceModule = sourceModule;
  }

  /**
   * Returns the style name. If this is a framework style, it will include the "android:" prefix.
   * Can be null, if there is no corresponding StyleResourceValue
   */
  @NotNull
  public String getQualifiedName() {
    return ResolutionUtils.getQualifiedStyleName(myStyleResourceValue);
  }

  /**
   * Returns the style name without namespaces or prefixes.
   */
  @NotNull
  public String getName() {
    return myStyleResourceValue.getName();
  }

  /**
   * @return url representation of this style,
   * Result will start either with {@value SdkConstants#ANDROID_STYLE_RESOURCE_PREFIX} or {@value SdkConstants#STYLE_RESOURCE_PREFIX}
   */
  @NotNull
  public String getStyleResourceUrl() {
    return ResourceUrl.create(myStyleResourceValue).toString();
  }

  public boolean isProjectStyle() {
    if (myStyleResourceValue.isFramework()) {
      return false;
    }
    ProjectResourceRepository repository = ProjectResourceRepository.getProjectResources(myConfiguration.getModule(), true);
    assert repository != null : myConfiguration.getModule().getName();
    return repository.hasResourceItem(ResourceType.STYLE, myStyleResourceValue.getName());
  }

  /**
   * Returns StyleResourceValue for current Configuration
   */
  @NotNull
  public StyleResourceValue getStyleResourceValue() {
    return myStyleResourceValue;
  }

  /**
   * Returns all the {@link ResourceItem} where this style is defined. This includes all the definitions in the
   * different resource folders.
   */
  @NotNull
  private Collection<ResourceItem> getStyleResourceItems() {
    assert !isFramework();

    Collection<ResourceItem> resultItems;
    if (isProjectStyle()) {
      final Module module = getModuleForAcquiringResources();
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null : module.getName() + " module doesn't have AndroidFacet";

      // We need to keep a Set of ResourceItems to override them. The key is the folder configuration + the name
      final HashMap<String, ResourceItem> resourceItems = Maps.newHashMap();
      ThemeEditorUtils.acceptResourceResolverVisitor(facet, new ThemeEditorUtils.ResourceFolderVisitor() {
        @Override
        public void visitResourceFolder(@NotNull LocalResourceRepository resources, String moduleName, @NotNull String variantName, boolean isSourceSelected) {
          if (!isSourceSelected) {
            // Currently we ignore the source sets that are not active
            // TODO: Process all source sets
            return;
          }

          List<ResourceItem> items = resources.getResourceItem(ResourceType.STYLE, myStyleResourceValue.getName());
          if (items == null) {
            return;
          }

          for (ResourceItem item : items) {
            String key = item.getConfiguration().toShortDisplayString() + "/" + item.getName();
            resourceItems.put(key, item);
          }
        }
      });

      resultItems = ImmutableList.copyOf(resourceItems.values());
    } else {
      LocalResourceRepository resourceRepository = AppResourceRepository.getAppResources(getModuleForAcquiringResources(), true);
      assert resourceRepository != null;
      List<ResourceItem> items = resourceRepository.getResourceItem(ResourceType.STYLE, getName());
      if (items != null) {
        resultItems = items;
      } else {
        resultItems = Collections.emptyList();
      }
    }
    return resultItems;
  }

  /**
   * Get a Module instance that should be used for resolving all possible resources that could constitute values
   * of this theme's attributes. Returns source module of a theme if it's available and rendering context module
   * otherwise.
   */
  private Module getModuleForAcquiringResources() {
    return mySourceModule != null ? mySourceModule : myConfiguration.getModule();
  }

  /**
   * Returns whether this style is editable.
   */
  public boolean isReadOnly() {
    return !isProjectStyle();
  }

  /**
   * Returns all the style attributes and its values. For each attribute, multiple {@link ConfiguredElement} can be returned
   * representing the multiple values in different configurations for each item.
   */
  @NotNull
  public ImmutableCollection<ConfiguredElement<ItemResourceValue>> getConfiguredValues() {
    // Get a list of all the items indexed by the item name. Each item contains a list of the
    // possible values in this theme in different configurations.
    //
    // If item1 has multiple values in different configurations, there will be an
    // item1 = {folderConfiguration1 -> value1, folderConfiguration2 -> value2}
    final ImmutableList.Builder<ConfiguredElement<ItemResourceValue>> itemResourceValues = ImmutableList.builder();

    if (isFramework()) {
      assert myConfiguration.getFrameworkResources() != null;

      com.android.ide.common.resources.ResourceItem styleItem =
        myConfiguration.getFrameworkResources().getResourceItem(ResourceType.STYLE, myStyleResourceValue.getName());
      // Go over all the files containing the resource.
      for (ResourceFile file : styleItem.getSourceFileList()) {
        ResourceValue styleResourceValue = styleItem.getResourceValue(ResourceType.STYLE, file.getConfiguration(), true);
        FolderConfiguration folderConfiguration = file.getConfiguration();

        if (styleResourceValue instanceof StyleResourceValue) {
          for (final ItemResourceValue value : ((StyleResourceValue)styleResourceValue).getValues()) {
            itemResourceValues.add(ConfiguredElement.create(folderConfiguration, value));
          }
        }
      }
    }
    else {
      LocalResourceRepository repository = AppResourceRepository.getAppResources(myConfiguration.getModule(), true);
      assert repository != null;
      // Find every definition of this style and get all the attributes defined
      List<ResourceItem> styleDefinitions = repository.getResourceItem(ResourceType.STYLE, myStyleResourceValue.getName());
      assert styleDefinitions != null; // Style doesn't exist anymore?
      for (ResourceItem styleDefinition : styleDefinitions) {
        ResourceValue styleResourceValue = styleDefinition.getResourceValue(isFramework());
        FolderConfiguration folderConfiguration = styleDefinition.getConfiguration();

        if (styleResourceValue instanceof StyleResourceValue) {
          for (final ItemResourceValue value : ((StyleResourceValue)styleResourceValue).getValues()) {
            // We use the qualified name since apps and libraries can use the same attribute name twice with and without "android:"
            itemResourceValues.add(ConfiguredElement.create(folderConfiguration, value));
          }
        }
      }
    }

    return itemResourceValues.build();
  }

  public boolean hasItem(@Nullable EditedStyleItem item) {
    //TODO: add isOverriden() method to EditedStyleItem
    return item != null && getStyleResourceValue().getItem(item.getName(), item.isFrameworkAttr()) != null;
  }

  public ItemResourceValue getItem(@NotNull String name, boolean isFramework) {
    return getStyleResourceValue().getItem(name, isFramework);
  }

  /**
   * See {@link #getParent(ThemeResolver)}
   */
  public ThemeEditorStyle getParent() {
    return getParent(null);
  }

  /**
   * Returns the names of all the parents of this style. Parents might differ depending on the folder configuration, this returns all the
   * variants for this style.
   */
  public Collection<ConfiguredElement<String>> getParentNames() {
    if (isFramework()) {
      // Framework themes do not have multiple parents so we just get the only one.
      ThemeEditorStyle parent = getParent();
      if (parent != null) {
        return ImmutableList.of(ConfiguredElement.create(getConfiguration().getEditedConfig(), parent.getQualifiedName()));
      }
      // The theme has no parent (probably the main "Theme" style)
      return Collections.emptyList();
    }

    ImmutableList.Builder<ConfiguredElement<String>> parents = ImmutableList.builder();
    for (final ResourceItem styleItem : getStyleResourceItems()) {
      StyleResourceValue style = (StyleResourceValue)styleItem.getResourceValue(false);
      assert style != null;
      String parentName = ResolutionUtils.getParentQualifiedName(style);
      if (parentName != null) {
        parents.add(ConfiguredElement.create(styleItem.getConfiguration(), parentName));
      }
    }
    return parents.build();
  }

  /**
   * @param themeResolver theme resolver that would be used to look up parent theme by name
   *                      Pass null if you don't care about resulting ThemeEditorStyle source module (which would be null in that case)
   * @return the style parent
   */
  @Nullable("if this is a root style")
  public ThemeEditorStyle getParent(@Nullable ThemeResolver themeResolver) {
    ResourceResolver resolver = myConfiguration.getResourceResolver();
    assert resolver != null;

    StyleResourceValue parent = resolver.getParent(getStyleResourceValue());
    if (parent == null) {
      return null;
    }

    if (themeResolver == null) {
      return ResolutionUtils.getStyle(myConfiguration, ResolutionUtils.getQualifiedStyleName(parent), null);
    }
    else {
      return themeResolver.getTheme(ResolutionUtils.getQualifiedStyleName(parent));
    }
  }

  /**
   * @param attribute The style attribute name.
   * @return the XmlTag that contains the value for a given attribute in the current style.
   */
  @Nullable("if the attribute does not exist in this theme")
  private XmlTag getValueTag(@NotNull XmlTag sourceTag, @NotNull final String attribute) {
    if (!isProjectStyle()) {
      // Non project styles do not contain local values.
      return null;
    }

    final Ref<XmlTag> resultXmlTag = new Ref<XmlTag>();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    sourceTag.acceptChildren(new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);

        if (!(element instanceof XmlTag)) {
          return;
        }

        final XmlTag tag = (XmlTag)element;
        if (SdkConstants.TAG_ITEM.equals(tag.getName()) && attribute.equals(tag.getAttributeValue(SdkConstants.ATTR_NAME))) {
          resultXmlTag.set(tag);
        }
      }
    });

    return resultXmlTag.get();
  }

  /**
   * @param configuration FolderConfiguration of the style
   * @return XmlTag of this style coming from folder with corresponding FolderConfiguration
   */
  @Nullable("if there is no style from this configuration")
  private XmlTag findXmlTagFromConfiguration(@NotNull FolderConfiguration configuration) {
    for (ResourceItem item : getStyleResourceItems()) {
      if (item.getConfiguration().equals(configuration)) {
        return LocalResourceRepository.getItemTag(myProject, item);
      }
    }
    return null;
  }

  /**
   * Finds best to be copied {@link FolderConfiguration}s
   * e.g if style is defined in "port-v8", "port-v18", "port-v22", "night-v20" and desiredApi = 21,
   * then result is {"port-v18", "night-v20"}
   * @param desiredApi new api level of {@link FolderConfiguration}s after being copied
   * @return Collection of FolderConfigurations which are going to be copied to version desiredApi
   */
  @NotNull
  private ImmutableCollection<FolderConfiguration> findToBeCopied(int desiredApi) {
    // Keeps closest VersionQualifier to 'desiredApi'
    // e.g. desiredApi = 21, "en-port", "en-port-v18", "en-port-v19", "en-port-v22" then
    // bestVersionCopyFrom will contain {"en-port" -> v19}, as it is closest one to v21
    final HashMap<FolderConfiguration, VersionQualifier> bestVersionCopyFrom = Maps.newHashMap();

    for (ResourceItem styleItem : getStyleResourceItems()) {
      FolderConfiguration configuration = FolderConfiguration.copyOf(styleItem.getConfiguration());
      int styleItemVersion = ThemeEditorUtils.getVersionFromConfiguration(configuration);

      // We want to get the best from port-v19 port-v20 port-v23. so we need to remove the version qualifier to compare them
      configuration.setVersionQualifier(null);

      if (styleItemVersion > desiredApi) {
        // VersionQualifier of the 'styleItem' is higher than 'desiredApi'.
        // Thus, we don't need to copy it, we are going to just modify it.
        continue;
      }
      // If 'version' is closer to 'desiredApi' than we have found
      if (!bestVersionCopyFrom.containsKey(configuration) || bestVersionCopyFrom.get(configuration).getVersion() < styleItemVersion) {
        bestVersionCopyFrom.put(configuration, new VersionQualifier(styleItemVersion));
      }
    }

    ImmutableList.Builder<FolderConfiguration> toBeCopied = ImmutableList.builder();

    for (FolderConfiguration key : bestVersionCopyFrom.keySet()) {
      FolderConfiguration configuration = FolderConfiguration.copyOf(key);
      VersionQualifier version = bestVersionCopyFrom.get(key);

      if (version.getVersion() != -1) {
        configuration.setVersionQualifier(version);
      }

      // If configuration = 'en-port-v19' and desiredApi = 'v21', then we should copy 'en-port-v19' to 'en-port-v21'
      // But If configuration = 'en-port-v21' and desiredApi = 'v21, then we don't need to copy
      // Version can't be bigger as we have filtered above
      if (version.getVersion() < desiredApi) {
        toBeCopied.add(configuration);
      }
    }

    return toBeCopied.build();
  }

  /**
   * Sets the value of given attribute in a specific folder.
   *
   * @param configuration FolderConfiguration of style that will be modified
   * @param attribute     the style attribute name
   * @param value         the style attribute value
   */
  private void setValue(@NotNull FolderConfiguration configuration, @NotNull final String attribute, @NotNull final String value) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    XmlTag styleTag = findXmlTagFromConfiguration(configuration);
    assert styleTag != null;

    XmlTag tag = getValueTag(styleTag, attribute);
    if (tag != null) {
      tag.getValue().setEscapedText(value);
    }
    else {
      // The value didn't exist, add it.
      XmlTag child = styleTag.createChildTag(SdkConstants.TAG_ITEM, styleTag.getNamespace(), value, false);
      child.setAttribute(SdkConstants.ATTR_NAME, attribute);
      styleTag.addSubTag(child, false);
    }
  }

  /**
   * Sets the value of given attribute in all possible folders where this style is defined. If attribute or value can be used from certain API level,
   * folders below that level won't be modified, instead new folder with certain API will be created.
   * Note: {@link LocalResourceRepository}'s won't get updated immediately
   *
   * @param attribute the style attribute name
   * @param value     the style attribute value
   */
  public void setValue(@NotNull final String attribute, @NotNull final String value) {
    if (!isProjectStyle()) {
      throw new UnsupportedOperationException("Non project styles can not be modified");
    }

    int maxApi =
      Math.max(ResolutionUtils.getOriginalApiLevel(value, myProject), ResolutionUtils.getOriginalApiLevel(attribute, myProject));
    int minSdk = ThemeEditorUtils.getMinApiLevel(myConfiguration.getModule());

    // When api level of both attribute and value is not greater that Minimum SDK,
    // we should modify every FolderConfiguration, thus we set desiredApi to -1
    final int desiredApi = (maxApi <= minSdk) ? -1 : maxApi;

    new WriteCommandAction.Simple(myProject, "Setting value of " + attribute) {
      @Override
      protected void run() {
        // Makes the command global even if only one xml file is modified
        // That way, the Undo is always available from the theme editor
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);

        Collection<FolderConfiguration> toBeCopied = findToBeCopied(desiredApi);
        for (FolderConfiguration configuration : toBeCopied) {
          XmlTag styleTag = findXmlTagFromConfiguration(configuration);
          assert styleTag != null;
          ThemeEditorUtils.copyTheme(desiredApi, styleTag);
        }

        if (!toBeCopied.isEmpty()) {
          // We need to refreshResource, to get all copied styles
          // Otherwise, LocalResourceRepositories won't get updated, so we won't get copied styles
          AndroidFacet facet = AndroidFacet.getInstance(myConfiguration.getModule());
          if (facet != null) {
            facet.refreshResources();
          }
        }

        Collection<ResourceItem> styleItems = getStyleResourceItems();
        for (ResourceItem style : styleItems) {
          FolderConfiguration configuration = style.getConfiguration();
          int version = ThemeEditorUtils.getVersionFromConfiguration(configuration);
          // If version qualifier is higher than 'desiredApi' then
          // it means than we can modify 'attribute' to value 'value'.
          if (version >= desiredApi) {
            setValue(configuration, attribute, value);
          }
        }
      }
    }.execute();
  }

  /**
   * Sets the parent of the style in a specific folder.
   * @param configuration  FolderConfiguration of style that will be modified
   * @param newParent new name of the parent
   */
  private void setParent(@NotNull final FolderConfiguration configuration, @NotNull final String newParent) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    XmlTag styleTag = findXmlTagFromConfiguration(configuration);
    assert styleTag != null;
    styleTag.setAttribute(SdkConstants.ATTR_PARENT, newParent);
  }

  /**
   * Sets the parent of the style in all possible folders where this style is defined.
   * If new parent can be used from certain API level, folders below that level won't be modified,
   * instead new folder with API of the new parent will be created.
   * Note: {@link LocalResourceRepository}'s won't get updated immediately
   *
   * @param qualifiedThemeName new name of the parent
   */
  public void setParent(@NotNull final String qualifiedThemeName) {
    if (!isProjectStyle()) {
      throw new UnsupportedOperationException("Non project styles can not be modified");
    }

    assert !qualifiedThemeName.startsWith(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX);
    assert !qualifiedThemeName.startsWith(SdkConstants.STYLE_RESOURCE_PREFIX);

    String newParentResourceUrl = ResolutionUtils.getStyleResourceUrl(qualifiedThemeName);
    int parentApi = ResolutionUtils.getOriginalApiLevel(newParentResourceUrl, myProject);
    int minSdk = ThemeEditorUtils.getMinApiLevel(myConfiguration.getModule());

    // When api level of both attribute and value is not greater that Minimum SDK,
    // we should modify every FolderConfiguration, thus we set desiredApi to -1
    final int desiredApi = (parentApi <= minSdk) ? -1 : parentApi;

    new WriteCommandAction.Simple(myProject, "Updating Parent to " + qualifiedThemeName, null) {
      @Override
      protected void run() {
        // Makes the command global even if only one xml file is modified
        // That way, the Undo is always available from the theme editor
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);

        Collection<FolderConfiguration> toBeCopied = findToBeCopied(desiredApi);
        for (FolderConfiguration configuration : toBeCopied) {
          XmlTag styleTag = findXmlTagFromConfiguration(configuration);
          assert styleTag != null;
          ThemeEditorUtils.copyTheme(desiredApi, styleTag);
        }

        if (!toBeCopied.isEmpty()) {
          // We need to refreshResource, to get all copied styles
          // Otherwise, LocalResourceRepositories won't get updated, so we won't get copied styles
          AndroidFacet facet = AndroidFacet.getInstance(myConfiguration.getModule());
          if (facet != null) {
            facet.refreshResources();
          }
        }

        Collection<ResourceItem> styleItems = getStyleResourceItems();
        for (ResourceItem style : styleItems) {
          FolderConfiguration configuration = style.getConfiguration();
          int version = ThemeEditorUtils.getVersionFromConfiguration(configuration);
          // If version qualifier is higher than 'desiredApi' then
          // it means than we can modify 'attribute' to value 'value'.
          if (version >= desiredApi) {
            setParent(configuration, qualifiedThemeName);
          }
        }
      }
    }.execute();
  }

  @Override
  public String toString() {
    if (!isReadOnly()) {
      return "[" + getName() + "]";
    }

    return getName();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || (!(obj instanceof ThemeEditorStyle))) {
      return false;
    }

    return getQualifiedName().equals(((ThemeEditorStyle)obj).getQualifiedName());
  }

  @Override
  public int hashCode() {
    return getQualifiedName().hashCode();
  }

  @NotNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  /**
   * Deletes an attribute of that particular style from all the relevant xml files
   */
  public void removeAttribute(@NotNull final String attribute) {
    if (!isProjectStyle()) {
      throw new UnsupportedOperationException("Non project styles can not be modified");
    }

    Collection<PsiFile> toBeEdited = new HashSet<PsiFile>();
    final Collection<XmlTag> toBeRemoved = new HashSet<XmlTag>();
    for (ResourceItem resourceItem : getStyleResourceItems()) {
      final XmlTag sourceXml = LocalResourceRepository.getItemTag(myProject, resourceItem);
      assert sourceXml != null;
      final XmlTag tag = getValueTag(sourceXml, attribute);
      if (tag != null) {
        toBeEdited.add(tag.getContainingFile());
        toBeRemoved.add(tag);
      }
    }

    new WriteCommandAction.Simple(myProject, "Removing " + attribute, toBeEdited.toArray(new PsiFile[toBeEdited.size()])) {
      @Override
      protected void run() {
        // Makes the command global even if only one xml file is modified
        // That way, the Undo is always available from the theme editor
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);

        for (XmlTag tag : toBeRemoved) {
          tag.delete();
        }
      }
    }.execute();
  }

  /**
   * Returns a PsiElement of the name attribute for this theme
   * made from a RANDOM sourceXml
   */
  @Nullable
  public PsiElement getNamePsiElement() {
    Collection<ResourceItem> resources = getStyleResourceItems();
    if (resources.isEmpty()){
      return null;
    }
    // Any sourceXml will do to get the name attribute from
    final XmlTag sourceXml = LocalResourceRepository.getItemTag(myProject, resources.iterator().next());
    assert sourceXml != null;
    final XmlAttribute nameAttribute = sourceXml.getAttribute("name");
    if (nameAttribute == null) {
      return null;
    }

    XmlAttributeValue attributeValue = nameAttribute.getValueElement();
    if (attributeValue == null) {
      return null;
    }

    return new ValueResourceElementWrapper(attributeValue);
  }

  /**
   * Plain getter, see {@link #mySourceModule} for field description.
   */
  @Nullable
  public Module getSourceModule() {
    return mySourceModule;
  }

  /**
   * Returns whether this style is public.
   */
  public boolean isPublic() {
    if (!isFramework()) {
      return true;
    }

    IAndroidTarget target = myConfiguration.getTarget();
    if (target == null) {
      LOG.error("Unable to get IAndroidTarget.");
      return false;
    }

    AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, myConfiguration.getModule());
    if (androidTargetData == null) {
      LOG.error("Unable to get AndroidTargetData.");
      return false;
    }

    return androidTargetData.isResourcePublic(ResourceType.STYLE.getName(), getName());
  }

  public boolean isFramework() {
    return myStyleResourceValue.isFramework();
  }
}
