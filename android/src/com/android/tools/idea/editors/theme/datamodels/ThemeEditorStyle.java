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
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.actions.OverrideResourceAction;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.StyleResolver;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.rendering.*;
import com.android.tools.lint.checks.ApiLookup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.inspections.lint.IntellijLintClient;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
* Wrapper for style configurations that allows modifying attributes directly in the XML file.
*/
public class ThemeEditorStyle {
  private static final Logger LOG = Logger.getInstance(ThemeEditorStyle.class);

  private final StyleResolver myThemeResolver;
  private final boolean myIsFrameworkStyle;
  private final String myStyleName;
  private final Configuration myConfiguration;
  private final Project myProject;

  public ThemeEditorStyle(@NotNull StyleResolver resolver,
                          @NotNull Configuration configuration,
                          @NotNull String styleName,
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

  @NotNull
  private StyleResourceValue getStyleResourceValue() {
    ResourceResolver resolver = myConfiguration.getResourceResolver();
    assert resolver != null;

    StyleResourceValue result = resolver.getStyle(myStyleName, myIsFrameworkStyle);
    assert result != null;

    return result;
  }

  @NotNull
  private List<ResourceItem> getResources() {
    assert !myIsFrameworkStyle;
    LocalResourceRepository repository = AppResourceRepository.getAppResources(myConfiguration.getModule(), true);
    assert repository != null;
    List<ResourceItem> resources = repository.getResourceItem(ResourceType.STYLE, myStyleName);
    return resources != null ? resources : Collections.<ResourceItem>emptyList();
  }

  /**
   * Returns whether this style is editable.
   */
  public boolean isReadOnly() {
    return !isProjectStyle();
  }

  /**
   * Returns the style name. If this is a framework style, it will include the "android:" prefix.
   */
  @NotNull
  public String getName() {
    return StyleResolver.getQualifiedStyleName(getStyleResourceValue());
  }

  /**
   * Returns the style name without namespaces or prefixes.
   */
  @NotNull
  public String getSimpleName() {
    return getStyleResourceValue().getName();
  }

  /**
   * Returns the theme values.
   */
  @NotNull
  public Collection<ItemResourceValue> getValues() {
    return getStyleResourceValue().getValues();
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

  @Nullable
  public String getValue(@NotNull final String attribute) {
    throw new UnsupportedOperationException();
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

    int minApiLevel = ThemeEditorUtils.getMinApiLevel(myConfiguration.getModule());
    int attributeMinApi = minApiLevel;
    if (attribute.startsWith(SdkConstants.ANDROID_NS_NAME_PREFIX)) {
      ApiLookup apiLookup = IntellijLintClient.getApiLookup(myProject);
      assert apiLookup != null;
      attributeMinApi = apiLookup.getFieldVersion("android/R$attr", attribute.substring(SdkConstants.ANDROID_NS_NAME_PREFIX_LEN));
    }

    boolean createAtMinLevel = true;
    XmlTag closestNonAllowedVersion = null;
    int closestNonAllowedApi = 0;

    final Collection<XmlTag> sources = new HashSet<XmlTag>();

    for (ResourceItem resourceItem : getResources()) {
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
      if (version < attributeMinApi) {
        // attribute not defined for api levels less than attributeMinApi
        if (version > closestNonAllowedApi) {
          closestNonAllowedApi = version;
          closestNonAllowedVersion = sourceXml;
        }
        continue;
      }
      if (version == attributeMinApi) {
        createAtMinLevel = false;
      }
      sources.add(sourceXml);
    }

    writeValues(sources, attribute, value, createAtMinLevel, attributeMinApi, closestNonAllowedVersion);
  }

  private void writeValues(@NotNull final Collection<XmlTag> sources, @NotNull final String attribute, @NotNull final String value,
                           final boolean createAtMinLevel, final int attributeMinApi, @Nullable final XmlTag closestNonAllowedVersion) {
    Collection<PsiFile> toBeEdited = new HashSet<PsiFile>();
    for (XmlTag sourceXml : sources) {
      toBeEdited.add(sourceXml.getContainingFile());
    }

    new WriteCommandAction.Simple(myProject, "Setting value of " + attribute, toBeEdited.toArray(new PsiFile[toBeEdited.size()])) {
      @Override
      protected void run() {
        boolean createNewTheme = createAtMinLevel;
        for (XmlTag sourceXml : sources) {
          // TODO: Check if the current value is defined by one of the parents and remove the attribute.
          XmlTag tag = getValueTag(sourceXml, attribute);
          if (tag != null) {
            tag.getValue().setEscapedText(value);
            // If the attribute has already been overridden, assume it has been done everywhere the user deemed necessary.
            // So do not create new api folders in that case.
            createNewTheme = false;
          }
          else {
            // The value didn't exist, add it.
            XmlTag child = sourceXml.createChildTag(SdkConstants.TAG_ITEM, sourceXml.getNamespace(), value, false);
            child.setAttribute(SdkConstants.ATTR_NAME, attribute);
            sourceXml.addSubTag(child, false);
          }
        }

        if (createNewTheme) {
          // copy this theme at the minimum api level for this attribute
          copyTheme(attributeMinApi, closestNonAllowedVersion, attribute, value);
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

    Collection<PsiFile> toBeEdited = new HashSet<PsiFile>();
    final Collection<XmlTag> sources = new HashSet<XmlTag>();
    for (ResourceItem resourceItem : getResources()) {
      final XmlTag sourceXml = LocalResourceRepository.getItemTag(myProject, resourceItem);
      assert sourceXml != null;
      toBeEdited.add(sourceXml.getContainingFile());
      sources.add(sourceXml);
    }

    new WriteCommandAction.Simple(myProject, "Updating parent to " + newParent, toBeEdited.toArray(new PsiFile[toBeEdited.size()])) {
      @Override
      protected void run() {
        for (XmlTag sourceXml : sources) {
          sourceXml.setAttribute(SdkConstants.ATTR_PARENT, newParent);
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
      return "[" + getSimpleName() + "]";
    }

    return getSimpleName();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || (!(obj instanceof ThemeEditorStyle))) {
      return false;
    }

    return getName().equals(((ThemeEditorStyle)obj).getName());
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
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
    for (ResourceItem resourceItem : getResources()) {
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
    List<ResourceItem> resources = getResources();
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
   * Copies a theme to a values folder with api version apiLevel
   * Adds a new attribute to that copied theme
   * @param apiLevel api level of the folder the theme is copied to
   * @param toBeCopied theme to be copied
   * @param attribute name of the new attribute
   * @param value value of the new attribute
   */
  private void copyTheme(int apiLevel, @Nullable final XmlTag toBeCopied, @NotNull String attribute, @NotNull final String value) {
    if (toBeCopied == null) {
      // No Theme is defined under the min allowed api level
      return;
    }

    PsiFile file = toBeCopied.getContainingFile();
    assert file instanceof XmlFile : file;
    ResourceFolderType folderType = ResourceHelper.getFolderType(file);
    assert folderType != null : file;
    FolderConfiguration config = ResourceHelper.getFolderConfiguration(file);
    assert config != null : file;

    VersionQualifier qualifier = new VersionQualifier(apiLevel);
    config.setVersionQualifier(qualifier);
    String folder = config.getFolderName(folderType);
    final AndroidLintQuickFix action = OverrideResourceAction.createFix(folder);
    // Context needed for calls on action, but has no effect, simply has to be non null
    final AndroidQuickfixContexts.DesignerContext context = AndroidQuickfixContexts.DesignerContext.getInstance();

    // Copies the theme to the new file
    action.apply(toBeCopied, toBeCopied, context);

    AndroidFacet facet = AndroidFacet.getInstance(myConfiguration.getModule());
    if (facet != null) {
      facet.refreshResources();
    }
    List<ResourceItem> newResources = getResources();
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

    return androidTargetData.isResourcePublic(ResourceType.STYLE.getName(), getSimpleName());
  }
}
