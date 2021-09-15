/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.refactoring.modularize;

import static com.android.tools.idea.projectsystem.SourceProvidersKt.containsFile;
import static com.android.tools.idea.projectsystem.SourceProvidersKt.getManifestFiles;
import static com.intellij.openapi.actionSystem.LangDataKeys.TARGET_MODULE;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.facet.SourceProviderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidModularizeHandler implements RefactoringActionHandler {

  private static final Logger LOGGER = Logger.getInstance(AndroidModularizeHandler.class);
  private static final int RESOURCE_SET_INITIAL_SIZE = 100;

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invoke(project, BaseRefactoringAction.getPsiElementArray(dataContext), dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    AndroidModularizeProcessor processor = createProcessor(project, elements);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      Module targetModule = TARGET_MODULE.getData(dataContext);
      if (targetModule != null) {
        processor.setTargetModule(targetModule);
      }
      processor.run();
    }
    else {
      List<Module> suitableModules = new ArrayList<>();
      // Only offer modules that have an Android facet, otherwise we don't know where to move resources.
      for (AndroidFacet facet : ProjectSystemUtil.getAndroidFacets(project)) {
        if (!ResourceFolderManager.getInstance(facet).getFolders().isEmpty()) {
          suitableModules.add(ModuleSystemUtil.getMainModule(facet.getModule()));
        }
      }
      for (PsiElement root : elements) {
        Module sourceModule = ModuleUtilCore.findModuleForPsiElement(root);
        if (sourceModule != null) {
          suitableModules.remove(sourceModule);
        }
      }

      AndroidModularizeDialog dialog = new AndroidModularizeDialog(project, suitableModules, processor);
      dialog.show();
    }
  }

  @VisibleForTesting
  AndroidModularizeProcessor createProcessor(@NotNull Project project, @NotNull PsiElement[] elements) {
    CodeAndResourcesReferenceCollector scanner = new CodeAndResourcesReferenceCollector(project);

    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ApplicationManager.getApplication().runReadAction(
        () -> scanner.accumulate(elements)), "Computing References", false, project);

    return new AndroidModularizeProcessor(project,
                                          elements,
                                          scanner.getClassReferences(),
                                          scanner.getResourceReferences(),
                                          scanner.getManifestReferences(),
                                          scanner.getReferenceGraph());
  }


  private static class CodeAndResourcesReferenceCollector {
    private final Project myProject;

    private final Set<PsiClass> myClassRefSet = new LinkedHashSet<>();
    private final Set<ResourceItem> myResourceRefSet = new LinkedHashSet<>(RESOURCE_SET_INITIAL_SIZE);
    private final Set<PsiElement> myManifestRefSet = new HashSet<>();
    private final Queue<PsiElement> myVisitQueue = new ArrayDeque<>();
    private final AndroidCodeAndResourcesGraph.Builder myGraphBuilder = new AndroidCodeAndResourcesGraph.Builder();

    public CodeAndResourcesReferenceCollector(@NotNull Project project) {
      myProject = project;
    }

    public void accumulate(PsiElement... roots) {
      myVisitQueue.clear();
      for (PsiElement element : roots) {
        PsiClass ownerClass =
          (element instanceof PsiClass) ? (PsiClass)element : PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (ownerClass != null && myClassRefSet.add(ownerClass)) {
          myVisitQueue.add(ownerClass);
          myGraphBuilder.addRoot(ownerClass);
        }
      }

      Set<AndroidFacet> facetSet = new HashSet<>();
      Set<VirtualFile> fileScope = new HashSet<>();
      Set<PsiElement> elementScope = new HashSet<>();

      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.pushState();
        indicator.setIndeterminate(false);
      }
      try {
        int numVisited = 0;

        while (!myVisitQueue.isEmpty()) {
          PsiElement element = myVisitQueue.poll();
          numVisited++;

          final AndroidFacet facet = AndroidFacet.getInstance(element);
          if (facet == null) {
            continue;
          }
          facetSet.add(facet);

          if (indicator != null) {
            indicator.setText(
              String.format(Locale.US, "Scanning definition %1$d of %2$d", numVisited, numVisited + myVisitQueue.size()));
            indicator.setFraction((double)numVisited / (numVisited + myVisitQueue.size()));
          }

          if (element instanceof PsiClass) {
            element.accept(new JavaReferenceVisitor(facet, element));

            // Check for manifest entries referencing this class (this applies to activities, content providers, etc).
            GlobalSearchScope manifestScope = GlobalSearchScope.filesScope(myProject, getManifestFiles(facet));

            ReferencesSearch.search(element, manifestScope).forEach(reference -> {
              PsiElement tag = reference.getElement();
              tag = PsiTreeUtil.getParentOfType(tag, XmlTag.class);

              if (tag != null) {
                if (myManifestRefSet.add(tag)) {
                  // Scan the tag because we might have references to other resources.
                  myVisitQueue.offer(tag);
                }

                myGraphBuilder.markReference(element, tag);
              }
            });

            // Scope building: we try to be as precise as possible when computing the enclosing scope. For example we include the (selected)
            // activity tags in a manifest file but not the entire file, which may contain references to resources we would otherwise move.
            if (((PsiClass)element).getContainingClass() == null) {
              fileScope.add(element.getContainingFile().getVirtualFile());
            }
            else {
              elementScope.add(element);
            }
          }
          else {
            if (element instanceof PsiFile) {
              fileScope.add(((PsiFile)element).getVirtualFile());
            } else {
              elementScope.add(element);
            }

            element.accept(new XmlResourceReferenceVisitor(facet, element));
          }
        }

        GlobalSearchScope globalSearchScope = GlobalSearchScope.EMPTY_SCOPE;
        for (AndroidFacet facet : facetSet) {
          globalSearchScope = globalSearchScope.union(facet.getModule().getModuleScope(false));
        }

        GlobalSearchScope visitedScope = GlobalSearchScope.filesScope(myProject, fileScope)
          .union(new LocalSearchScope(elementScope.toArray(PsiElement.EMPTY_ARRAY)));
        globalSearchScope = globalSearchScope.intersectWith(GlobalSearchScope.notScope(visitedScope));

        for (PsiClass clazz : myClassRefSet) {
          ReferencesSearch.search(clazz, globalSearchScope).forEach(reference -> {
            myGraphBuilder.markReferencedOutsideScope(clazz);
            LOGGER.debug(clazz + " referenced from " + reference.getElement().getContainingFile());
          });
        }

        Set<ResourceReference> seenResources = new HashSet<>(myResourceRefSet.size());
        for (ResourceItem item : myResourceRefSet) {
          ResourceReference ref = item.getReferenceToSelf();
          if (seenResources.add(ref)) {
            PsiField[] fields;
            PsiElement elm = getResourceDefinition(item);
            if (elm instanceof PsiFile) {
              fields = IdeResourcesUtil.findResourceFieldsForFileResource((PsiFile)elm, true);
            }
            else if (elm instanceof XmlTag) {
              fields = IdeResourcesUtil.findResourceFieldsForValueResource((XmlTag)elm, true);
            }
            else {
              continue;
            }

            for (PsiField pf : fields) {
              ReferencesSearch.search(pf, globalSearchScope).forEach(reference -> {
                myGraphBuilder.markReferencedOutsideScope(elm);
                LOGGER.debug(item + " referenced from " + reference.getElement().getContainingFile());
              });
            }
          }
        }
      }
      finally {
        if (indicator != null) {
          indicator.popState();
        }
      }
    }

    @NotNull
    public Set<PsiClass> getClassReferences() {
      return myClassRefSet;
    }

    @NotNull
    public Set<ResourceItem> getResourceReferences() {
      return myResourceRefSet;
    }

    @NotNull
    public Set<PsiElement> getManifestReferences() {
      return myManifestRefSet;
    }

    @NotNull
    public AndroidCodeAndResourcesGraph getReferenceGraph() {
      return myGraphBuilder.build();
    }

    @Nullable
    private PsiElement getResourceDefinition(ResourceItem resource) {
      PsiFile psiFile = IdeResourcesUtil.getItemPsiFile(myProject, resource);
      if (psiFile == null) { // psiFile could be null if this is dynamically defined, so nothing to visit...
        return null;
      }

      if (IdeResourcesUtil.getFolderType(psiFile) == ResourceFolderType.VALUES) {
        // This is just a value, so we'll just scan its corresponding XmlTag
        return IdeResourcesUtil.getItemTag(myProject, resource);
      }
      return psiFile;
    }

    private class XmlResourceReferenceVisitor extends XmlRecursiveElementWalkingVisitor {
      private final AndroidFacet myFacet;
      private final PsiElement mySource;
      private final LocalResourceRepository myResourceRepository;

      XmlResourceReferenceVisitor(@NotNull AndroidFacet facet, @NotNull PsiElement source) {
        myFacet = facet;
        mySource = source;
        myResourceRepository = ResourceRepositoryManager.getModuleResources(facet);
      }

      @Override
      public void visitXmlAttributeValue(XmlAttributeValue element) {
        processPotentialReference(element.getValue());
      }

      @Override
      public void visitXmlToken(XmlToken token) {
        processPotentialReference(token.getText());
      }

      private void processPotentialReference(String text) {
        ResourceUrl url = ResourceUrl.parse(text);
        if (url != null) {
          if (!url.isFramework() && !url.isCreate() && url.type != ResourceType.ID) {
            List<ResourceItem> matches = myResourceRepository.getResources(ResourceNamespace.TODO(), url.type, url.name);
            for (ResourceItem match : matches) {
              PsiElement target = getResourceDefinition(match);
              if (target != null) {
                if (myResourceRefSet.add(match)) {
                  myVisitQueue.offer(target);
                }
                myGraphBuilder.markReference(mySource, target);
              }
            }
          }
        } else {
          // Perhaps this is a reference to a Java class
          PsiClass target = JavaPsiFacade.getInstance(myProject).findClass(
            text, myFacet.getModule().getModuleScope(false));
          if (target != null) {
            if (myClassRefSet.add(target)) {
              myVisitQueue.offer(target);
            }
            myGraphBuilder.markReference(mySource, target);
          }
        }
      }
    }

    private class JavaReferenceVisitor extends JavaRecursiveElementWalkingVisitor {
      private final AndroidFacet myFacet;
      private final PsiElement mySource;
      private final LocalResourceRepository myResourceRepository;

      JavaReferenceVisitor(@NotNull AndroidFacet facet, @NotNull PsiElement source) {
        myFacet = facet;
        mySource = source;
        myResourceRepository = ResourceRepositoryManager.getModuleResources(facet);
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        PsiElement element = expression.resolve();
        if (element instanceof PsiField) {
          AndroidPsiUtils.ResourceReferenceType referenceType = AndroidPsiUtils.getResourceReferenceType(expression);

          if (referenceType == AndroidPsiUtils.ResourceReferenceType.APP) {
            // This is a resource we might be able to move
            ResourceType type = AndroidPsiUtils.getResourceType(expression);
            if (type != null && type != ResourceType.ID) {
              String name = AndroidPsiUtils.getResourceName(expression);

              List<ResourceItem> matches = myResourceRepository.getResources(ResourceNamespace.TODO(), type, name);
              for (ResourceItem match : matches) {
                PsiElement target = getResourceDefinition(match);
                if (target != null) {
                  if (myResourceRefSet.add(match)) {
                    myVisitQueue.offer(target);
                  }
                  myGraphBuilder.markReference(mySource, target);
                }
              }
            }
            return; // We had a resource match, no need to keep visiting children.
          }
        }
        super.visitReferenceExpression(expression);
      }

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        PsiElement target = reference.advancedResolve(false).getElement();
        if (target instanceof PsiClass) {
          if (!(target instanceof PsiTypeParameter) && !(target instanceof SyntheticElement)) {
            VirtualFile source = target.getContainingFile().getVirtualFile();
            if (containsFile(SourceProviderManager.getInstance(myFacet).getSources(), source)) {
              // This is a local source file, therefore a candidate to be moved
              if (myClassRefSet.add((PsiClass)target)) {
                myVisitQueue.add(target);
              }
              if (target != mySource) { // Don't add self-references
                myGraphBuilder.markReference(mySource, target);
              }
            }
          }
        }
      }
    }
  }
}
