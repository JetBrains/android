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

import com.android.annotations.NonNull;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.lint.LintIdeClient;
import com.android.tools.idea.lint.LintIdeIssueRegistry;
import com.android.tools.idea.lint.LintIdeRequest;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ProjectResourceRepository;
import com.android.tools.lint.checks.AppCompatCustomViewDetector;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.analysis.AnalysisScope;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import org.jetbrains.android.inspections.lint.ProblemData;
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.MethodMigrationEntry;
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ChangeCustomViewUsageInfo;
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ClassMigrationUsageInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.CLASS_ACTIVITY;

class MigrateToAppCompatUtil {

  // Class known for its static members
  private MigrateToAppCompatUtil() {
  }

  static List<UsageInfo> findClassUsages(@NonNull Project project,
                                         @NonNull String qName) {
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(qName, GlobalSearchScope.allScope(project));
    return findRefs(project, aClass);
  }

  public static List<UsageInfo> findPackageUsages(Project project, PsiMigration migration, String qName) {
    PsiPackage aPackage = findOrCreatePackage(project, migration, qName);
    return findRefs(project, aPackage);
  }

  @NotNull
  private static List<UsageInfo> findRefs(@NonNull Project project, PsiElement element) {
    if (element == null) {
      return Collections.emptyList();
    }
    List<UsageInfo> results = new SmartList<>();
    for (PsiReference usage : ReferencesSearch.search(element, GlobalSearchScope.projectScope(project), false)) {
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
        refs.addAll(processor.findReferences(methods[0], false));
      }
      return refs;
    }
    return Collections.emptyList();
  }

  // Code copied from MigrationUtil since it's not marked public
  static PsiClass findOrCreateClass(Project project, final PsiMigration migration, final String qName) {
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(qName, GlobalSearchScope.allScope(project));
    if (aClass == null) {
      aClass = WriteAction.compute(() -> migration.createClass(qName));
    }
    return aClass;
  }

  static PsiPackage findOrCreatePackage(Project project, final PsiMigration migration, final String qName) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(qName);
    if (aPackage != null) {
      return aPackage;
    }
    else {
      return WriteAction.compute(() -> migration.createPackage(qName));
    }
  }

  @NonNull
  static List<ChangeCustomViewUsageInfo> findCustomViewsUsages(@NonNull Project project, @NonNull Module[] modules) {
    PsiManager manager = PsiManager.getInstance(project);
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();

    Map<Issue, Map<File, List<ProblemData>>> issues = computeCustomViewIssuesMap(project, modules);
    Map<File, List<ProblemData>> fileListMap = issues.get(AppCompatCustomViewDetector.ISSUE);
    if (fileListMap == null) {
      return Collections.emptyList();
    }

    List<ChangeCustomViewUsageInfo> result = Lists.newArrayList();

    //noinspection ConstantConditions
    Map<PsiFile, List<ProblemData>> psiFileListMap = fileListMap.entrySet().stream()
      .filter(e -> fileSystem.findFileByIoFile(e.getKey()) != null)
      .collect(Collectors.toMap(
        e -> manager.findFile(fileSystem.findFileByIoFile(e.getKey())),
        Map.Entry::getValue));

    for (Map.Entry<PsiFile, List<ProblemData>> entry : psiFileListMap.entrySet()) {
      PsiFile psiFile = entry.getKey();

      if (!psiFile.isValid()) {
        continue;
      }
      List<ProblemData> problemDataList = entry.getValue();

      for (ProblemData problemData : problemDataList) {
        Integer start = problemData.getTextRange().getStartOffset();
        LintFix fix = problemData.getQuickfixData();
        if (!(fix instanceof LintFix.ReplaceString)) continue;
        LintFix.ReplaceString replaceFix = (LintFix.ReplaceString)fix;
        String suggestedSuperClass = replaceFix.replacement;
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
  static Map<Issue, Map<File, List<ProblemData>>> computeCustomViewIssuesMap(@NotNull Project project, @NotNull Module[] modules) {
    Map<Issue, Map<File, List<ProblemData>>> map = Maps.newHashMap();
    boolean detectorWasEnabled = AppCompatCustomViewDetector.ISSUE.isEnabledByDefault();
    AppCompatCustomViewDetector.ISSUE.setEnabledByDefault(true);
    AnalysisScope scope = new AnalysisScope(project);

    try {
      Set<Issue> issues = new HashSet<>(1);
      issues.add(AppCompatCustomViewDetector.ISSUE);
      LintIdeClient client = LintIdeClient.forBatch(project, map, scope, issues);
      LintRequest request = new LintIdeRequest(client, project, null, Arrays.asList(modules), false) {
        @NonNull
        @Override
        public com.android.tools.lint.detector.api.Project getMainProject(@NonNull com.android.tools.lint.detector.api.Project project) {
          com.android.tools.lint.detector.api.Project mainProject = super.getMainProject(project);
          return new com.android.tools.lint.detector.api.Project(mainProject.getClient(), mainProject.getDir(),
                                                                 mainProject.getReferenceDir()) {
            @Override
            public Boolean dependsOn(@NotNull String artifact) {
              // Make it look like the App already depends on AppCompat to get the warnings for custom views.
              if (APPCOMPAT_LIB_ARTIFACT.equals(artifact)) {
                return Boolean.TRUE;
              }
              return super.dependsOn(artifact);
            }
          };
        }
      };
      request.setScope(Scope.JAVA_FILE_SCOPE);
      new LintDriver(new LintIdeIssueRegistry(), client, request).analyze();
    }
    finally {
      AppCompatCustomViewDetector.ISSUE.setEnabledByDefault(detectorWasEnabled);
    }
    return map;
  }

  /**
   * Get {@link XmlFile} instances of type {@link ResourceType} from the given
   * {@link AbstractResourceRepository} and {@link Project}.
   *
   * @param project      The project to use to get the PsiFile.
   * @param repository   The repository to be used for getting the items.
   * @param resourceType The resourceType to look up
   * @return A Set of XmlFile objects.
   */
  @NonNull
  static Set<XmlFile> getPsiFilesOfType(@NonNull Project project,
                                        @NonNull AbstractResourceRepository repository,
                                        @NonNull ResourceType resourceType) {

    Collection<String> itemsOfType = repository.getItemsOfType(resourceType);

    return itemsOfType.stream()
      .map(name -> repository.getResourceItem(resourceType, name))
      .flatMap(Collection::stream)
      .map(item -> LocalResourceRepository.getItemPsiFile(project, item))
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
   *                     the resources from the {@link ProjectResourceRepository}.
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
      ProjectResourceRepository projectResources = ProjectResourceRepository.getOrCreateInstance(module);
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
    IdeaPluginDescriptor kotlinPlugin = ObjectUtils.notNull(PluginManager.getPlugin(kotlinPluginId));
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
