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
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.StyleResolver;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
* Wrapper for style configurations that allows modifying attributes directly in the XML file.
*/
public class ThemeEditorStyle {
  private static final Logger LOG = Logger.getInstance(ThemeEditorStyle.class);

  private final StyleResolver myThemeResolver;
  private final boolean myIsProjectStyle;
  private final boolean myIsFrameworkStyle;
  private final String myStyleName;
  private final Configuration myConfiguration;
  private final Project myProject;
  public ThemeEditorStyle(@NotNull StyleResolver resolver,
                   @NotNull Configuration configuration,
                   @NotNull  String styleName,
                   boolean isFrameworkStyle) {
    myIsFrameworkStyle = isFrameworkStyle;
    myThemeResolver = resolver;
    myConfiguration = configuration;

    myStyleName = styleName;
    myProject = configuration.getModule().getProject();

    if (!myIsFrameworkStyle) {
      //TODO: Do it Better
      XmlTag sourceXml = getSourceXmls().get(0);
      /*
       * Find if the file is contained in the resources folder. If the source file is not contained in the source folder this might be
       * coming from a library that we can not actually edit.
       */
      VirtualFile parent = sourceXml.getContainingFile() != null && sourceXml.getContainingFile().getVirtualFile() != null ?
                           sourceXml.getContainingFile().getVirtualFile().getParent() :
                           null;
      myIsProjectStyle =
        parent != null && parent.getParent() != null && AndroidResourceUtil.isLocalResourceDirectory(parent.getParent(), myProject);
    } else {
      myIsProjectStyle = false;
    }
  }

  private StyleResourceValue getStyleResourceValue() {
    return myConfiguration.getResourceResolver().getStyle(myStyleName, myIsFrameworkStyle);
  }

  private List<XmlTag> getSourceXmls() {
    assert !myIsFrameworkStyle;
    LocalResourceRepository repository = AppResourceRepository.getAppResources(myConfiguration.getModule(), true);
    assert repository != null;
    List<ResourceItem> resources = repository.getResourceItem(ResourceType.STYLE, myStyleName);
    List<XmlTag> xmlTags = new ArrayList<XmlTag>();
    for (ResourceItem resource : resources) {
      XmlTag tag = LocalResourceRepository.getItemTag(myProject, resource);
      assert tag != null;
      xmlTags.add(tag);
    }
    return xmlTags;
  }

  /**
   * Returns whether this is a project style and therefore editable. If the style is not part of the project, it can be part or the framework
   * or a library.
   */
  public boolean isProjectStyle() {
    return myIsProjectStyle;
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

  /**
   * Returns the style parent or null if this is a root style.
   */
  @Nullable
  public ThemeEditorStyle getParent() {
    StyleResourceValue parent = myConfiguration.getResourceResolver().getParent(getStyleResourceValue());
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
   * Returns whether an attribute is locally defined by this style or not.
   * @param attribute The style attribute name.
   */
  public boolean isAttributeDefined(@NotNull String attribute) {
    return getStyleResourceValue().getNames().contains(attribute);
  }

  @Nullable
  public String getValue(@NotNull final String attribute) {
    throw new UnsupportedOperationException();
  }

  /**
   * Sets the attribute value and returns whether the value was created or just modified.
   * @param attribute The style attribute name.
   * @param value The attribute value.
   */
  public boolean setValue(@NotNull final String attribute, @NotNull final String value) {
    if (!isProjectStyle()) {
      throw new UnsupportedOperationException("Non project styles can not be modified");
    }

    for (final XmlTag sourceXml : getSourceXmls()) {
      // TODO: Check if the current value is defined by one of the parents and remove the attribute.
      final XmlTag tag = getValueTag(sourceXml, attribute);
      if (tag != null) {
        // Update the value.
        new WriteCommandAction.Simple(myProject, "Setting value of " + tag.getName(), tag.getContainingFile()) {
          @Override
          public void run() {
            tag.getValue().setEscapedText(value);
          }
        }.execute();
        continue;
      }

      // The value didn't exist, add it.
      final XmlTag child = sourceXml.createChildTag(SdkConstants.TAG_ITEM, sourceXml.getNamespace(), value, false);
      child.setAttribute(SdkConstants.ATTR_NAME, attribute);
      new WriteCommandAction.Simple(myProject, "Adding value of " + child.getName(), child.getContainingFile()) {
        @Override
        public void run() {
          sourceXml.addSubTag(child, false);
        }
      }.execute();
    }
    return true;
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

    for (final XmlTag sourceXml : getSourceXmls()) {
      new WriteCommandAction.Simple(myProject, "Updating parent to " + newParent) {
        @Override
        protected void run() throws Throwable {
          sourceXml.setAttribute(SdkConstants.ATTR_PARENT, newParent);
        }
      }.execute();
    }
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
   * Deletes an attribute of that particular style from the xml file
   */
  public void removeAttribute(@NotNull final String attribute) {
    if (!isProjectStyle()) {
      throw new UnsupportedOperationException("Non project styles can not be modified");
    }

    for (XmlTag sourceXml : getSourceXmls()) {
      final XmlTag tag = getValueTag(sourceXml, attribute);
      if (tag != null) {
        new WriteCommandAction.Simple(myProject, "Removing " + tag.getName(), tag.getContainingFile()) {
          @Override
          public void run() {
            tag.delete();
          }
        }.execute();
      }
    }
  }

  /**
   * Returns a PsiElement of the name attribute for this theme
   * made from a RANDOM sourceXml
   */
  @Nullable
  public PsiElement getNamePsiElement() {
    List<XmlTag> sourceXmls = getSourceXmls();
    if (sourceXmls.isEmpty()){
      return null;
    }
    // Any sourceXml will do to get the name attribute from
    final XmlAttribute nameAttribute = sourceXmls.get(0).getAttribute("name");
    if (nameAttribute == null) {
      return null;
    }

    XmlAttributeValue attributeValue = nameAttribute.getValueElement();
    if (attributeValue == null) {
      return null;
    }

    return new ValueResourceElementWrapper(attributeValue);
  }
}
