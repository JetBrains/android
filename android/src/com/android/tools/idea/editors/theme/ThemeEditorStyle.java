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
package com.android.tools.idea.editors.theme;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.tools.idea.configurations.Configuration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collection;

/**
* Wrapper for style configurations that allows modifying attributes directly in the XML file.
*/
public class ThemeEditorStyle {
  private static final Logger LOG = Logger.getInstance(ThemeEditorStyle.class);

  private final StyleResolver myThemeResolver;
  private final Project myProject;
  private final WeakReference<XmlTag> mySourceXml;
  private final boolean isProjectStyle;
  private final boolean isFrameworkStyle;
  private final StyleResourceValue myStyleData;
  private final Configuration myConfiguration;

  ThemeEditorStyle(@NotNull StyleResolver resolver,
                   @NotNull Project project,
                   @NotNull Configuration configuration,
                   @NotNull StyleResourceValue styleValue,
                   @Nullable XmlTag sourceXml) {
    myThemeResolver = resolver;
    myProject = project;
    myConfiguration = configuration;
    mySourceXml = new WeakReference<XmlTag>(sourceXml);
    myStyleData = styleValue;

    if (sourceXml != null && sourceXml.isValid()) {
      isFrameworkStyle = false;
      /*
       * Find if the file is contained in the resources folder. If the source file is not contained in the source folder this might be
       * coming from a library that we can not actually edit.
       */
      VirtualFile parent = sourceXml.getContainingFile() != null && sourceXml.getContainingFile().getVirtualFile() != null ?
                           sourceXml.getContainingFile().getVirtualFile().getParent() :
                           null;
      isProjectStyle =
        parent != null && parent.getParent() != null && AndroidResourceUtil.isLocalResourceDirectory(parent.getParent(), project);
    } else {
      isFrameworkStyle = true;
      isProjectStyle = false;
    }
  }

  public boolean isFrameworkStyle() {
    return isFrameworkStyle;
  }

  /**
   * Returns whether this a project style and therefore editable. If the style it's not part of the project, it can be part or the framework
   * or a library.
   */
  public boolean isProjectStyle() {
    return isProjectStyle;
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
    return StyleResolver.getQualifiedStyleName(myStyleData);
  }

  /**
   * Returns the style name without namespaces or prefixes.
   */
  @NotNull
  public String getSimpleName() {
    return myStyleData.getName();
  }

  /**
   * Returns the theme values.
   */
  @NotNull
  public Collection<ItemResourceValue> getValues() {
    return myStyleData.getValues();
  }

  /**
   * Returns the style parent or null if this is a root style.
   */
  @Nullable
  public ThemeEditorStyle getParent() {
    StyleResourceValue parent = myConfiguration.getResourceResolver().getParent(myStyleData);
    if (parent == null) {
      return null;
    }

    return myThemeResolver.getStyle(StyleResolver.getQualifiedStyleName(parent));
  }

  /**
   * Returns the XmlTag that contains the value for a given attribute in the current style.
   * @param attribute The style attribute name.
   * @return The {@link com.intellij.psi.xml.XmlTag} or null if the attribute does not exist in this theme.
   */
  @Nullable
  protected XmlTag getValueTag(@NotNull final String attribute) {
    if (!isProjectStyle()) {
      // Non project styles do not contain local values.
      return null;
    }

    XmlTag sourceTag = mySourceXml.get();
    if (sourceTag == null || !sourceTag.isValid()) {
      LOG.error("Xml source is gone");
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
    return myStyleData.getNames().contains(attribute);
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

    // TODO: Check if the current value is defined by one of the parents and remove the attribute.
    final XmlTag tag = getValueTag(attribute);
    if (tag != null) {
      // Update the value.
      new WriteCommandAction.Simple(myProject, "Setting value of " + tag.getName(), tag.getContainingFile()) {
        @Override
        public void run() {
          tag.getValue().setEscapedText(value);
        }
      }.execute();

      return true;
    }

    final XmlTag sourceTag = mySourceXml.get();
    if (sourceTag == null) {
      LOG.error("Xml source is gone");
      return false;
    }

    // The value didn't exist, add it.
    final XmlTag child = sourceTag.createChildTag(SdkConstants.TAG_ITEM, sourceTag.getNamespace(), value, false);
    child.setAttribute(SdkConstants.ATTR_NAME, attribute);
    new WriteCommandAction.Simple(myProject, "Adding value of " + child.getName(), child.getContainingFile()) {
      @Override
      public void run() {
        sourceTag.addSubTag(child, false);
      }
    }.execute();

    return true;
  }

  public void setParent(@NotNull final String newParent) {
    if (!isProjectStyle()) {
      throw new UnsupportedOperationException("Non project styles can not be modified");
    }

    final XmlTag tag = mySourceXml.get();
    if (tag == null) {
      LOG.warn("Unable to set parent, tag is null");
      return;
    }

    new WriteCommandAction.Simple(myProject, "Updating parent to " + newParent) {
      @Override
      protected void run() throws Throwable {
        tag.setAttribute(SdkConstants.ATTR_PARENT, newParent);
      }
    }.execute();
  }

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
}
