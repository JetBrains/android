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
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.StyleResolver;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.ProjectResourceRepository;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
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
import java.util.List;
import java.util.Map;

/**
 * Wrapper for style configurations that allows modifying attributes directly in the XML file.
 */
public class ThemeEditorStyle {
  private static final Logger LOG = Logger.getInstance(ThemeEditorStyle.class);

  private final boolean myIsFrameworkStyle;
  private final @NotNull StyleResolver myThemeResolver;
  private final @NotNull String myStyleName;
  private final @NotNull Configuration myConfiguration;
  private final Project myProject;

  public ThemeEditorStyle(final @NotNull StyleResolver resolver,
                          final @NotNull Configuration configuration,
                          final @NotNull String styleName,
                          boolean isFrameworkStyle) {
    myIsFrameworkStyle = isFrameworkStyle;
    myThemeResolver = resolver;
    myConfiguration = configuration;
    myStyleName = styleName;
    myProject = configuration.getModule().getProject();
  }

  public boolean isProjectStyle() {
    if (myIsFrameworkStyle) {
      return false;
    }
    ProjectResourceRepository repository = ProjectResourceRepository.getProjectResources(myConfiguration.getModule(), true);
    assert repository != null : myConfiguration.getModule().getName();
    return repository.hasResourceItem(ResourceType.STYLE, myStyleName);
  }

  /**
   * Returns StyleResourceValue using myConfiguration with parameters myStyleName and myIsFrameworkStyle
   * Can be null, if there is no such StyleResourceValue
   */
  @Nullable
  public StyleResourceValue getStyleResourceValue() {
    ResourceResolver resolver = myConfiguration.getResourceResolver();
    assert resolver != null;
    return resolver.getStyle(myStyleName, myIsFrameworkStyle);
  }

  /**
   * Returns all the {@link ResourceItem} where this style is defined. This includes all the definitions in the
   * different resource folders.
   */
  @NotNull
  private List<ResourceItem> getStyleResourceItems() {
    assert !myIsFrameworkStyle;

    final ImmutableList.Builder<ResourceItem> resourceItems = ImmutableList.builder();
    AndroidFacet facet = AndroidFacet.getInstance(myConfiguration.getModule());
    assert facet != null : myConfiguration.getModule().getName() + " module doesn't have AndroidFacet";
    ThemeEditorUtils.acceptResourceResolverVisitor(facet, new ThemeEditorUtils.ResourceFolderVisitor() {
      @Override
      public void visitResourceFolder(@NotNull LocalResourceRepository resources, boolean isSourceSelected) {
        if (!isSourceSelected) {
          // Currently we ignore the source sets that are not active
          // TODO: Process all source sets
          return;
        }

        List<ResourceItem> items = resources.getResourceItem(ResourceType.STYLE, myStyleName);
        if (items == null) {
          return;
        }
        resourceItems.addAll(items);
      }
    });
    return resourceItems.build();
  }

  /**
   * Returns whether this style is editable.
   */
  public boolean isReadOnly() {
    return !isProjectStyle();
  }

  /**
   * Returns the style name. If this is a framework style, it will include the "android:" prefix.
   * Can be null, if there is no corresponding StyleResourceValue
   */
  @Nullable
  public String getQualifiedName() {
    StyleResourceValue style = getStyleResourceValue();
    return style == null ? null : StyleResolver.getQualifiedStyleName(style);
  }

  /**
   * Returns the style name without namespaces or prefixes.
   */
  @NotNull
  public String getName() {
    return getStyleResourceValue().getName();
  }

