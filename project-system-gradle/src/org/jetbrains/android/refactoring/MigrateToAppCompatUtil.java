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

import static com.android.SdkConstants.ANDROIDX_APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.CLASS_ACTIVITY;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.lint.common.LintBatchResult;
import com.android.tools.idea.lint.common.LintIdeClient;
import com.android.tools.idea.lint.common.LintIdeRequest;
import com.android.tools.idea.lint.common.LintIdeSupport;
import com.android.tools.idea.lint.common.LintProblemData;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.lint.checks.AppCompatCustomViewDetector;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.intellij.analysis.AnalysisScope;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.SmartList;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.MethodMigrationEntry;
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ChangeCustomViewUsageInfo;
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ClassMigrationUsageInfo;
import com.android.tools.idea.res.IdeResourcesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class MigrateToAppCompatUtil {

  // Class known for its static members
  private MigrateToAppCompatUtil() {
  }

  static List<UsageInfo> findClassUsages(@NonNull Project project,
                                         @NonNull String qName) {
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(qName, GlobalSearchScope.allScope(project));
    return findRefs(project, aClass);
  }

  public static List<UsageInfo> findPackageUsages(Project project, String qName) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(qName);
    return findRefs(project, aPackage);
  }

  @NotNull
  private static List<UsageInfo> findRefs(@NonNull Project project, PsiElement element) {
    if (element == null) {
      return Collections.emptyList();
    }
    List<UsageInfo> results = new SmartList<>();
    // ignoreScope should work with false in this case but, for some reason, it does not return any results for
    // certain classes even when it should. For now setting it to true to workaround b/79696324
    for (PsiReference usage : ReferencesSearch.search(element, GlobalSearchScope.projectScope(project), true)) {
      if (usage.getElement().isWritable()) {
        results.add(new UsageInfo(usage));
      }
    }
    return results;
  }

  static Collection<PsiReference> findChangeMethodRefs(Project project, MethodMigrationEntry entry) {
    String psiClass = entry.myOldClassName;
    PsiClass psiLookupClass = JavaPsiFacade.getInstance(project).findClass(psiClass, GlobalSearchScope.allScope(project));
    if (psiLookupClass == null) {
      return Collections.emptyList();
    }
    PsiMethod[] methods = psiLookupClass.findMethodsByName(entry.myOldMethodName, true);
    if (methods.length > 0) {
      List<PsiReference> refs = new ArrayList<>();
      for (PsiMethod method : methods) {
        RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(method);
        refs.addAll(processor.findReferences(methods[0], GlobalSearchScope.projectScope(project), false));
      }
      return refs;
    }
    return Collections.emptyList();
  }

  @NonNull
  static List<ChangeCustomViewUsageInfo> findCustomViewsUsages(@NonNull Project project, @NonNull Module[] modules) {
    PsiManager manager = PsiManager.getInstance(project);
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();

    Map<Issue, Map<File, List<LintProblemData>>> issues = computeCustomViewIssuesMap(project, modules);
    Map<File, List<LintProblemData>> fileListMap = issues.get(AppCompatCustomViewDetector.ISSUE);
    if (fileListMap == null) {
      return Collections.emptyList();
    }

    List<ChangeCustomViewUsageInfo> result = new ArrayList<>();

    //noinspection ConstantConditions
    Map<PsiFile, List<LintProblemData>> psiFileListMap = fileListMap.entrySet().stream()
      .filter(e -> fileSystem.findFileByIoFile(e.getKey()) != null)
      .collect(Collectors.toMap(
        e -> manager.findFile(fileSystem.findFileByIoFile(e.getKey())),
        Map.Entry::getValue));

    for (Map.Entry<PsiFile, List<LintProblemData>> entry : psiFileListMap.entrySet()) {
      PsiFile psiFile = entry.getKey();

      if (!psiFile.isValid()) {
        continue;
      }
      List<LintProblemData> problemDataList = entry.getValue();

      for (LintProblemData problemData : problemDataList) {
        int start = problemData.getTextRange().getStartOffset();
        LintFix fix = problemData.getQuickfixData();
        if (!(fix instanceof LintFix.ReplaceString)) continue;
        LintFix.ReplaceString replaceFix = (LintFix.ReplaceString)fix;
        String suggestedSuperClass = replaceFix.getReplacement();
        PsiElement element = PsiTreeUtil.findElementOfClassAtOffset(psiFile, start, PsiElement.class, true);
        if (element != null) {
          result.add(new ChangeCustomViewUsageInfo(element, suggestedSuperClass));
        }
      }
    }
    return result;
  }

  /**
   * Run the {@link AppCompatCustomViewDetector} lint check to find all usages of Custom Views that need
   * to be migrated to their appCompat counterparts.
   *
   * @param project
   * @param modules
   * @return map of issues with the problemdata.
   */
  @NotNull
  static Map<Issue, Map<File, List<LintProblemData>>> computeCustomViewIssuesMap(@NotNull Project project, @NotNull Module[] modules) {
    Map<Issue, Map<File, List<LintProblemData>>> map = Maps.newHashMap();
    boolean detectorWasEnabled = AppCompatCustomViewDetector.ISSUE.isEnabledByDefault();
    AppCompatCustomViewDetector.ISSUE.setEnabledByDefault(true);
    AnalysisScope scope = new AnalysisScope(project);

    try {
      Set<Issue> issues = new HashSet<>(1);
      issues.add(AppCompatCustomViewDetector.ISSUE);
      LintBatchResult lintResult = new LintBatchResult(project, map, scope, issues);
      LintIdeClient client = LintIdeSupport.get().createBatchClient(lintResult);
      LintRequest request = new LintIdeRequest(client, project, null, Arrays.asList(modules), false) {
        @Nullable com.android.tools.lint.detector.api.Project myMainProject = null;
        @NonNull
        @Override
        public com.android.tools.lint.detector.api.Project getMainProject(@NonNull com.android.tools.lint.detector.api.Project project) {
          if (myMainProject == null) {
            com.android.tools.lint.detector.api.Project mainProject = super.getMainProject(project);
            // Ensure it has its own directory to give it a unique identity; otherwise lint might
            // confuse the two (assigning them the same configuration, which can lead to cycles, etc)
            File dir = new File(mainProject.getDir().getParentFile(), mainProject.getName() + "-main");
            myMainProject = new com.android.tools.lint.detector.api.Project(mainProject.getClient(), dir, dir) {
              @Override
              public Boolean dependsOn(@NotNull String artifact) {
                // Make it look like the App already depends on AppCompat to get the warnings for custom views.
                if (APPCOMPAT_LIB_ARTIFACT.equals(artifact) || ANDROIDX_APPCOMPAT_LIB_ARTIFACT.equals(artifact)) {
                  return Boolean.TRUE;
                }
                return super.dependsOn(artifact);
              }
            };
          }
          return myMainProject;
        }
      };
      request.setScope(Scope.JAVA_FILE_SCOPE);
      client.createDriver(request, LintIdeSupport.get().getIssueRegistry()).analyze();
    }
    finally {
      AppCompatCustomViewDetector.ISSUE.setEnabledByDefault(detectorWasEnabled);
    }
    return map;
  }

  /**
   * Get {@link XmlFile} instances of type {@link ResourceType} from the given
   * {@link ResourceRepository} and {@link Project}.
   *
   * @param project      The project to use to get the PsiFile.
   * @param repository   The repository to be used for getting the items.
   * @param resourceType The resourceType to look up
   * @return A Set of XmlFile objects.
   */
  @NonNull
  static Set<XmlFile> getPsiFilesOfType(@NonNull Project project,
                                        @NonNull ResourceRepository repository,
                                        @NonNull ResourceType resourceType) {

    Collection<String> itemsOfType = repository.getResources(ResourceNamespace.TODO(), resourceType).keySet();

    return itemsOfType.stream()
      .map(name -> repository.getResources(ResourceNamespace.TODO(), resourceType, name))
      .flatMap(Collection::stream)
      .map(item -> IdeResourcesUtil.getItemPsiFile(project, item))
      .filter(f -> f instanceof XmlFile)
      .map(XmlFile.class::cast)
      .collect(Collectors.toSet());
  }

  /**
   * Utility method for finding usages of any {@link AppCompatMigrationEntry.XmlElementMigration}.
   *
   * @param project the current project
   * @param modules The modules that should be looked at for this project.
   * @param operations A list of {@link AppCompatMigrationEntry.XmlElementMigration} instances that define
   *                   which tags/attributes and attribute values should be looked at.
   * @param resourceType The {@link ResourceType} such as LAYOUT, MENU that is used for fetching
   *                     the resources from the {@link LocalResourceRepository}.
   * @return A list of UsageInfos that describe the changes to be migrated.
   */
  public static List<UsageInfo> findUsagesOfXmlElements(@NonNull Project project,
                                                        @NonNull Module[] modules,
                                                        @NonNull List<AppCompatMigrationEntry.XmlElementMigration> operations,
                                                        @NonNull ResourceType resourceType) {

    if (operations.isEmpty()) {
      return Collections.emptyList();
    }
    // Create a mapping between tagName => XmlElementMigration so we can simply lookup any xmlOperations
    // for a given tagName when visiting all the xml tags. (This is to prevent looking at each operation
    // while visiting every xml tag in a file)
    ArrayListMultimap<String, AppCompatMigrationEntry.XmlElementMigration> tag2XmlOperation = ArrayListMultimap.create();
    for (AppCompatMigrationEntry.XmlElementMigration operation : operations) {
      for (String tagName : operation.applicableTagNames()) {
        tag2XmlOperation.put(tagName, operation);
      }
    }

    List<UsageInfo> usageInfos = new ArrayList<>();
    for (Module module : modules) {
      LocalResourceRepository projectResources = ResourceRepositoryManager.getProjectResources(module);
      if (projectResources == null) {
        continue;
      }
      Set<XmlFile> xmlFiles = getPsiFilesOfType(project, projectResources, resourceType);
      for (XmlFile file : xmlFiles) {
        file.accept(new XmlRecursiveElementVisitor() {
          @Override
          public void visitXmlTag(XmlTag tag) {
            super.visitXmlTag(tag);
            List<AppCompatMigrationEntry.XmlElementMigration> operations = tag2XmlOperation.get(tag.getName());
            if (operations != null) {
              for (AppCompatMigrationEntry.XmlElementMigration operation : operations) {
                UsageInfo usage = operation.apply(tag);
                if (usage != null) {
                  usageInfos.add(usage);
                }
              }
            }
          }
        });
      }
    }
    tag2XmlOperation.clear();
    return usageInfos;
  }

  /**
   * Prevent the issue where we show Usages only pointing to a migration for an import
   * especially for Activity and FragmentActivity.
   *
   * This happens because we exclude the two classes when used within a method parameter.
   * for e.g: onAttach(Activity activity) - should not be migrated.
   *
   * @param infos The usageInfos to process
   */
  public static void removeUnneededUsages(@NonNull List<UsageInfo> infos) {

    ArrayListMultimap<PsiFile, ClassMigrationUsageInfo> map = ArrayListMultimap.create();
    for (UsageInfo usageInfo : infos) {
      if (!(usageInfo instanceof ClassMigrationUsageInfo)) {
        continue;
      }
      if (usageInfo.getElement() == null || usageInfo.getElement().getContainingFile() == null) {
        continue;
      }
      map.put(usageInfo.getElement().getContainingFile(), (ClassMigrationUsageInfo)usageInfo);
    }

    List<UsageInfo> toRemove = new SmartList<>();
    for (PsiFile file : map.keySet()) {
      List<ClassMigrationUsageInfo> usages = map.get(file);
      boolean excludeUsages = usages.stream()
        .allMatch(u -> {
          if (u.getElement() != null && u.getElement().getParent() instanceof PsiImportStatement) {
            String qname = ((PsiImportStatement)u.getElement().getParent()).getQualifiedName();
            if (qname != null &&
                (qname.equals(CLASS_ACTIVITY) || qname.equals(MigrateToAppCompatProcessor.CLASS_SUPPORT_FRAGMENT_ACTIVITY))) {
              return true;
            }
          }
          return false;
        });
      if (excludeUsages) {
        toRemove.addAll(usages);
      }
    }
    infos.removeAll(toRemove);
  }

  static boolean isKotlinSimpleNameReference(PsiReference reference) {
    PluginId kotlinPluginId = PluginId.findId("org.jetbrains.kotlin");
    IdeaPluginDescriptor kotlinPlugin = Objects.requireNonNull(PluginManagerCore.getPlugin(kotlinPluginId));
    ClassLoader pluginClassLoader = kotlinPlugin.getPluginClassLoader();
    try {
      Class<?> simpleNameReferenceClass =
        Class.forName("org.jetbrains.kotlin.idea.references.KtSimpleNameReference", true, pluginClassLoader);
      return simpleNameReferenceClass.isInstance(reference);
    }
    catch (ClassNotFoundException e) {
      return false;
    }
  }
}
