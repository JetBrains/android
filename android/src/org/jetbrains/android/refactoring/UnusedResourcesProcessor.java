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

import static com.android.SdkConstants.ATTR_ID;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.FlavorTypeModel.ResValue;
import com.android.tools.idea.lint.AndroidLintIdeIssueRegistry;
import com.android.tools.idea.lint.common.LintBatchResult;
import com.android.tools.idea.lint.common.LintIdeClient;
import com.android.tools.idea.lint.common.LintIdeRequest;
import com.android.tools.idea.lint.common.LintIdeSupport;
import com.android.tools.idea.lint.common.LintProblemData;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.lint.checks.UnusedResourceDetector;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
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
import java.util.stream.Collectors;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.*;

import static com.android.SdkConstants.EXT_GRADLE;
import static com.android.SdkConstants.EXT_GRADLE_KTS;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

public class UnusedResourcesProcessor extends BaseRefactoringProcessor {
  private final String myFilter;
  private final Module[] myModules;
  private PsiElement[] myElements = PsiElement.EMPTY_ARRAY;
  private boolean myIncludeIds;
  private String myCachedCommandName = null;
  private Map<PsiElement, GradleBuildModel> myBuildModelMap;

  public UnusedResourcesProcessor(@NotNull Project project, @NotNull Module[] modules, @Nullable String filter) {
    super(project, null);
    myModules = modules;
    myFilter = filter;
    myBuildModelMap = new HashMap<>();
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new UnusedResourcesUsageViewDescriptor(myElements);
  }

  @Override
  @NotNull
  protected UsageInfo[] findUsages() {
    Map<Issue, Map<File, List<LintProblemData>>> map = computeUnusedMap();
    List<PsiElement> elements = computeUnusedDeclarationElements(map);
    myElements = elements.toArray(PsiElement.EMPTY_ARRAY);
    UsageInfo[] result = new UsageInfo[myElements.length];
    for (int i = 0, n = myElements.length; i < n; i++) {
      result[i] = new UsageInfo(myElements[i]);
    }
    return UsageViewUtil.removeDuplicatedUsages(result);
  }