  /**
   * Returns all the style attributes and it's values. The returned {@link EditedStyleItem}s contain all the definitions
   * for the attribute across the different resource folders.
   */
  @NotNull
  public Collection<EditedStyleItem> getValues() {
    // Get a list of all the items indexed by the item name. Each item contains a list of the
    // possible values in this theme in different configurations.
    //
    // If item1 has multiple values in different configurations, there will be an
    // item1 = {folderConfiguration1 -> value1, folderConfiguration2 -> value2}
    final Multimap<String, ConfiguredItemResourceValue> itemResourceValues = ArrayListMultimap.create();

    if (myIsFrameworkStyle) {
      assert myConfiguration.getFrameworkResources() != null;

      com.android.ide.common.resources.ResourceItem styleItem =
        myConfiguration.getFrameworkResources().getResourceItem(ResourceType.STYLE, myStyleName);
      // Go over all the files containing the resource.
      for (ResourceFile file : styleItem.getSourceFileList()) {
        ResourceValue styleResourceValue = styleItem.getResourceValue(ResourceType.STYLE, file.getConfiguration(), true);
        FolderConfiguration folderConfiguration = file.getConfiguration();

        if (styleResourceValue instanceof StyleResourceValue) {
          for (final ItemResourceValue value : ((StyleResourceValue)styleResourceValue).getValues()) {
            itemResourceValues
              .put(StyleResolver.getQualifiedItemName(value), new ConfiguredItemResourceValue(folderConfiguration, value));
          }
        }
      }
    }
    else {
      // TODO: Use something else instead of myConfiguration.getModule?
      LocalResourceRepository repository = AppResourceRepository.getAppResources(myConfiguration.getModule(), true);
      assert repository != null;
      // Find every definition of this style and get all the attributes defined
      List<ResourceItem> styleDefinitions = repository.getResourceItem(ResourceType.STYLE, myStyleName);
      assert styleDefinitions != null; // Style doesn't exist anymore?
      for (ResourceItem styleDefinition : styleDefinitions) {
        ResourceValue styleResourceValue = styleDefinition.getResourceValue(myIsFrameworkStyle);
        FolderConfiguration folderConfiguration = styleDefinition.getConfiguration();

        if (styleResourceValue instanceof StyleResourceValue) {
          for (final ItemResourceValue value : ((StyleResourceValue)styleResourceValue).getValues()) {
            // We use the qualified name since apps and libraries can use the same attribute name twice with and without "android:"
            itemResourceValues
              .put(StyleResolver.getQualifiedItemName(value), new ConfiguredItemResourceValue(folderConfiguration, value));
          }
        }
      }
    }

    final ImmutableList.Builder<EditedStyleItem> allValues = ImmutableList.builder();
    // Now, we process the map created above and we created the EditedStyleItems. We pass all the possible values and the FolderConfiguration
    // that should be showed as main (or selected) value.
    for (Map.Entry<String, Collection<ConfiguredItemResourceValue>> items : itemResourceValues.asMap().entrySet()) {
      Collection<ConfiguredItemResourceValue> configuredValues = items.getValue();
      final ConfiguredItemResourceValue bestMatch =
        (ConfiguredItemResourceValue)myConfiguration.getFullConfig()
          .findMatchingConfigurable(ImmutableList.copyOf(configuredValues));
      if (bestMatch == null) {
        assert configuredValues.size() == 1;
        // For framework values we might only have one value. We select that one
        allValues.add(new EditedStyleItem(configuredValues.iterator().next(), this));
      }
      else {
        allValues.add(new EditedStyleItem(bestMatch, Collections2
          .filter(configuredValues, new Predicate<ConfiguredItemResourceValue>() {
            @Override
            public boolean apply(@Nullable ConfiguredItemResourceValue input) {
              return input != bestMatch;
            }
          }), this));
      }
    }

    return allValues.build();
  }

  public boolean hasItem(@Nullable EditedStyleItem item) {
    //TODO: add isOverriden() method to EditedStyleItem
    return item != null && getStyleResourceValue().getItem(item.getName(), item.isFrameworkAttr()) != null;
  }

  /**
   * Returns the style parent or null if this is a root style.
   */
  @Nullable
  public ThemeEditorStyle getParent() {
    ResourceResolver resolver = myConfiguration.getResourceResolver();
    assert resolver != null;

    StyleResourceValue parent = resolver.getParent(getStyleResourceValue());
    if (parent == null) {
      return null;
    }

    return myThemeResolver.getStyle(StyleResolver.getQualifiedStyleName(parent));
  }

