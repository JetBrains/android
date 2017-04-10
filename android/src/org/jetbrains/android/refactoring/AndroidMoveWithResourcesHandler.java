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
package org.jetbrains.android.refactoring;

import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ProjectResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.actionSystem.LangDataKeys.TARGET_MODULE;

public class AndroidMoveWithResourcesHandler implements RefactoringActionHandler {

  private static final int RESOURCE_SET_INITIAL_SIZE = 100;

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invoke(project, BaseRefactoringAction.getPsiElementArray(dataContext), dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    ResourceReferenceScanner scanner = new ResourceReferenceScanner(project, elements);
    scanner.compute();

    AndroidMoveWithResourcesProcessor processor =
      new AndroidMoveWithResourcesProcessor(project, elements, scanner.getResourceReferences(), scanner.getManifestReferences());

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      Module targetModule = TARGET_MODULE.getData(dataContext);
      if (targetModule != null) {
        processor.setTargetModule(targetModule);
      }
      processor.run();
    }
    else {
      AndroidMoveWithResourcesDialog dialog = new AndroidMoveWithResourcesDialog(project, processor);
      dialog.show();
    }
  }


  private static class ResourceReferenceScanner {
    private final Project myProject;
    private final PsiElement[] myRoots;

    private Set<ResourceItem> myResourceRefSet;
    private Set<PsiElement> myManifestRefSet;

    public ResourceReferenceScanner(@NotNull Project project, @NotNull PsiElement[] elements) {
      myProject = project;
      myRoots = elements;
    }

    public void compute() {
      myResourceRefSet = new LinkedHashSet<>(RESOURCE_SET_INITIAL_SIZE);
      myManifestRefSet = new HashSet<>();

      Queue<ResourceItem> toVisit = new ArrayDeque<>();

      for (PsiElement element : myRoots) {
        final AndroidFacet facet = AndroidFacet.getInstance(element);
        if (facet == null) {
          continue;
        }

        ProjectResourceRepository resourceRepository = ProjectResourceRepository.getOrCreateInstance(facet);
        element.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            PsiElement element = expression.resolve();
            if (element instanceof PsiField) {
              AndroidPsiUtils.ResourceReferenceType referenceType = AndroidPsiUtils.getResourceReferenceType(expression);

              if (referenceType == AndroidPsiUtils.ResourceReferenceType.APP) {
                // This is a resource we might be able to move
                ResourceType type = AndroidPsiUtils.getResourceType(expression);
                if (type != null) {
                  String name = AndroidPsiUtils.getResourceName(expression);

                  List<ResourceItem> matches = resourceRepository.getResourceItem(type, name);
                  if (matches != null) {
                    for (ResourceItem match : matches) {
                      if (myResourceRefSet.add(match)) {
                        toVisit.offer(match);
                      }
                    }
                  }
                }
              }
            }
          }

          @Override
          public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
            // TODO: Collect Java references as well. We move everything reachable in the transitive closure.
            PsiElement target = reference.advancedResolve(false).getElement();
          }
        });

        // Check for manifest entries referencing this class (activities, content providers, etc).
        GlobalSearchScope manifestScope = GlobalSearchScope.filesScope(myProject, IdeaSourceProvider.getManifestFiles(facet));

        ReferencesSearch.search(element, manifestScope).forEach(reference -> {
          PsiElement tag = reference.getElement();
          tag = PsiTreeUtil.getParentOfType(tag, XmlTag.class);

          if (tag != null) {
            myManifestRefSet.add(tag);
            // Scan the tag because we might have references to other resources.
            tag.accept(new XmlResourceReferenceVisitor(resourceRepository, myResourceRefSet, toVisit));
          }
        });
      }

      while (!toVisit.isEmpty()) {
        ResourceItem resource = toVisit.poll();

        PsiFile psiFile = LocalResourceRepository.getItemPsiFile(myProject, resource);
        if (psiFile == null) {
          continue;
        }
        AndroidFacet facet = AndroidFacet.getInstance(psiFile);
        if (facet == null) {
          continue;
        }
        ProjectResourceRepository resourceRepository = ProjectResourceRepository.getOrCreateInstance(facet);

        PsiElement definitionToScan = psiFile;
        if (ResourceHelper.getFolderType(psiFile) == ResourceFolderType.VALUES) {
          // This is just a value, so we'll just scan its corresponding XmlTag
          definitionToScan = LocalResourceRepository.getItemTag(myProject, resource);
          if (definitionToScan == null) {
            continue;
          }
        }

        definitionToScan.accept(new XmlResourceReferenceVisitor(resourceRepository, myResourceRefSet, toVisit));
      }
    }

    @NotNull
    public Set<ResourceItem> getResourceReferences() {
      return myResourceRefSet;
    }

    @NotNull
    public Set<PsiElement> getManifestReferences() {
      return myManifestRefSet;
    }
  }

  private static class XmlResourceReferenceVisitor extends XmlRecursiveElementWalkingVisitor {

    private final ProjectResourceRepository myResourceRepository;
    private final Set<ResourceItem> mySeenResourceItems;
    private final Queue<ResourceItem> myToVisitLater;

    XmlResourceReferenceVisitor(ProjectResourceRepository resourceRepository, Set<ResourceItem> seenResourceItems, Queue<ResourceItem> toVisitLater) {
      myResourceRepository = resourceRepository;
      mySeenResourceItems = seenResourceItems;
      myToVisitLater = toVisitLater;
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
      if (url != null && !url.framework && !url.create && url.type != ResourceType.ID) {
        List<ResourceItem> matches = myResourceRepository.getResourceItem(url.type, url.name);
        if (matches != null) {
          for (ResourceItem match : matches) {
            if (mySeenResourceItems.add(match)) {
              myToVisitLater.offer(match);
            }
          }
        }
      }
    }
  }
}
