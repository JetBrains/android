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
package com.android.tools.idea.rendering;

import com.android.SdkConstants;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightVirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class LocalResourceRepositoryAsVirtualFile extends LightVirtualFile {
  private final LocalResourceRepository myRepository;
  private VirtualFile myResourceRoot;
  private Project myProject;
  private String myName;

  @Nullable
  public static LocalResourceRepositoryAsVirtualFile getInstance(@NotNull Project project, @NotNull VirtualFile file) {
    Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null) {
      return null;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }
    LocalResourceRepositoryAsVirtualFile repositoryFile = ProjectResourceRepository.getProjectResources(facet, true).asVirtualFile();
    // AndroidFacet.getPrimaryResourceDir() is what AndroidResourceUtil uses
    //noinspection deprecation
    VirtualFile resourceRoot = facet.getPrimaryResourceDir();
    if (resourceRoot == null) {
      return null;
    }
    repositoryFile.setResourceRoot(resourceRoot);
    return repositoryFile;
  }

  LocalResourceRepositoryAsVirtualFile(@NotNull LocalResourceRepository repository) {
    super("", new LocalResourceRepositoryFileType(), "");
    myRepository = repository;
    myName = myRepository.getDisplayName();
  }

  /**
   * Sets the value of an attribute for resource items.  If SdkConstants.ATTR_NAME is set to null or "", the items are deleted.
   * @param attribute The attribute whose value we wish to change
   * @param value The desired attribute value
   * @param items The resource items
   * @return True if the value was successfully set, false otherwise
   */
  public boolean setAttributeForItems(@NotNull final String attribute, @Nullable final String value, @NotNull ResourceItem... items) {
    if (items.length <= 0) {
      return false;
    }
    final List<XmlTag> tags = Lists.newArrayListWithExpectedSize(items.length);
    final Set<PsiFile> files = Sets.newHashSetWithExpectedSize(items.length);
    for (ResourceItem item : items) {
      XmlTag tag = StringResourceData.resourceToXmlTag(item);
      if (tag == null) {
        return false;
      }
      tags.add(tag);
      files.add(tag.getContainingFile());
    }
    final boolean deleteTag = attribute.equals(SdkConstants.ATTR_NAME) && (value == null || value.isEmpty());
    new WriteCommandAction.Simple(myProject, "Setting attribute " + attribute, files.toArray(new PsiFile[files.size()])) {
      @Override
      public void run() {
        for (XmlTag tag : tags) {
          if (deleteTag) {
            tag.delete();
          } else {
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
   * @param item The resource item
   * @param value The desired text
   * @return True if the text was successfully set, false otherwise
   */
  public boolean setItemText(@NotNull ResourceItem item, @NotNull final String value) {
    if (value.isEmpty()) {
      // Deletes the tag
      return setAttributeForItems(SdkConstants.ATTR_NAME, null, item);
    }
    final XmlTag tag = StringResourceData.resourceToXmlTag(item);
    if (tag != null) {
      new WriteCommandAction.Simple(myProject, "Setting value of " + item.getName(), tag.getContainingFile()) {
        @Override
        public void run() {
          tag.getValue().setText(value);
        }
      }.execute();
      return true;
    }
    return false;
  }

  /**
   * Creates a string resource in the specified locale.
   * @param locale The locale in which to create the resource
   * @param name The name of the string resource
   * @param value The desired value
   * @param translatable Whether the resource is translatable
   * @return True if the resource was successfully created, false otherwise
   */
  public boolean createItem(@Nullable Locale locale, @NotNull final String name, @NotNull final String value, final boolean translatable) {
    XmlFile resourceFile = getStringResourceFile(locale);
    if (resourceFile == null) {
      return false;
    }
    final XmlTag root = resourceFile.getRootTag();
    if (root == null) {
      return false;
    }
    new WriteCommandAction.Simple(myProject, "Creating string " + name, resourceFile) {
      @Override
      public void run() {
        // AndroidResourceUtil.createValueResource tries to format the value it is passed (e.g., by escaping quotation marks)
        // We want to save the text exactly as entered by the user, so we create and add the XML tag directly
        XmlTag child = root.createChildTag(ResourceType.STRING.getName(), root.getNamespace(), value, false);
        child.setAttribute(SdkConstants.ATTR_NAME, name);
        // XmlTagImpl handles a null value by deleting the attribute, which is our desired behavior
        //noinspection ConstantConditions
        child.setAttribute(SdkConstants.ATTR_TRANSLATABLE, translatable ? null : SdkConstants.VALUE_FALSE);
        root.addSubTag(child, false);
      }
    }.execute();
    return true;
  }

  @Nullable
  private XmlFile getStringResourceFile(@Nullable Locale locale) {
    FolderConfiguration configuration = new FolderConfiguration();
    if (locale != null) {
      configuration.setLanguageQualifier(locale.language);
      if (locale.hasRegion()) {
        configuration.setRegionQualifier(locale.region);
      }
    }
    PsiManager manager = PsiManager.getInstance(myProject);
    final String valuesFolderName = configuration.getFolderName(ResourceFolderType.VALUES);
    VirtualFile valuesFolder = myResourceRoot.findChild(valuesFolderName);
    if (valuesFolder == null) {
      valuesFolder =
        new WriteCommandAction<VirtualFile>(myProject, "Creating directory " + valuesFolderName, manager.findFile(myResourceRoot)) {
          @Override
          public void run(@NotNull Result<VirtualFile> result) {
            try {
              result.setResult(myResourceRoot.createChildDirectory(this, valuesFolderName));
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
      } catch (Exception ex) {
        return null;
      }
    } else {
      PsiFile resourcePsiFile = manager.findFile(resourceVirtualFile);
      if (!(resourcePsiFile instanceof XmlFile)) {
        return null;
      }
      resourceFile = (XmlFile) resourcePsiFile;
    }

    return resourceFile;
  }

  public void setIcon(Icon icon) {
    if (getAssignedFileType() != null) {
      ((LocalResourceRepositoryFileType)getAssignedFileType()).setIcon(icon);
    }
  }

  @NotNull
  public LocalResourceRepository getRepository() {
    return myRepository;
  }

  public void setResourceRoot(@NotNull VirtualFile resourceRoot) {
    myResourceRoot = resourceRoot;
  }

  public void setProject(@NotNull Project project) {
    myProject = project;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public long getTimeStamp() {
    return myRepository.getModificationCount();
  }

  private static class LocalResourceRepositoryFileType extends FakeFileType {
    private Icon myIcon;

    public LocalResourceRepositoryFileType() {
      myIcon = AndroidIcons.Android;
    }

    @NotNull
    @Override
    public String getName() {
      return "Android Local Resource Repository";
    }

    @NotNull
    @Override
    public String getDescription() {
      return "Android Local Resource Repository Files";
    }

    public void setIcon(@NotNull Icon icon) {
      myIcon = icon;
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myIcon;
    }

    @Override
    public boolean isMyFileType(VirtualFile file) {
      return file instanceof LocalResourceRepositoryAsVirtualFile;
    }
  }
}