  /**
   * Returns the XmlTag that contains the value for a given attribute in the current style.
   * @param attribute The style attribute name.
   * @return The {@link XmlTag} or null if the attribute does not exist in this theme.
   */
  @Nullable
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

        final XmlTag tag = (XmlTag) element;
        if (SdkConstants.TAG_ITEM.equals(tag.getName()) && attribute.equals(tag.getAttributeValue(SdkConstants.ATTR_NAME))) {
          resultXmlTag.set(tag);
        }
      }
    });

    return resultXmlTag.get();
  }

  /**
   * Sets the attribute value.
   * @param attribute The style attribute name.
   * @param value The attribute value.
   */
  public void setValue(@NotNull final String attribute, @NotNull final String value) {
    if (!isProjectStyle()) {
      throw new UnsupportedOperationException("Non project styles can not be modified");
    }

    final int minAcceptableApi = Math.max(ThemeEditorUtils.getOriginalApiLevel(attribute, myProject),
                                          ThemeEditorUtils.getOriginalApiLevel(value, myProject));
    final ApiInformation apiInformation = new ApiInformation(minAcceptableApi);

    Collection<PsiFile> toBeEdited = new HashSet<PsiFile>();
    for (XmlTag sourceXml : apiInformation.sources) {
      toBeEdited.add(sourceXml.getContainingFile());
    }

    new WriteCommandAction.Simple(myProject, "Setting value of " + attribute, toBeEdited.toArray(new PsiFile[toBeEdited.size()])) {
      @Override
      protected void run() {
        // Makes the command global even if only one xml file is modified
        // That way, the Undo is always available from the theme editor
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);

        for (XmlTag sourceXml : apiInformation.sources) {
          // TODO: Check if the current value is defined by one of the parents and remove the attribute.
          XmlTag tag = getValueTag(sourceXml, attribute);
          if (tag != null) {
            tag.getValue().setEscapedText(value);
            // If the attribute has already been overridden, assume it has been done everywhere the user deemed necessary.
            // So do not create new api folders in that case.
            apiInformation.toBeCopied = null;
          }
          else {
            // The value didn't exist, add it.
            XmlTag child = sourceXml.createChildTag(SdkConstants.TAG_ITEM, sourceXml.getNamespace(), value, false);
            child.setAttribute(SdkConstants.ATTR_NAME, attribute);
            sourceXml.addSubTag(child, false);
          }
        }

        if (apiInformation.toBeCopied != null) {
          // copy this theme at the minimum api level for this attribute
          ThemeEditorUtils.copyTheme(minAcceptableApi, apiInformation.toBeCopied);

          AndroidFacet facet = AndroidFacet.getInstance(myConfiguration.getModule());
          if (facet != null) {
            facet.refreshResources();
          }
          List<ResourceItem> newResources = getStyleResourceItems();
          VersionQualifier qualifier = new VersionQualifier(minAcceptableApi);
          for (ResourceItem resourceItem : newResources) {
            if (resourceItem.getQualifiers().contains(qualifier.getFolderSegment())) {
              final XmlTag sourceXml = LocalResourceRepository.getItemTag(myProject, resourceItem);
              assert sourceXml != null;

              final XmlTag child = sourceXml.createChildTag(SdkConstants.TAG_ITEM, sourceXml.getNamespace(), value, false);
              child.setAttribute(SdkConstants.ATTR_NAME, attribute);
              sourceXml.addSubTag(child, false);
              break;
            }
          }
        }
      }
    }.execute();
  }

  /**
   * Changes the name of the themes in all the xml files
   * The theme needs to be reloaded in ThemeEditorComponent for the change to be complete
   * THIS METHOD DOES NOT DIRECTLY MODIFY THE VALUE ONE GETS WHEN EVALUATING getParent()
   */
  public void setParent(@NotNull final String newParent) {
    if (!isProjectStyle()) {
      throw new UnsupportedOperationException("Non project styles can not be modified");
    }

    final int minAcceptableApi = ThemeEditorUtils.getOriginalApiLevel(newParent, myProject);
    final ApiInformation apiInformation = new ApiInformation(minAcceptableApi);
    Collection<PsiFile> toBeEdited = new HashSet<PsiFile>();
    for (XmlTag sourceXml : apiInformation.sources) {
      toBeEdited.add(sourceXml.getContainingFile());
    }

    new WriteCommandAction.Simple(myProject, "Updating parent to " + newParent, toBeEdited.toArray(new PsiFile[toBeEdited.size()])) {
      @Override
      protected void run() {
        // Makes the command global even if only one xml file is modified
        // That way, the Undo is always available from the theme editor
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);

        for (XmlTag sourceXml : apiInformation.sources) {
          sourceXml.setAttribute(SdkConstants.ATTR_PARENT, newParent);
        }
        if (apiInformation.toBeCopied != null) {
          // copy this theme at the minimum api level for this parent
          ThemeEditorUtils.copyTheme(minAcceptableApi, apiInformation.toBeCopied);

          AndroidFacet facet = AndroidFacet.getInstance(myConfiguration.getModule());
          if (facet != null) {
            facet.refreshResources();
          }
          List<ResourceItem> newResources = getStyleResourceItems();
          VersionQualifier qualifier = new VersionQualifier(minAcceptableApi);
          for (ResourceItem resourceItem : newResources) {
            if (resourceItem.getQualifiers().contains(qualifier.getFolderSegment())) {
              final XmlTag sourceXml = LocalResourceRepository.getItemTag(myProject, resourceItem);
              assert sourceXml != null;

              sourceXml.setAttribute(SdkConstants.ATTR_PARENT, newParent);
              break;
            }
          }
        }
      }
    }.execute();
  }

  @NotNull
  public StyleResolver getResolver() {
    return myThemeResolver;
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
    List<ResourceItem> resources = getStyleResourceItems();
    if (resources.isEmpty()){
      return null;
    }
    // Any sourceXml will do to get the name attribute from
    final XmlTag sourceXml = LocalResourceRepository.getItemTag(myProject, resources.get(0));
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
   * Class containing all the information needed to correctly set attributes with respect to api levels
   */
  private class ApiInformation {
    /** Sources to be edited when setting the attribute */
    private final Collection<XmlTag> sources = new HashSet<XmlTag>();
    /** Theme to be copied to a new file if needed */
    private XmlTag toBeCopied = null;

    private ApiInformation(int minAcceptableApi) {
      int minApiLevel = ThemeEditorUtils.getMinApiLevel(myConfiguration.getModule());
      int closestNonAllowedApi = 0;
      boolean createNewTheme = true;

      if (minAcceptableApi < minApiLevel) {
        // Do not create a theme for an api level inferior to the min api level of the project
        createNewTheme = false;
      }

      for (ResourceItem resourceItem : getStyleResourceItems()) {
        FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForQualifierString(resourceItem.getQualifiers());
        int version = minApiLevel;
        if (folderConfiguration != null) {
          VersionQualifier versionQualifier = folderConfiguration.getVersionQualifier();
          if (versionQualifier != null && versionQualifier.isValid()) {
            version = versionQualifier.getVersion();
          }
        }

        final XmlTag sourceXml = LocalResourceRepository.getItemTag(myProject, resourceItem);
        assert sourceXml != null;
        if (version < minAcceptableApi) {
          // attribute not defined for api levels less than minAcceptableApi
          if (version > closestNonAllowedApi) {
            closestNonAllowedApi = version;
            toBeCopied = sourceXml;
          }
          continue;
        }
        if (version == minAcceptableApi) {
          // This theme already exists at its minimum api level, no need to create it
          createNewTheme = false;
        }
        sources.add(sourceXml);
      }
      if (!createNewTheme) {
        toBeCopied = null;
      }
    }
  }

  /**
   * Returns whether this style is public.
   */
  public boolean isPublic() {
    if (!myIsFrameworkStyle) {
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
}
