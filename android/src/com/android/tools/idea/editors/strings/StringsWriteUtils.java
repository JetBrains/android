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
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ValueXmlHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.util.concurrency.EdtExecutorService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringsWriteUtils {
  public static void removeLocale(@NotNull Locale locale, @NotNull AndroidFacet facet, @NotNull Object requestor) {
    WriteCommandAction.writeCommandAction(facet.getModule().getProject())
      .withName("Remove " + locale + " Locale")
      .withGlobalUndo()
      .run(() -> {
        FolderConfiguration configuration = new FolderConfiguration();
        configuration.setLocaleQualifier(locale.qualifier);

        String name = configuration.getFolderName(ResourceFolderType.VALUES);

        ResourceFolderManager.getInstance(facet).getFolders().stream()
          .map(directory -> directory.findChild(name))
          .filter(Objects::nonNull)
          .forEach(directory -> delete(directory, requestor));
      });
  }

  private static void delete(@NotNull VirtualFile file, @NotNull Object requestor) {
    try {
      file.delete(requestor);
    }
    catch (IOException exception) {
      Logger.getInstance(StringsWriteUtils.class).warn(exception);
    }
  }

  /**
   * Sets the value of an attribute for resource items.  If SdkConstants.ATTR_NAME is set to null or "", the items are deleted.
   *
   * @param attribute The attribute whose value we wish to change
   * @param value     The desired attribute value
   * @param items     The resource items
   * @return True if the value was successfully set, false otherwise
   */
  public static boolean setAttributeForItems(@NotNull Project project,
                                             @NotNull String attribute,
                                             @Nullable String value,
                                             @NotNull List<ResourceItem> items) {
    if (items.isEmpty()) {
      return false;
    }
    List<XmlTag> tags = new ArrayList<>(items.size());
    Set<PsiFile> files = Sets.newHashSetWithExpectedSize(items.size());
    for (ResourceItem item : items) {
      XmlTag tag = IdeResourcesUtil.getItemTag(project, item);
      if (tag == null) {
        return false;
      }
      tags.add(tag);
      files.add(tag.getContainingFile());
    }
    boolean deleteTag = attribute.equals(SdkConstants.ATTR_NAME) && (value == null || value.isEmpty());
    WriteCommandAction.writeCommandAction(project, files.toArray(PsiFile.EMPTY_ARRAY)).withName("Setting attribute " + attribute)
      .run(() -> {
        // Makes the command global even if only one xml file is modified
        // That way, the Undo is always available from the translation editor
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);

        for (XmlTag tag : tags) {
          if (deleteTag) {
            tag.delete();
          }
          else {
            // XmlTagImpl handles a null value by deleting the attribute, which is our desired behavior.
            tag.setAttribute(attribute, value);
          }
        }
      });
    return true;
  }

  /**
   * Sets the text value of a resource item.  If the value is the empty string, the item is deleted.
   *
   * @param item  The resource item
   * @param value The desired text
   * @return True if the text was successfully set, false otherwise
   */
  public static boolean setItemText(@NotNull Project project, @NotNull ResourceItem item, @NotNull String value) {
    if (value.isEmpty()) {
      // Deletes the tag
      return setAttributeForItems(project, SdkConstants.ATTR_NAME, null, Collections.singletonList(item));
    }
    XmlTag tag = IdeResourcesUtil.getItemTag(project, item);
    if (tag != null) {
      WriteCommandAction.writeCommandAction(project, tag.getContainingFile()).withName("Setting value of " + item.getName()).run(() -> {
        // Makes the command global even if only one xml file is modified.
        // That way, the Undo is always available from the translation editor.
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);

        // First remove the existing value of the tag (any text and possibly other XML nested tags - like xliff:g).
        for (XmlTagChild child : tag.getValue().getChildren()) {
          child.delete();
        }

        XmlElementFactory factory = XmlElementFactory.getInstance(project);

        // Encapsulate the value in a placeholder tag (see com.intellij.psi.XmlElementFactoryImpl.createDisplayText()).
        XmlTag text = factory.createTagFromText("<string>" + escapeResourceStringAsXml(value) + "</string>");

        for (PsiElement psiElement : text.getValue().getChildren()) {
          tag.add(psiElement);
        }
      });
      return true;
    }
    return false;
  }

  /**
   * Creates a string resource in the specified locale.
   *
   * @return a future referencing the resource item that was created, or null if it wasn't created or could not be read back.
   *     The future is guaranteed to be completed on the UI thread.
   */
  public static @NotNull ListenableFuture<ResourceItem> createItem(@NotNull AndroidFacet facet,
                                                                   @NotNull VirtualFile resFolder,
                                                                   @Nullable Locale locale,
                                                                   @NotNull String name,
                                                                   @NotNull String value,
                                                                   boolean translatable) {
    Project project = facet.getModule().getProject();
    XmlFile resourceFile = getStringResourceFile(project, resFolder, locale);
    if (resourceFile == null) {
      return Futures.immediateFuture(null);
    }
    XmlTag root = resourceFile.getRootTag();
    if (root == null) {
      return Futures.immediateFuture(null);
    }
    WriteCommandAction.writeCommandAction(project, resourceFile).withName("Creating string " + name).run(() -> {
      // Makes the command global even if only one xml file is modified
      // That way, the Undo is always available from the translation editor
      CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);

      XmlTag child = root.createChildTag(ResourceType.STRING.getName(), root.getNamespace(), escapeResourceStringAsXml(value), false);

      child.setAttribute(SdkConstants.ATTR_NAME, name);
      // XmlTagImpl handles a null value by deleting the attribute, which is our desired behavior
      child.setAttribute(SdkConstants.ATTR_TRANSLATABLE, translatable ? null : SdkConstants.VALUE_FALSE);

      root.addSubTag(child, false);
    });

    SettableFuture<ResourceItem> result = SettableFuture.create();
    LocalResourceRepository repository = ResourceRepositoryManager.getModuleResources(facet);
    // Ensure that items *just* created are processed by the resource repository.
    repository.invokeAfterPendingUpdatesFinish(EdtExecutorService.getInstance(), () -> {
      List<ResourceItem> items = repository.getResources(ResourceNamespace.TODO(), ResourceType.STRING, name);

      for (ResourceItem item : items) {
        FolderConfiguration config = item.getConfiguration();
        LocaleQualifier qualifier = config.getLocaleQualifier();

        Locale l = qualifier == null ? null : Locale.create(qualifier);
        if (Objects.equals(l, locale)) {
          result.set(item);
          break;
        }
      }

      result.set(null);
    });

    return result;
  }

  @Nullable
  static XmlFile getStringResourceFile(@NotNull Project project, @NotNull VirtualFile resFolder, @Nullable Locale locale) {
    FolderConfiguration configuration = new FolderConfiguration();
    if (locale != null) {
      configuration.setLocaleQualifier(locale.qualifier);
    }
    PsiManager manager = PsiManager.getInstance(project);
    String valuesFolderName = configuration.getFolderName(ResourceFolderType.VALUES);
    VirtualFile valuesFolder = resFolder.findChild(valuesFolderName);
    if (valuesFolder == null) {
      try {
        valuesFolder = WriteCommandAction.writeCommandAction(project, manager.findFile(resFolder))
          .withName("Creating directory " + valuesFolderName)
          .compute(() -> resFolder.createChildDirectory(StringsWriteUtils.class, valuesFolderName));
      }
      catch (IOException e) {
        return null;
      }
    }
    String resourceFileName = IdeResourcesUtil.getDefaultResourceFileName(ResourceType.STRING);
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
        resourceFile = IdeResourcesUtil.createFileResource(resourceFileName, valuesDir, "", ResourceType.STRING.getName(), true);
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
