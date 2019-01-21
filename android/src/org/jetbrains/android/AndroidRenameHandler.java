/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android;

import com.android.ide.common.resources.ValueResourceNameValidator;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.res.ResourceGroupVirtualDirectory;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.android.augment.AndroidLightField;
import org.jetbrains.android.dom.converters.AndroidResourceReference;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidRenameHandler implements RenameHandler, TitledHandler {
  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);

    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element instanceof SchemaPrefix) {
      return false; // Leave renaming of namespace prefixes to the default XML handler.
    }

    if (element instanceof PsiDirectory) {
      VirtualFile virtualFile = ((PsiDirectory)element).getVirtualFile();
      if (virtualFile instanceof ResourceGroupVirtualDirectory) {
        return true;
      }
    }

    if (editor == null) {
      return false;
    }

    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) {
      return false;
    }

    if (AndroidUsagesTargetProvider.findValueResourceTagInContext(editor, file, true) != null) {
      return true;
    }

    if (getResourceReferenceTarget(editor) != null) {
      return true;
    }

    Project project = CommonDataKeys.PROJECT.getData(dataContext);

    if (project == null) {
      return false;
    }
    return isPackageAttributeInManifest(project, element);
  }

  /**
   * Determine if this editor's caret is currently on a reference to an Android resource and if so return the root definition of that
   * resource.
   */
  @Nullable
  private static PsiElement getResourceReferenceTarget(@NotNull Editor editor) {
    PsiReference reference = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());
    if (!(reference instanceof AndroidResourceReference)) {
      return null;
    }

    Collection<PsiElement> elements = TargetElementUtil.getInstance().getTargetCandidates(reference);
    if (elements.isEmpty()) {
      return null;
    }

    ArrayList<PsiElement> elementList = new ArrayList<>(elements);
    Collections.sort(elementList, AndroidResourceUtil.RESOURCE_ELEMENT_COMPARATOR);
    return elementList.get(0);
  }

  @Override
  public boolean isRenaming(@NotNull DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (elements.length == 1 && elements[0] instanceof PsiDirectory) {
      VirtualFile virtualFile = ((PsiDirectory)elements[0]).getVirtualFile();
      if (!(virtualFile instanceof ResourceGroupVirtualDirectory)) {
        return;
      }
      performResourceGroupRenaming((ResourceGroupVirtualDirectory)virtualFile, project, dataContext);
      return;
    }

    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return;
    }

    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) {
      return;
    }

    invoke(project, editor, file, dataContext);
  }

  private static void performResourceGroupRenaming(@NotNull ResourceGroupVirtualDirectory resourceGroup,
                                                   @NotNull Project project,
                                                   @NotNull DataContext dataContext) {
    VirtualFile[] resourceFiles = resourceGroup.getResourceFiles();
    if (resourceFiles.length == 0) {
      return;
    }
    PsiFile firstFile = PsiManager.getInstance(project).findFile(resourceFiles[0]);
    final AndroidFacet facet = firstFile != null ? AndroidFacet.getInstance(firstFile) : null;
    if (facet != null) {
      // Treat the tree node rename as if the user renamed the R field instead.
      performResourceFieldRenaming(AndroidResourceUtil.findResourceFieldsForFileResource(firstFile, true),
                                   project, dataContext, null);
    }
  }

  @Override
  public void invoke(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile file, @NotNull DataContext dataContext) {
    if (file == null || editor == null) {
      return;
    }
    XmlTag tag = AndroidUsagesTargetProvider.findValueResourceTagInContext(editor, file, true);

    if (tag != null) {
      // See if you've actually pointed at an XML value inside the value definition, e.g.
      //   <string name="my_alias">@string/my_string</string>
      // If the caret is on my_string, you expect to rename my_string, not my_alias (the XmlTag).
      ResourceUrl url = findResourceReferenceUnderCaret(editor, file);
      if (url != null && !url.isFramework()) {
        performResourceReferenceRenaming(project, editor, dataContext, file, url);
      }
      else {
        performValueResourceRenaming(project, editor, dataContext, tag);
      }
    }
    else {
      PsiElement element = getResourceReferenceTarget(editor);
      if (element != null) {
        performResourceReferenceRenaming(project, editor, dataContext, element);
      }
      else {
        performApplicationPackageRenaming(project, editor, dataContext);
      }
    }
  }

  private static void performValueResourceRenaming(@NotNull Project project,
                                                   @NotNull Editor editor,
                                                   @NotNull DataContext dataContext,
                                                   @NotNull XmlTag tag) {
    XmlAttribute nameAttribute = tag.getAttribute("name");
    if (nameAttribute == null) {
      return;
    }

    XmlAttributeValue attributeValue = nameAttribute.getValueElement();
    if (attributeValue == null) {
      return;
    }
    RenameDialog.showRenameDialog(dataContext,
                                  new ResourceRenameDialog(project, new ValueResourceElementWrapper(attributeValue), null, editor));
  }

  private static void performResourceReferenceRenaming(Project project,
                                                       Editor editor,
                                                       DataContext dataContext,
                                                       PsiFile file,
                                                       ResourceUrl url) {
    assert !url.isFramework();

    final AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet != null) {
      // Treat the resource reference as if the user renamed the R field instead.
      performResourceFieldRenaming(AndroidResourceUtil.findResourceFields(facet, url.type.getName(), url.name, false),
                                   project, dataContext, editor);
    }
  }

  private static void performResourceFieldRenaming(@NotNull PsiField[] resourceFields,
                                                   @NotNull Project project,
                                                   @NotNull DataContext dataContext,
                                                   @Nullable Editor editor) {
    if (resourceFields.length == 1) {
      PsiElement element = resourceFields[0];
      if (StudioFlags.IN_MEMORY_R_CLASSES.get() && element instanceof AndroidLightField) {
        element = new ResourceFieldElementWrapper((AndroidLightField)element);
      }
      RenameDialog.showRenameDialog(dataContext, new ResourceRenameDialog(project, element, null, editor));
    }
  }

  private static void performResourceReferenceRenaming(@NotNull Project project,
                                                       @NotNull Editor editor,
                                                       @NotNull DataContext dataContext,
                                                       @NotNull PsiElement element) {
    RenameDialog.showRenameDialog(dataContext, new ResourceRenameDialog(project, element, null, editor));
  }

  @Nullable
  private static ResourceUrl findResourceReferenceUnderCaret(@NotNull Editor editor, @NotNull PsiFile file) {
    if (!(file instanceof XmlFile)) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return null;
    }

    if (!AndroidResourceUtil.isInResourceSubdirectory(file, ResourceFolderType.VALUES.getName())) {
      return null;
    }

    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) {
      return null;
    }

    if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
      XmlText text = PsiTreeUtil.getParentOfType(element, XmlText.class);
      if (text != null) {
        return ResourceUrl.parse(text.getText().trim());
      }
    }
    return null;
  }

  @Override
  public String getActionTitle() {
    return "Rename Android value resource";
  }

  static boolean isPackageAttributeInManifest(@NotNull Project project, @Nullable PsiElement element) {
    if (element == null) {
      return false;
    }
    PsiFile psiFile = element.getContainingFile();

    if (!(psiFile instanceof XmlFile)) {
      return false;
    }
    AndroidFacet facet = AndroidFacet.getInstance(psiFile);

    if (facet == null) {
      return false;
    }
    VirtualFile vFile = psiFile.getVirtualFile();

    if (vFile == null || !vFile.equals(AndroidRootUtil.getPrimaryManifestFile(facet))) {
      return false;
    }
    if (!(element instanceof XmlAttributeValue)) {
      return false;
    }
    PsiElement parent = element.getParent();

    if (!(parent instanceof XmlAttribute)) {
      return false;
    }
    GenericAttributeValue attrValue = DomManager.getDomManager(project).getDomElement((XmlAttribute)parent);

    if (attrValue == null) {
      return false;
    }
    DomElement parentDomElement = attrValue.getParent();
    return parentDomElement instanceof Manifest && attrValue.equals(((Manifest)parentDomElement).getPackage());
  }

  private static void performApplicationPackageRenaming(@NotNull Project project,
                                                        @NotNull Editor editor,
                                                        @NotNull DataContext context) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);

    if (!(element instanceof XmlAttributeValue)) {
      return;
    }
    Module module = ModuleUtilCore.findModuleForPsiElement(element);

    if (module == null) {
      return;
    }
    RenameDialog.showRenameDialog(context, new RenameDialog(project, element, null, editor) {
      @Override
      @NotNull
      protected String getLabelText() {
        return "Rename Android application package of module '" + module.getName() + "' to:";
      }

      @Override
      protected void canRun() throws ConfigurationException {
        String name = getNewName();

        if (name.isEmpty()) {
          throw new ConfigurationException(AndroidBundle.message("specify.package.name.error"));
        }
        if (!AndroidUtils.isValidAndroidPackageName(name)) {
          throw new ConfigurationException(AndroidBundle.message("not.valid.package.name.error", name));
        }
        if (!AndroidCommonUtils.contains2Identifiers(name)) {
          throw new ConfigurationException(AndroidBundle.message("package.name.must.contain.2.ids.error"));
        }
        super.canRun();
      }
    });
  }

  private static class ResourceRenameDialog extends RenameDialog {
    ResourceRenameDialog(@NotNull Project project,
                         @NotNull PsiElement psiElement,
                         @Nullable PsiElement nameSuggestionContext,
                         @Nullable Editor editor) {
      super(project, psiElement, nameSuggestionContext, editor);
    }

    @Override
    protected void canRun() throws ConfigurationException {
      String name = getNewName();
      String errorText = ValueResourceNameValidator.getErrorText(name, null);
      if (errorText != null ) {
        throw new ConfigurationException(errorText);
      }
    }
  }
}
