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
package org.jetbrains.android.dom.converters;

import com.android.tools.idea.projectsystem.IdeaSourceProvider;
import com.android.tools.idea.projectsystem.SourceProviderManager;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A quick fix that creates non-existing class, used, e.g., for invalid android:name references in AndroidManifest.xml file
 */
public class CreateMissingClassQuickFix implements LocalQuickFix {
  private final SmartPsiElementPointer<PsiPackage> myPackage;

  @NotNull private final String myClassName;
  @NotNull private final Module myModule;
  private final String myBaseClassFqcn;

  /**
   * @param aPackage      destination package of a new class
   * @param className     created class name
   * @param module        application module where class should be created
   * @param baseClassFqcn if provided, created class would inherit this one
   */
  CreateMissingClassQuickFix(@NotNull PsiPackage aPackage,
                             @NotNull String className,
                             @NotNull Module module,
                             @Nullable/*if created class shouldn't extend anything*/ String baseClassFqcn) {
    myPackage = SmartPointerManager.getInstance(aPackage.getProject()).createSmartPsiElementPointer(aPackage);
    myClassName = className;
    myModule = module;
    myBaseClassFqcn = baseClassFqcn;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return String.format("Create class '%s'", myClassName);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Create class";
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiPackage aPackage = myPackage.getElement();
    if (aPackage == null) {
      return;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    if (facet == null) {
      return;
    }

    IdeaSourceProvider sources = SourceProviderManager.getInstance(facet).getSources();
    final Iterable<VirtualFile> sourceDirs = Iterables.concat(sources.getJavaDirectories(), sources.getKotlinDirectories());
    final PsiDirectory[] directories = aPackage.getDirectories();
    final List<PsiDirectory> filteredDirectories = Lists.newArrayListWithExpectedSize(directories.length);
    for (PsiDirectory directory : directories) {
      for (VirtualFile file : sourceDirs) {
        if (VfsUtilCore.isAncestor(file, directory.getVirtualFile(), true)) {
          filteredDirectories.add(directory);
          break;
        }
      }
    }

    final PsiDirectory directory;
    switch (filteredDirectories.size()) {
      case 0:
        directory = null;
        break;
      case 1:
        directory = filteredDirectories.get(0);
        break;
      default:
        // There are several directories, present a dialog window for a user to choose a particular destination directory
        final PsiDirectory[] array = filteredDirectories.toArray(PsiDirectory.EMPTY_ARRAY);
        directory = DirectoryChooserUtil.selectDirectory(aPackage.getProject(), array, filteredDirectories.get(0), "");
    }

    if (directory == null) {
      return;
    }

    PsiClass aClass =
    WriteCommandAction.writeCommandAction(project).compute(() -> {
      // Create a new class
      final PsiClass psiClass = JavaDirectoryService.getInstance().createClass(directory, myClassName);

      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

      // Add a base class to "extends" list
      final PsiReferenceList list = psiClass.getExtendsList();
      if (list != null && myBaseClassFqcn != null) {
          final PsiClass parentClass = facade.findClass(myBaseClassFqcn, GlobalSearchScope.allScope(project));
          if (parentClass != null) {
            list.add(facade.getElementFactory().createClassReferenceElement(parentClass));
          }
        }

      // Add a "public" modifier, which is absent by default. Required because classes references in AndroidManifest
      // have to point to public classes.
      final PsiModifierList modifierList = psiClass.getModifierList();
      if (modifierList != null) {
          modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
      }

      return psiClass;
    });

    OpenFileDescriptor fileDescriptor = new OpenFileDescriptor(project, aClass.getContainingFile().getVirtualFile());
    FileEditorManager.getInstance(project).openEditor(fileDescriptor, true);
  }
}
