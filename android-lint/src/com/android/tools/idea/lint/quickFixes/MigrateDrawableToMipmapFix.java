/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.quickFixes;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.FD_RES_VALUES;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.google.common.collect.Sets;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.psi.SearchUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Quickfix which migrates a drawable resource into a mipmap resource, moving bitmap and drawable XML
 * folders into mipmap folders (created if necessary) as well as updating resource references in XML
 * and Java files
 */
public class MigrateDrawableToMipmapFix extends DefaultLintQuickFix {
  private final ResourceUrl myUrl;

  public MigrateDrawableToMipmapFix(@NotNull ResourceUrl url) {
    super("Convert " + url + " to @mipmap/" + url.name);
    myUrl = url;
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    Project project = startElement.getProject();
    AndroidFacet facet = AndroidFacet.getInstance(startElement);
    if (facet == null) {
      return;
    }

    final List<PsiFile> bitmaps = new ArrayList<>();
    final Set<PsiElement> references = Sets.newHashSet();

    GlobalSearchScope useScope = GlobalSearchScope.projectScope(project);
    LocalResourceRepository projectResources = StudioResourceRepositoryManager.getProjectResources(facet);
    List<ResourceItem> resourceItems = projectResources.getResources(ResourceNamespace.TODO(), myUrl.type, myUrl.name);
    for (ResourceItem item : resourceItems) {
      PsiFile file = IdeResourcesUtil.getItemPsiFile(project, item);
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

    PsiField[] resourceFields = IdeResourcesUtil.findResourceFields(facet, ResourceType.DRAWABLE.getName(), myUrl.name, true);
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

    WriteCommandAction.writeCommandAction(project, applicableFiles.toArray(PsiFile.EMPTY_ARRAY)).withName("Migrate Drawable to Bitmap").run(() -> {
      try {
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

          if (file.getFileType() == XmlFileType.INSTANCE && parent.getName().startsWith(FD_RES_VALUES)) {
            // Resource alias rather than an actual drawable XML file: update the type reference instead
            XmlFile xmlFile = (XmlFile)bitmap;
            XmlTag root = xmlFile.getRootTag();
            if (root != null) {
              for (XmlTag item : root.getSubTags()) {
                String name = item.getAttributeValue(ATTR_NAME);
                if (myUrl.name.equals(name)) {
                  if (ResourceType.DRAWABLE.getName().equals(item.getName())) {
                    item.setName(ResourceType.MIPMAP.getName());
                  }
                  else if (ResourceType.DRAWABLE.getName().equals(item.getAttributeValue(ATTR_TYPE))) {
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
          }
          else if (reference instanceof PsiReferenceExpression) {
            // Convert R.drawable.foo references to R.mipmap.foo
            PsiReferenceExpression inner = (PsiReferenceExpression)reference;
            PsiExpression qualifier = inner.getQualifierExpression();
            if (qualifier instanceof PsiReferenceExpression) {
              PsiReferenceExpression outer = (PsiReferenceExpression)qualifier;
              if (outer.getReferenceNameElement() instanceof PsiIdentifier) {
                PsiIdentifier identifier = (PsiIdentifier)outer.getReferenceNameElement();
                if (ResourceType.DRAWABLE.getName().equals(identifier.getText())) {
                  final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(reference.getProject());
                  PsiIdentifier newIdentifier = elementFactory.createIdentifier(ResourceType.MIPMAP.getName());
                  identifier.replace(newIdentifier);
                }
              }
            }
          }
        }
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return true;
  }
}
