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
package com.android.tools.idea.editors.strings;

import com.android.SdkConstants;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ValueXmlHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.LocalResourceRepository;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class StringsWriteUtils {
  /**
   * Sets the value of an attribute for resource items.  If SdkConstants.ATTR_NAME is set to null or "", the items are deleted.
   *
   * @param attribute The attribute whose value we wish to change
   * @param value     The desired attribute value
   * @param items     The resource items
   * @return True if the value was successfully set, false otherwise
   */
  public static boolean setAttributeForItems(@NotNull Project project,
                                             @NotNull final String attribute,
                                             @Nullable final String value,
                                             @NotNull List<ResourceItem> items) {
    if (items.isEmpty()) {
      return false;
    }
    final List<XmlTag> tags = Lists.newArrayListWithExpectedSize(items.size());
    final Set<PsiFile> files = Sets.newHashSetWithExpectedSize(items.size());
    for (ResourceItem item : items) {
      XmlTag tag = LocalResourceRepository.getItemTag(project, item);
      if (tag == null) {
        return false;
      }
      tags.add(tag);
      files.add(tag.getContainingFile());
    }
    final boolean deleteTag = attribute.equals(SdkConstants.ATTR_NAME) && (value == null || value.isEmpty());
    new WriteCommandAction.Simple(project, "Setting attribute " + attribute, files.toArray(new PsiFile[files.size()])) {
      @Override
      public void run() {
        for (XmlTag tag : tags) {
          if (deleteTag) {
            tag.delete();
          }
          else {
            // XmlTagImpl handles a null value by deleting the attribute, which is our desired behavior
            //noinspection ConstantConditions
            tag.setAttribute(attribute, value);
          }
        }
      }
    }.execute();
    return true;
  }

  /**
   * Sets the text value of a resource item.  If the value is the empty string, the item is deleted.
   *
   * @param item  The resource item
   * @param value The desired text
   * @return True if the text was successfully set, false otherwise
   */
  public static boolean setItemText(@NotNull final Project project, @NotNull ResourceItem item, @NotNull final String value) {
    if (value.isEmpty()) {
      // Deletes the tag
      return setAttributeForItems(project, SdkConstants.ATTR_NAME, null, Collections.singletonList(item));
    }
    final XmlTag tag = LocalResourceRepository.getItemTag(project, item);
    if (tag != null) {
      new WriteCommandAction.Simple(project, "Setting value of " + item.getName(), tag.getContainingFile()) {
        @Override
        public void run() {
          // First remove the existing value of the tag (any text and possibly other XML nested tags - like xliff:g).
          for (XmlTagChild child : tag.getValue().getChildren()) {
            child.delete();
          }

          XmlElementFactory factory = XmlElementFactory.getInstance(project);

          // Encapsulate the value in a dummy tag (see com.intellij.psi.XmlElementFactoryImpl.createDisplayText()).
          XmlTag text = factory.createTagFromText("<string>" + escapeResourceStringAsXml(value) + "</string>");

          for (PsiElement psiElement : text.getValue().getChildren()) {
            tag.add(psiElement);
          }
        }
      }.execute();
      return true;
    }
    return false;
  }

  /**
   * Creates a string resource in the specified locale.
   *
   * @return the resource item that was created, null if it wasn't created or could not be read back
   */
  @Nullable
  public static ResourceItem createItem(@NotNull final AndroidFacet facet,
                                        @NotNull VirtualFile resFolder,
                                        @Nullable final Locale locale,
                                        @NotNull final String name,
                                        @NotNull final String value,
                                        final boolean translatable) {
    Project project = facet.getModule().getProject();
    XmlFile resourceFile = getStringResourceFile(project, resFolder, locale);
    if (resourceFile == null) {
      return null;
    }
    final XmlTag root = resourceFile.getRootTag();
    if (root == null) {
      return null;
    }
    new WriteCommandAction.Simple(project, "Creating string " + name, resourceFile) {
      @Override
      public void run() {
        XmlTag child = root.createChildTag(ResourceType.STRING.getName(), root.getNamespace(), escapeResourceStringAsXml(value), false);

        child.setAttribute(SdkConstants.ATTR_NAME, name);
        // XmlTagImpl handles a null value by deleting the attribute, which is our desired behavior
        //noinspection ConstantConditions
        child.setAttribute(SdkConstants.ATTR_TRANSLATABLE, translatable ? null : SdkConstants.VALUE_FALSE);

        root.addSubTag(child, false);
      }
    }.execute();

    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      return getStringResourceItem(facet, name, locale);
    }
    else {
      return ApplicationManager.getApplication().runReadAction(new Computable<ResourceItem>() {
        @Override
        public ResourceItem compute() {
          return getStringResourceItem(facet, name, locale);
        }
      });
    }
  }

  @Nullable
  private static ResourceItem getStringResourceItem(@NotNull AndroidFacet facet, @NotNull String key, @Nullable Locale locale) {
    LocalResourceRepository repository = facet.getModuleResources(true);
    // Ensure that items *just* created are processed by the resource repository
    repository.sync();
    List<ResourceItem> items = repository.getResourceItem(ResourceType.STRING, key);
    if (items == null) {
      return null;
    }

    for (ResourceItem item : items) {
      FolderConfiguration config = item.getConfiguration();
      LocaleQualifier qualifier = config == null ? null : config.getLocaleQualifier();

      if (qualifier == null) {
        if (locale == null) {
          return item;
        }
        else {
          continue;
        }
      }

      Locale l = Locale.create(qualifier);
      if (l.equals(locale)) {
        return item;
      }
    }

    return null;
  }

  @Nullable
  private static XmlFile getStringResourceFile(@NotNull Project project, @NotNull final VirtualFile resFolder, @Nullable Locale locale) {
    FolderConfiguration configuration = new FolderConfiguration();
    if (locale != null) {
      configuration.setLocaleQualifier(locale.qualifier);
    }
    PsiManager manager = PsiManager.getInstance(project);
    final String valuesFolderName = configuration.getFolderName(ResourceFolderType.VALUES);
    VirtualFile valuesFolder = resFolder.findChild(valuesFolderName);
    if (valuesFolder == null) {
      valuesFolder = new WriteCommandAction<VirtualFile>(project, "Creating directory " + valuesFolderName, manager.findFile(resFolder)) {
        @Override
        public void run(@NotNull Result<VirtualFile> result) {
          try {
            result.setResult(resFolder.createChildDirectory(this, valuesFolderName));
          }
          catch (IOException ex) {
            // Immediately after this, we handle the case where the result is null
            //noinspection ConstantConditions
            result.setResult(null);
          }
        }
      }.execute().getResultObject();
      if (valuesFolder == null) {
        return null;
      }
    }
    String resourceFileName = AndroidResourceUtil.getDefaultResourceFileName(ResourceType.STRING);
    if (resourceFileName == null) {
      return null;
    }
    VirtualFile resourceVirtualFile = valuesFolder.findChild(resourceFileName);
    XmlFile resourceFile;
    if (resourceVirtualFile == null) {
      PsiDirectory valuesDir = manager.findDirectory(valuesFolder);
      if (valuesDir == null) {
        return null;
      }
      try {
        resourceFile = AndroidResourceUtil.createFileResource(resourceFileName, valuesDir, "", ResourceType.STRING.getName(), true);
      }
      catch (Exception ex) {
        return null;
      }
    }
    else {
      PsiFile resourcePsiFile = manager.findFile(resourceVirtualFile);
      if (!(resourcePsiFile instanceof XmlFile)) {
        return null;
      }
      resourceFile = (XmlFile)resourcePsiFile;
    }

    return resourceFile;
  }

  @NotNull
  private static String escapeResourceStringAsXml(@NotNull String xml) {
    try {
      return ValueXmlHelper.escapeResourceStringAsXml(xml);
    }
    catch (IllegalArgumentException exception) {
      // TODO Let the user know they've entered invalid XML
      Logger.getInstance(StringsWriteUtils.class).warn(exception);
      return xml;
    }
  }
}