  @NotNull
  private List<PsiElement> computeUnusedDeclarationElements(Map<Issue, Map<File, List<LintProblemData>>> map) {
    final List<PsiElement> elements = Lists.newArrayList();

    // Make sure lint didn't put extra issues into the map
    for (Issue issue : Lists.newArrayList(map.keySet())) {
      if (issue != UnusedResourceDetector.ISSUE && issue != UnusedResourceDetector.ISSUE_IDS) {
        map.remove(issue);
      }
    }
    ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiManager manager = PsiManager.getInstance(myProject);

    for (Issue issue : new Issue[]{UnusedResourceDetector.ISSUE, UnusedResourceDetector.ISSUE_IDS}) {
      Map<File, List<LintProblemData>> fileListMap = map.get(issue);
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

            List<LintProblemData> problems = fileListMap.get(file);

            if (psiFile.getFileType().isBinary()) {
              // Delete the whole file
              if (matchesFilter(fileListMap, file)) {
                elements.add(psiFile);
              }
            }
            else {
              ResourceFolderType folderType = IdeResourcesUtil.getFolderType(psiFile);
              if (folderType == null) {
                // Not found in a resource folder. This happens for example for
                // matches in build.gradle.
                //
                // Attempt to find the resource in the build file. If we can't,
                // we'll ignore this resource (it would be dangerous to just delete the
                // file; see for example http://b.android.com/220069.)
                if ((psiFile instanceof GroovyFile || psiFile instanceof KtFile) &&
                    (psiFile.getName().endsWith(EXT_GRADLE) || psiFile.getName().endsWith(EXT_GRADLE_KTS))) {
                  GradleBuildModel gradleBuildModel = GradleBuildModel.parseBuildFile(psiFile.getVirtualFile(), myProject);
                  // Get all the resValue declared within the android block.
                  AndroidModel androidElement = gradleBuildModel.android();
                  List<ResValue> resValues = androidElement.defaultConfig().resValues();
                  resValues.addAll(
                    androidElement.productFlavors().stream().flatMap(e -> e.resValues().stream()).collect(Collectors.toList()));
                  resValues.addAll(
                    androidElement.buildTypes().stream().flatMap(e -> e.resValues().stream()).collect(Collectors.toList()));
                  for (ResValue resValue : resValues) {
                    Object typeString = resValue.type();
                    Object nameString = resValue.name();
                    // See if this is one of the unused resources
                    List<LintProblemData> lintProblems = fileListMap.get(VfsUtilCore.virtualToIoFile(psiFile.getVirtualFile()));
                    if (problems != null) {
                      for (LintProblemData problem : lintProblems) {
                        String unusedResource = LintFix.getData(problem.getQuickfixData(), String.class);
                        if (unusedResource != null && unusedResource.equals(SdkConstants.R_PREFIX + typeString + '.' + nameString)) {
                          if (resValue.getModel().getPsiElement() != null) {
                            elements.add(resValue.getModel().getPsiElement());
                            // Keep track of the current buildModel to apply refactoring later on.
                            myBuildModelMap.put(resValue.getModel().getPsiElement(), gradleBuildModel);
                            resValue.remove();
                          }
                        }
                      }
                    }
                  }
                }

                continue;
              }
              if (folderType != ResourceFolderType.VALUES) {
                // Make sure it's not an unused id declaration in a layout/menu/etc file that's
                // also being deleted as unused
                if (issue == UnusedResourceDetector.ISSUE_IDS) {
                  Map<File, List<LintProblemData>> m = map.get(UnusedResourceDetector.ISSUE);
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

  private void addElementsInFile(List<PsiElement> elements, PsiFile psiFile, List<LintProblemData> problems) {
    // Delete all the resources in the given file
    if (psiFile instanceof XmlFile) {
      List<Integer> starts = Lists.newArrayListWithCapacity(problems.size());
      for (LintProblemData problem : problems) {
        if (matchesFilter(problem)) {
          starts.add(problem.getTextRange().getStartOffset());
        }
      }
      starts.sort(Collections.<Integer>reverseOrder());
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
  private Map<Issue, Map<File, List<LintProblemData>>> computeUnusedMap() {
    Map<Issue, Map<File, List<LintProblemData>>> map = Maps.newHashMap();

    Set<Issue> issues;
    if (myIncludeIds) {
      issues = ImmutableSet.of(UnusedResourceDetector.ISSUE, UnusedResourceDetector.ISSUE_IDS);
    } else {
      issues = ImmutableSet.of(UnusedResourceDetector.ISSUE);
    }

    AnalysisScope scope = new AnalysisScope(myProject);

    boolean unusedWasEnabled = UnusedResourceDetector.ISSUE.isEnabledByDefault();
    boolean unusedIdsWasEnabled = UnusedResourceDetector.ISSUE_IDS.isEnabledByDefault();
    UnusedResourceDetector.ISSUE.setEnabledByDefault(true);
    UnusedResourceDetector.ISSUE_IDS.setEnabledByDefault(myIncludeIds);

    try {
      LintBatchResult lintResult = new LintBatchResult(myProject, map, scope, issues);
      LintIdeClient client = LintIdeSupport.get().createBatchClient(lintResult);
      LintRequest request = new LintIdeRequest(client, myProject, null, Arrays.asList(myModules), false);
      request.setScope(Scope.ALL);
      LintDriver lint = new LintDriver(new AndroidLintIdeIssueRegistry(), client, request);
      lint.analyze();
    }
    finally {
      UnusedResourceDetector.ISSUE.setEnabledByDefault(unusedWasEnabled);
      UnusedResourceDetector.ISSUE_IDS.setEnabledByDefault(unusedIdsWasEnabled);
    }

    return map;
  }

  private boolean matchesFilter(@NotNull Map<File, List<LintProblemData>> fileListMap, @NotNull File file) {
    if (myFilter != null) {
      List<LintProblemData> problems = fileListMap.get(file);
      for (LintProblemData problem : problems) {
        String unusedResource = LintFix.getData(problem.getQuickfixData(), String.class);
        if (myFilter.equals(unusedResource)) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  private boolean matchesFilter(@NotNull LintProblemData problem) {
    return myFilter == null || myFilter.equals(LintFix.getData(problem.getQuickfixData(), String.class));
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
      for (UsageInfo usage : usages) {
        PsiElement element = usage.getElement();
        if (element != null && element.isValid()) {
          if (myBuildModelMap.get(element) != null && myBuildModelMap.get(element).isModified()) {
            runWriteCommandAction(myProject, myBuildModelMap.get(element)::applyChanges);
          }
          else {
            element.delete();
          }
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

  @NotNull
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

  @Override
  protected boolean isToBeChanged(@NotNull UsageInfo usageInfo) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // Automatically exclude/deselect elements that contain the string "AUTO-EXCLUDE".
      // This is our simple way to unit test the UI operation of users deselecting certain
      // elements in the refactoring UI.
      PsiElement element = usageInfo.getElement();
      if (element != null && element.getText().contains("AUTO-EXCLUDE")) {
        return false;
      }
    }
    return super.isToBeChanged(usageInfo);
  }
}
