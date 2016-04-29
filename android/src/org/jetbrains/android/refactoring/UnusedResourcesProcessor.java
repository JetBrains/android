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
package org.jetbrains.android.refactoring;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.lint.checks.UnusedResourceDetector;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.TextFormat;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.inspections.lint.IntellijLintClient;
import org.jetbrains.android.inspections.lint.IntellijLintIssueRegistry;
import org.jetbrains.android.inspections.lint.IntellijLintRequest;
import org.jetbrains.android.inspections.lint.ProblemData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ATTR_ID;

public class UnusedResourcesProcessor extends BaseRefactoringProcessor {
  private final String myFilter;
  private final Module[] myModules;
  private PsiElement[] myElements = PsiElement.EMPTY_ARRAY;
  private boolean myIncludeIds;
  private String myCachedCommandName = null;

  public UnusedResourcesProcessor(@NotNull Project project, @NotNull Module[] modules, @Nullable String filter) {
    super(project, null);
    myModules = modules;
    myFilter = filter;
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new UnusedResourcesUsageViewDescriptor(myElements);
  }

  @Override
  @NotNull
  protected UsageInfo[] findUsages() {
    Map<Issue, Map<File, List<ProblemData>>> map = computeUnusedMap();
    List<PsiElement> elements = computeUnusedDeclarationElements(map);
    myElements = elements.toArray(new PsiElement[elements.size()]);
    UsageInfo[] result = new UsageInfo[myElements.length];
    for (int i = 0, n = myElements.length; i < n; i++) {
      PsiElement element = myElements[i];
      if (element instanceof PsiBinaryFile) {
        // The usage view doesn't handle binaries at all. Work around this (for example,
        // the UsageInfo class asserts in the constructor if the element doesn't have
        // a text range.)
        SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
        SmartPsiElementPointer<PsiElement> smartPointer = smartPointerManager.createSmartPsiElementPointer(element);
        SmartPsiFileRange smartFileRange =
          smartPointerManager.createSmartPsiFileRangePointer((PsiBinaryFile)element, TextRange.EMPTY_RANGE);
        result[i] = new UsageInfo(smartPointer, smartFileRange, false, false) {
          @Override
          public boolean isValid() {
            return true;
          }

          @Override
          @Nullable
          public Segment getSegment() {
            return null;
          }
        };
      } else {
        result[i] = new UsageInfo(element);
      }
    }
    return UsageViewUtil.removeDuplicatedUsages(result);
  }

