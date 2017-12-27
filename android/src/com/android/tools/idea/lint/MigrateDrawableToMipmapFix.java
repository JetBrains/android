/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ProjectResourceRepository;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.psi.SearchUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;

/**
 * Quickfix which migrates a drawable resource into a mipmap resource, moving bitmap and drawable XML
 * folders into mipmap folders (created if necessary) as well as updating resource references in XML
 * and Java files
 */
class MigrateDrawableToMipmapFix implements AndroidLintQuickFix {
  private final ResourceUrl myUrl;

  MigrateDrawableToMipmapFix(@NotNull ResourceUrl url) {
    myUrl = url;
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    Project project = startElement.getProject();
    AndroidFacet facet = AndroidFacet.getInstance(startElement);
    if (facet == null) {
      return;
    }

    final List<PsiFile> bitmaps = Lists.newArrayList();
    final Set<PsiElement> references = Sets.newHashSet();

    GlobalSearchScope useScope = GlobalSearchScope.projectScope(project);
    ProjectResourceRepository projectResources = ProjectResourceRepository.getOrCreateInstance(facet);
    List<ResourceItem> resourceItems = projectResources.getResourceItem(myUrl.type, myUrl.name);
    if (resourceItems != null) {
      for (ResourceItem item : resourceItems) {
        PsiFile file = LocalResourceRepository.getItemPsiFile(project, item);
        if (file == null) {
          continue;
        }
        bitmaps.add(file);

        Iterable<PsiReference> allReferences = SearchUtils.findAllReferences(file, useScope);
        for (PsiReference next : allReferences) {
          PsiElement element = next.getElement();
          if (element != null) {
            references.add(element);
          }
        }
      }
    }

    PsiField[] resourceFields = AndroidResourceUtil.findResourceFields(facet, ResourceType.DRAWABLE.getName(), myUrl.name, true);
    if (resourceFields.length == 1) {
      Iterable<PsiReference> allReferences = SearchUtils.findAllReferences(resourceFields[0], useScope);
      for (PsiReference next : allReferences) {
        PsiElement element = next.getElement();
        if (element != null) {
          references.add(element);
        }
      }
    }

    Set<PsiFile> applicableFiles = Sets.newHashSet();
    applicableFiles.addAll(bitmaps);
    for (PsiElement element : references) {
      PsiFile containingFile = element.getContainingFile();
      if (containingFile != null) {
        applicableFiles.add(containingFile);
      }
    }

    WriteCommandAction<Void> action = new WriteCommandAction<Void>(project,
                                                                   "Migrate Drawable to Bitmap",
                                                                   applicableFiles.toArray(new PsiFile[applicableFiles.size()])) {
      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        // Move each drawable bitmap from drawable-my-qualifiers to bitmap-my-qualifiers
        for (PsiFile bitmap : bitmaps) {
          VirtualFile file = bitmap.getVirtualFile();
          if (file == null) {
            continue;
          }
          VirtualFile parent = file.getParent();
          if (parent == null) { // shouldn't happen for bitmaps found in the resource repository
            continue;
          }

          if (file.getFileType() == StdFileTypes.XML && parent.getName().startsWith(FD_RES_VALUES)) {
            // Resource alias rather than an actual drawable XML file: update the type reference instead
            XmlFile xmlFile = (XmlFile)bitmap;
            XmlTag root = xmlFile.getRootTag();
            if (root != null) {
              for (XmlTag item : root.getSubTags()) {
                String name = item.getAttributeValue(ATTR_NAME);
                if (myUrl.name.equals(name)) {
                  if (ResourceType.DRAWABLE.getName().equals(item.getName())) {
                    item.setName(ResourceType.MIPMAP.getName());
                  } else if (ResourceType.DRAWABLE.getName().equals(item.getAttributeValue(ATTR_TYPE))) {
                    item.setAttribute(ATTR_TYPE, ResourceType.MIPMAP.getName());
                  }
                }
              }
            }
            continue; // Don't move the file
          }

          VirtualFile res = parent.getParent();
          if (res == null) { // shouldn't happen for bitmaps found in the resource repository
            continue;
          }

          FolderConfiguration configuration = FolderConfiguration.getConfigForFolder(parent.getName());
          if (configuration == null) {
            continue;
          }
          String targetFolderName = configuration.getFolderName(ResourceFolderType.MIPMAP);
          VirtualFile targetFolder = res.findChild(targetFolderName);
          if (targetFolder == null) {
            targetFolder = res.createChildDirectory(this, targetFolderName);
          }
          file.move(this, targetFolder);
        }

        // Update references
        for (PsiElement reference : references) {
          if (reference instanceof XmlAttributeValue) {
            // Convert @drawable/foo references to @mipmap/foo
            XmlAttributeValue value = (XmlAttributeValue)reference;
            XmlAttribute attribute = (XmlAttribute)value.getParent();
            attribute.setValue(ResourceUrl.create(ResourceType.MIPMAP, myUrl.name, false).toString());
          } else if (reference instanceof PsiReferenceExpression) {
            // Convert R.drawable.foo references to R.mipmap.foo
            PsiReferenceExpression inner = (PsiReferenceExpression)reference;
            PsiExpression qualifier = inner.getQualifierExpression();
            if (qualifier instanceof PsiReferenceExpression) {
              PsiReferenceExpression outer = (PsiReferenceExpression)qualifier;
              if (outer.getReferenceNameElement() instanceof PsiIdentifier) {
                PsiIdentifier identifier = (PsiIdentifier)outer.getReferenceNameElement();
                if (ResourceType.DRAWABLE.getName().equals(identifier.getText())) {
                  Project project = reference.getProject();
                  final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                  PsiIdentifier newIdentifier = elementFactory.createIdentifier(ResourceType.MIPMAP.getName());
                  identifier.replace(newIdentifier);
                }
              }
            }
          }
        }

      }
    };
    action.execute();
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return true;
  }

  @NotNull
  @Override
  public String getName() {
    return "Convert " + myUrl + " to @mipmap/" + myUrl.name;
  }
}
