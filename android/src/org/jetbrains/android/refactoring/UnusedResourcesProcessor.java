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
import static com.android.SdkConstants.EXT_GRADLE;
import static com.android.SdkConstants.EXT_GRADLE_KTS;
import static com.android.tools.lint.checks.UnusedResourceDetector.KEY_RESOURCE_FIELD;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleModelProvider;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.FlavorTypeModel.ResValue;
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
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

public class UnusedResourcesProcessor extends BaseRefactoringProcessor {
  private final String myFilter;
  private final Module[] myModules;
  private PsiElement[] myElements = PsiElement.EMPTY_ARRAY;
  private boolean myIncludeIds;
  private String myCachedCommandName = null;
  private final Map<PsiElement, GradleBuildModel> myBuildModelMap;

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
    final List<PsiElement> elements = new ArrayList<>();

    // Make sure lint didn't put extra issues into the map
    for (Issue issue : Lists.newArrayList(map.keySet())) {
      if (issue != UnusedResourceDetector.ISSUE && issue != UnusedResourceDetector.ISSUE_IDS) {
        map.remove(issue);
      }
    }
    ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiManager manager = PsiManager.getInstance(myProject);

    Map<File, PsiFile> files = Maps.newHashMap();
    Set<PsiFile> excludedFiles = new HashSet<>();
    for (Map.Entry<Issue, Map<File, List<LintProblemData>>> entry : map.entrySet()) {
      for (File file : entry.getValue().keySet()) {
        if (!files.containsKey(file)) {
          VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
          if (virtualFile != null) {
            if (!virtualFile.isDirectory()) { // Gradle model errors currently don't have source positions
              PsiFile psiFile = manager.findFile(virtualFile);
              if (psiFile != null) {
                files.put(file, psiFile);

                // See whether the file with the warnings is in module that is not included
                // in this scope. If so, record it into the list of excluded files such that
                // we can skip removing these references later on.
                Module module = ModuleUtilCore.findModuleForFile(psiFile);
                if (module != null) {
                  if (ArrayUtil.find(myModules, module) == -1) {
                    excludedFiles.add(psiFile);
                  }
                }
              }
            }
          }
        }
      }
    }

    // We cannot just skip removing references in modules outside of the scope.
    // If an unused resource is referenced from outside the included scope,
    // then deleting it partially would result in a broken project. Therefore,
    // track which references appear in excluded files, which we'll then later
    // use to also skip removing references in included scopes that are referenced
    // from excluded files.
    Set<String> excludedResources = new HashSet<>();
    if (!excludedFiles.isEmpty()) {
      for (Map.Entry<Issue, Map<File, List<LintProblemData>>> entry : map.entrySet()) {
        Map<File, List<LintProblemData>> fileMap = entry.getValue();
        for (File file : fileMap.keySet()) {
          PsiFile psiFile = files.get(file);
          if (excludedFiles.contains(psiFile)) {
            List<LintProblemData> list = fileMap.get(file);
            if (list != null) {
              for (LintProblemData problem : list) {
                String resource = getResource(problem);
                if (resource != null) {
                  excludedResources.add(resource);
                }
              }
            }
          }
        }
      }
    }

    for (Issue issue : new Issue[]{UnusedResourceDetector.ISSUE, UnusedResourceDetector.ISSUE_IDS}) {
      Map<File, List<LintProblemData>> fileListMap = map.get(issue);
      if (fileListMap != null && !fileListMap.isEmpty()) {
        if (!files.isEmpty()) {
          for (File file : files.keySet()) {
            PsiFile psiFile = files.get(file);
            if (psiFile == null) {
              // Ignore for now; currently this happens for build.gradle resValue definitions
              // where we only had the project directory as the location from the Gradle model
              continue;
            }

            if (excludedFiles.contains(psiFile)) {
              continue;
            }

            if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, psiFile)) {
              continue;
            }

            List<LintProblemData> problems = fileListMap.get(file);
            if (problems == null) {
              continue;
            }

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
                  GradleBuildModel gradleBuildModel = GradleModelProvider.getInstance().parseBuildFile(psiFile.getVirtualFile(), myProject);

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
                        String unusedResource = getResource(problem);
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
                  addElementsInFile(elements, psiFile, problems, excludedResources);
                }
                else {
                  // Unused non-value resource file: Delete the whole file
                  if (matchesFilter(fileListMap, file)) {
                    elements.add(psiFile);
                  }
                }
              }
              else {
                addElementsInFile(elements, psiFile, problems, excludedResources);
              }
            }
          }
        }
      }
    }
    return elements;
  }

  private static String getResource(LintProblemData problem) {
    return LintFix.getString(problem.getQuickfixData(), KEY_RESOURCE_FIELD, null);
  }

  private void addElementsInFile(List<PsiElement> elements,
                                 PsiFile psiFile,
                                 List<LintProblemData> problems,
                                 Set<String> excludedResources) {
    // Delete all the resources in the given file
    if (psiFile instanceof XmlFile) {
      List<Integer> starts = Lists.newArrayListWithCapacity(problems.size());
      for (LintProblemData problem : problems) {
        if (excludedResources.contains(getResource(problem))) {
          continue;
        }
        if (matchesFilter(problem)) {
          starts.add(problem.getTextRange().getStartOffset());
        }
      }
      starts.sort(Collections.reverseOrder());
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
      // Note: We pass in *all* modules in the project here, not just those in the scope of the
      // resource refactoring. If you for example are running the unused resource refactoring on a
      // library module, we want to only remove unused resources from the specific library
      // module, but we still have to have lint analyze all modules such that it doesn't consider
      // resources in the library as unused when they could be referenced from other modules.
      // So, we'll analyze all modules with lint, and then in the UnusedResourceProcessor
      // we'll filter the matches down to only those in the target modules when we're done.
      List<Module> modules = Arrays.asList(ModuleManager.getInstance(myProject).getModules());
      LintRequest request = new LintIdeRequest(client, myProject, null, modules, false);
      request.setScope(Scope.ALL);
      LintDriver lint = client.createDriver(request, LintIdeSupport.get().getIssueRegistry());
      // Make sure we don't remove resources that are still referenced from
      // tests (though these should probably be in a test resource source
      // set instead.)
      lint.setCheckTestSources(true);
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
        if (myFilter.equals(getResource(problem))) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  private boolean matchesFilter(@NotNull LintProblemData problem) {
    return myFilter == null || myFilter.equals(getResource(problem));
  }

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