  @NotNull
  private List<PsiElement> computeUnusedDeclarationElements(Map<Issue, Map<File, List<ProblemData>>> map) {
    List<PsiElement> elements = Lists.newArrayList();

    // Make sure lint didn't put extra issues into the map
    for (Issue issue : Lists.newArrayList(map.keySet())) {
      if (issue != UnusedResourceDetector.ISSUE && issue != UnusedResourceDetector.ISSUE_IDS) {
        map.remove(issue);
      }
    }
    ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiManager manager = PsiManager.getInstance(myProject);

    for (Issue issue : new Issue[]{UnusedResourceDetector.ISSUE, UnusedResourceDetector.ISSUE_IDS}) {
      Map<File, List<ProblemData>> fileListMap = map.get(issue);
      if (fileListMap != null && !fileListMap.isEmpty()) {
        Map<File, PsiFile> files = Maps.newHashMap();
        for (File file : fileListMap.keySet()) {
          VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
          if (virtualFile != null) {
            if (!virtualFile.isDirectory()) { // Gradle model errors currently don't have source positions
              PsiFile psiFile = manager.findFile(virtualFile);
              if (psiFile != null) {
                files.put(file, psiFile);
              }
            }
          }
        }

        if (!files.isEmpty()) {
          for (File file : files.keySet()) {
            PsiFile psiFile = files.get(file);
            if (psiFile == null) {
              // Ignore for now; currently this happens for build.gradle resValue definitions
              // where we only had the project directory as the location from the Gradle model
              continue;
            }

            if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, psiFile)) {
              continue;
            }

            List<ProblemData> problems = fileListMap.get(file);

            if (psiFile.getFileType().isBinary()) {
              // Delete the whole file
              if (matchesFilter(fileListMap, file)) {
                elements.add(psiFile);
              }
            }
            else {
              ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
              if (folderType != ResourceFolderType.VALUES) {
                // Make sure it's not an unused id declaration in a layout/menu/etc file that's
                // also being deleted as unused
                if (issue == UnusedResourceDetector.ISSUE_IDS) {
                  Map<File, List<ProblemData>> m = map.get(UnusedResourceDetector.ISSUE);
                  if (m != null && m.containsKey(file)) {
                    // Yes - skip
                    continue;
                  }

                  // Delete ranges within the file
                  addElementsInFile(elements, psiFile, problems);
                }
                else {
                  // Unused non-value resource file: Delete the whole file
                  if (matchesFilter(fileListMap, file)) {
                    elements.add(psiFile);
                  }
                }
              }
              else {
                addElementsInFile(elements, psiFile, problems);
              }
            }
          }
        }
      }
    }
    return elements;
  }

  private void addElementsInFile(List<PsiElement> elements, PsiFile psiFile, List<ProblemData> problems) {
    // Delete all the resources in the given file
    if (psiFile instanceof XmlFile) {
      List<Integer> starts = Lists.newArrayListWithCapacity(problems.size());
      for (ProblemData problem : problems) {
        if (matchesFilter(problem)) {
          starts.add(problem.getTextRange().getStartOffset());
        }
      }
      Collections.sort(starts, Collections.<Integer>reverseOrder());
      for (Integer offset : starts) {
        if (psiFile.isValid()) {
          XmlAttribute attribute = PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, XmlAttribute.class, false);
          PsiElement remove = attribute;
          if (attribute == null) {
            remove = PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, XmlTag.class, false);
          }
          else if (!ATTR_ID.equals(attribute.getLocalName())) {
            // If deleting a resource, delete the whole resource element, except for attribute android:id="" declarations
            // where we remove the attribute, not the tag
            remove = PsiTreeUtil.getParentOfType(attribute, XmlTag.class);
          }
          if (remove != null) {
            elements.add(remove);
          }
        }
      }
    }
  }

  @NotNull
  private Map<Issue, Map<File, List<ProblemData>>> computeUnusedMap() {
    Map<Issue, Map<File, List<ProblemData>>> map = Maps.newHashMap();
    List<Issue> issues = Lists.newArrayListWithExpectedSize(2);

    issues.add(UnusedResourceDetector.ISSUE);
    if (myIncludeIds) {
      issues.add(UnusedResourceDetector.ISSUE_IDS);
    }
    AnalysisScope scope = new AnalysisScope(myProject);

    boolean unusedWasEnabled = UnusedResourceDetector.ISSUE.isEnabledByDefault();
    boolean unusedIdsWasEnabled = UnusedResourceDetector.ISSUE_IDS.isEnabledByDefault();
    UnusedResourceDetector.ISSUE.setEnabledByDefault(true);
    UnusedResourceDetector.ISSUE_IDS.setEnabledByDefault(myIncludeIds);

    try {
      IntellijLintClient client = IntellijLintClient.forBatch(myProject, map, scope, issues);
      LintRequest request = new IntellijLintRequest(client, myProject, null, Arrays.asList(myModules), false);
      request.setScope(Scope.ALL);
      LintDriver lint = new LintDriver(new IntellijLintIssueRegistry(), client);
      lint.analyze(request);
    }
    finally {
      UnusedResourceDetector.ISSUE.setEnabledByDefault(unusedWasEnabled);
      UnusedResourceDetector.ISSUE_IDS.setEnabledByDefault(unusedIdsWasEnabled);
    }

    return map;
  }

  private boolean matchesFilter(@NotNull Map<File, List<ProblemData>> fileListMap, @NotNull File file) {
    if (myFilter != null) {
      List<ProblemData> problems = fileListMap.get(file);
      for (ProblemData problem : problems) {
        if (myFilter.equals(UnusedResourceDetector.getUnusedResource(problem.getMessage(), TextFormat.RAW))) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  private boolean matchesFilter(@NotNull ProblemData problem) {
    return myFilter == null || myFilter.equals(UnusedResourceDetector.getUnusedResource(problem.getMessage(), TextFormat.RAW));
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    return true;
  }

  public PsiElement[] getElements() {
    return myElements;
  }

  @Override
  protected void refreshElements(@NotNull PsiElement[] elements) {
    System.arraycopy(elements, 0, myElements, 0, elements.length);
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    final RefactoringEventData beforeData = new RefactoringEventData();
    beforeData.addElements(myElements);
    return beforeData;
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.unused.resources";
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    try {
      for (PsiElement element : myElements) {
        if (element != null && element.isValid()) {
          element.delete();
        }
      }
    }
    catch (IncorrectOperationException e) {
      RefactoringUIUtil.processIncorrectOperation(myProject, e);
    }
  }

  private String calcCommandName() {
    return "Deleting " + RefactoringUIUtil.calculatePsiElementDescriptionList(myElements);
  }

  @Override
  protected String getCommandName() {
    if (myCachedCommandName == null) {
      myCachedCommandName = calcCommandName();
    }
    return myCachedCommandName;
  }

  @Override
  protected boolean skipNonCodeUsages() {
    return true;
  }

  public void setIncludeIds(boolean includeIds) {
    myIncludeIds = includeIds;
  }
}
