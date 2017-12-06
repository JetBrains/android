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
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleVersion;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersion.AndroidVersionException;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.templates.IdeGoogleMavenRepository;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.migration.PsiMigrationManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.SmartHashSet;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ClassMigrationUsageInfo;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static com.android.SdkConstants.*;
import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;
import static org.jetbrains.android.refactoring.AppCompatMigrationEntry.*;

/**
 * A RefactoringProcessor that can operate on a list of {@link AppCompatMigrationEntry}
 * objects and complete a migration.
 */
public class MigrateToAppCompatProcessor extends BaseRefactoringProcessor {

  static final int MIGRATION_ENTRY_SIZE = 35;
  static final String ATTR_ACTION_VIEW_CLASS = "actionViewClass";
  static final String ANDROID_WIDGET_SEARCH_VIEW_CLASS = "android.widget.SearchView";
  static final String SUPPORT_V7_WIDGET_SEARCH_VIEW_CLASS = "android.support.v7.widget.SearchView";
  static final String ANDROID_WIDGET_SHARE_PROVIDER_CLASS = "android.widget.ShareActionProvider";
  static final String ATTR_ACTION_PROVIDER_CLASS = "actionProviderClass";
  static final String CLASS_SUPPORT_FRAGMENT_ACTIVITY = "android.support.v4.app.FragmentActivity";

  /**
   * Dependency to add to build.gradle
   */
  private static class AppCompatLibraryDescriptor extends ExternalLibraryDescriptor {
    private AppCompatLibraryDescriptor(@Nullable String minVersion) {
      super(GoogleMavenArtifactId.APP_COMPAT_V7.getMavenGroupId(), GoogleMavenArtifactId.APP_COMPAT_V7.getMavenArtifactId(), minVersion,
            null);
    }

    @NotNull
    @Override
    public List<String> getLibraryClassesRoots() {
      return Collections.emptyList();
    }
  }

  private final Module[] myModules;
  private final List<AppCompatMigrationEntry> myMigrationMap;
  private AppCompatStyleMigration myAppCompatStyleMigration;
  private PsiElement[] myElements = PsiElement.EMPTY_ARRAY;
  private final boolean myCreateAppCompatStyleInstance;
  private List<SmartPsiElementPointer<PsiElement>> myRefsToShorten;
  private List<ClassMigrationUsageInfo> myClassMigrations;

  /**
   * Keep track of files that may need {@link android.app.Activity} to be imported
   * during a refactor because findUsages() found either
   * a. A method accepting Activity as a parameter e.g. onAttach(Context)
   * or
   * b. A PsiVariable of the type Activity which in turn may be used for passing to a method
   */
  private final Set<VirtualFile> myPsiFilesWithActivityImports;

  /**
   * Keep track of files that need {@code CLASS_SUPPORT_FRAGMENT_ACTIVITY} to be imported.
   */
  private final Set<VirtualFile> myPsiFilesWithFragmentActivityImports;

  private PsiMigration myPsiMigration;

  protected MigrateToAppCompatProcessor(@NotNull Project project) {
    this(project, buildMigrationMap(), null);
  }

  @VisibleForTesting
  protected MigrateToAppCompatProcessor(@NonNull Project project, @NonNull List<AppCompatMigrationEntry> migrationMap,
                                        @Nullable AppCompatStyleMigration appCompatStyleMigration) {
    super(project, null);
    myModules = ModuleManager.getInstance(project).getModules();
    myMigrationMap = migrationMap;
    myAppCompatStyleMigration = appCompatStyleMigration;
    myCreateAppCompatStyleInstance = myAppCompatStyleMigration == null;
    myPsiFilesWithFragmentActivityImports = new SmartHashSet<>();
    myPsiFilesWithActivityImports = new SmartHashSet<>();
    myPsiMigration = startMigration(project);
  }

  private PsiMigration startMigration(Project project) {
    return PsiMigrationManager.getInstance(project).startMigration();
  }

  @VisibleForTesting
  @NotNull
  static List<AppCompatMigrationEntry> buildMigrationMap() {
    List<AppCompatMigrationEntry> mapEntries = Lists.newArrayListWithExpectedSize(MIGRATION_ENTRY_SIZE);
    // Change Activity => AppCompatActivity
    mapEntries.add(new ClassMigrationEntry(CLASS_ACTIVITY, CLASS_APP_COMPAT_ACTIVITY.defaultName()));
    // ActionBarActivity is deprecated
    mapEntries.add(new ClassMigrationEntry("android.support.v7.app.ActionBarActivity", CLASS_APP_COMPAT_ACTIVITY.defaultName()));
    mapEntries.add(new ClassMigrationEntry(CLASS_SUPPORT_FRAGMENT_ACTIVITY, CLASS_APP_COMPAT_ACTIVITY.defaultName()));
    mapEntries.add(new ClassMigrationEntry("android.app.ActionBar", "android.support.v7.app.ActionBar"));
    // Change method getActionBar => getSupportActionBar
    mapEntries.add(new MethodMigrationEntry(CLASS_ACTIVITY, "getActionBar", CLASS_APP_COMPAT_ACTIVITY.defaultName(),
                                            "getSupportActionBar"));

    // Change method setActionBar => setSupportActionBar
    mapEntries.add(new MethodMigrationEntry(CLASS_ACTIVITY, "setActionBar", CLASS_APP_COMPAT_ACTIVITY.defaultName(),
                                            "setSupportActionBar"));

    mapEntries.add(new ClassMigrationEntry("android.widget.Toolbar", CLASS_TOOLBAR_V7.defaultName()));

    mapEntries.add(new XmlTagMigrationEntry("android.widget.Toolbar", "",
                                            CLASS_TOOLBAR_V7.defaultName(), "",
                                            XmlElementMigration.FLAG_LAYOUT));

    // Change usages of Fragment => v4.app.Fragment
    mapEntries.add(new ClassMigrationEntry("android.app.Fragment", "android.support.v4.app.Fragment"));
    mapEntries.add(new ClassMigrationEntry("android.app.FragmentTransaction",
                                           "android.support.v4.app.FragmentTransaction"));
    mapEntries.add(new ClassMigrationEntry("android.app.FragmentManager",
                                           "android.support.v4.app.FragmentManager"));

    mapEntries.add(new MethodMigrationEntry(CLASS_ACTIVITY, "getFragmentManager",
                                            CLASS_APP_COMPAT_ACTIVITY.defaultName(), "getSupportFragmentManager"));

    mapEntries.add(new ClassMigrationEntry(
      "android.app.AlertDialog",
      "android.support.v7.app.AlertDialog"));

    mapEntries.add(new ClassMigrationEntry(
      "android.widget.SearchView",
      "android.support.v7.widget.SearchView"));

    mapEntries.add(new ClassMigrationEntry(
      "android.widget.ShareActionProvider",
      "android.support.v7.widget.ShareActionProvider"));

    mapEntries.add(new ClassMigrationEntry(
      "android.view.ActionProvider",
      "android.support.v4.view.ActionProvider"));

    // The various MenuItem => MenuItemCompat migrations
    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "getActionProvider",
      "android.support.v4.view.MenuItemCompat", "getActionProvider", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "getActionView",
      "android.support.v4.view.MenuItemCompat", "getActionView", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "collapseActionView",
      "android.support.v4.view.MenuItemCompat", "collapseActionView", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "expandActionView",
      "android.support.v4.view.MenuItemCompat", "expandActionView", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "setActionProvider",
      "android.support.v4.view.MenuItemCompat", "setActionProvider", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "setActionView",
      "android.support.v4.view.MenuItemCompat", "setActionView", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "isActionViewExpanded",
      "android.support.v4.view.MenuItemCompat", "isActionViewExpanded", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "setShowAsAction",
      "android.support.v4.view.MenuItemCompat", "setShowAsAction", 0));

    mapEntries.add(new ClassMigrationEntry(
      "android.view.MenuItem.OnActionExpandListener",
      "android.support.v4.view.MenuItemCompat.OnActionExpandListener"));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "setOnActionExpandListener",
      "android.support.v4.view.MenuItemCompat", "setOnActionExpandListener", 0));

    mapEntries.add(new AttributeMigrationEntry(ATTR_SHOW_AS_ACTION, ANDROID_URI,
                                               ATTR_SHOW_AS_ACTION, AUTO_URI,
                                               XmlElementMigration.FLAG_MENU, TAG_ITEM));

    mapEntries.add(new AttributeMigrationEntry(ATTR_ACTION_VIEW_CLASS, ANDROID_URI,
                                               ATTR_ACTION_VIEW_CLASS, AUTO_URI,
                                               XmlElementMigration.FLAG_MENU, TAG_ITEM));

    mapEntries.add(new AttributeValueMigrationEntry(ANDROID_WIDGET_SEARCH_VIEW_CLASS, SUPPORT_V7_WIDGET_SEARCH_VIEW_CLASS,
                                                    ATTR_ACTION_VIEW_CLASS, ANDROID_URI, XmlElementMigration.FLAG_MENU, TAG_ITEM));

    mapEntries.add(new AttributeMigrationEntry(ATTR_ACTION_PROVIDER_CLASS, ANDROID_URI,
                                               ATTR_ACTION_PROVIDER_CLASS, AUTO_URI,
                                               XmlElementMigration.FLAG_MENU, TAG_ITEM));

    mapEntries.add(new AttributeValueMigrationEntry(ANDROID_WIDGET_SHARE_PROVIDER_CLASS,
                                                    "android.support.v7.widget.ShareActionProvider",
                                                    ATTR_ACTION_PROVIDER_CLASS, ANDROID_URI,
                                                    XmlElementMigration.FLAG_MENU, TAG_ITEM));

    // All Themes and Styles
    mapEntries.add(new AppCompatMigrationEntry(CHANGE_THEME_AND_STYLE));
    mapEntries.add(new AppCompatMigrationEntry(CHANGE_CUSTOM_VIEW_SUPERCLASS));
    // src => srcCompat
    mapEntries.add(new AttributeMigrationEntry("src", ANDROID_URI,
                                               "srcCompat", AUTO_URI, XmlElementMigration.FLAG_LAYOUT,
                                               "ImageView", "ImageButton"));

    mapEntries.add(new ClassMigrationEntry("android.widget.ListPopupWindow",
                                           "android.support.v7.widget.ListPopupWindow"));

    return ImmutableList.copyOf(mapEntries);
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new MigrateToAppCompatUsageViewDescriptor(myElements);
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    myPsiFilesWithFragmentActivityImports.clear();
    myPsiFilesWithActivityImports.clear();

    if (myCreateAppCompatStyleInstance) {
      createAppCompatStyleMigration();
    }

    List<UsageInfo> infos = new ArrayList<>();
    try {
      List<XmlElementMigration> menuXmlOperations = Lists.newArrayListWithExpectedSize(10);
      List<XmlElementMigration> layoutXmlOperations = Lists.newArrayListWithExpectedSize(10);

      for (AppCompatMigrationEntry entry : myMigrationMap) {

        switch (entry.getType()) {
          case CHANGE_CLASS: {
            ClassMigrationEntry classMigrationEntry = (ClassMigrationEntry)entry;
            List<UsageInfo> usages =
              MigrateToAppCompatUtil.findClassUsages(myProject, classMigrationEntry.myOldName);
            boolean isActivity = classMigrationEntry.myOldName.equals(CLASS_ACTIVITY);
            boolean isFragmentActivity = classMigrationEntry.myOldName.equals(CLASS_SUPPORT_FRAGMENT_ACTIVITY);

            for (UsageInfo usageInfo : usages) {

              // Omit method and variable usages especially pointing to `android.app.Activity`
              PsiElement element = usageInfo.getElement();
              if (isActivity) {
                PsiMethod psiMethod = getParentOfType(element, PsiMethod.class);
                // we instead want to special case this to onAttach(Activity)?
                if (psiMethod != null
                    && (MethodUtils.hasSuper(psiMethod) || MethodUtils.isOverridden(psiMethod))) {
                  VirtualFile containingFile = element.getContainingFile().getVirtualFile();
                  myPsiFilesWithActivityImports.add(containingFile);
                  // ignore the actual UsageInfo
                  continue;
                }
              }
              else if (isFragmentActivity && getParentOfType(element, PsiVariable.class) != null) {
                // Special case FragmentActivity activity = getActivity() (within a fragment)
                // TODO maybe look to see if the class extends to support Fragment?
                VirtualFile containingFile = element.getContainingFile().getVirtualFile();
                myPsiFilesWithFragmentActivityImports.add(containingFile);
                continue;
              }
              infos.add(new ClassMigrationUsageInfo(usageInfo, classMigrationEntry));
            }
            break;
          }
          case CHANGE_METHOD: {

            MethodMigrationEntry methodEntry = (MethodMigrationEntry)entry;
            Collection<PsiReference> refs = MigrateToAppCompatUtil.findChangeMethodRefs(myProject, methodEntry);
            for (PsiReference ref : refs) {
              infos.add(new MigrateToAppCompatUsageInfo.ChangeMethodUsageInfo(ref, methodEntry));
            }
            break;
          }
          case CHANGE_THEME_AND_STYLE: {
            for (Module module : myModules) {
              AndroidFacet facet = AndroidFacet.getInstance(module);
              if (facet != null) {
                infos.addAll(myAppCompatStyleMigration.findStyleElementsToBeModified(myProject, facet));
              }
            }
            break;
          }
          case CHANGE_CUSTOM_VIEW_SUPERCLASS: {
            infos.addAll(MigrateToAppCompatUtil.findCustomViewsUsages(myProject, myModules));
            break;
          }
          case REPLACE_METHOD: {
            ReplaceMethodCallMigrationEntry callMigrationEntry = (ReplaceMethodCallMigrationEntry)entry;
            Collection<PsiReference> refs = MigrateToAppCompatUtil.findChangeMethodRefs(myProject, callMigrationEntry);
            for (PsiReference ref : refs) {
              infos.add(new MigrateToAppCompatUsageInfo.ReplaceMethodUsageInfo(ref, callMigrationEntry));
            }
            break;
          }
          case CHANGE_ATTR: // collect all XmlMigration(s)
          case CHANGE_ATTR_VALUE:
          case CHANGE_TAG: {
            XmlElementMigration op = (XmlElementMigration)entry;
            if (op.isMenuOperation()) {
              menuXmlOperations.add((XmlElementMigration)entry);
            }
            if (op.isLayoutOperation()) {
              layoutXmlOperations.add((XmlElementMigration)entry);
            }
            break;
          }
          default:
            throw new AssertionError("Unhandled type " + entry.getType());
        }

        // Process all the Xml operations for a particular resourceType in one pass over each file rather
        // than visiting the same Xml file once for each operation.
        infos.addAll(MigrateToAppCompatUtil.findUsagesOfXmlElements(myProject, myModules, menuXmlOperations, ResourceType.MENU));
        infos.addAll(MigrateToAppCompatUtil.findUsagesOfXmlElements(myProject, myModules, layoutXmlOperations, ResourceType.LAYOUT));
      }
    }
    finally {
      ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(this::finishMigration), myProject.getDisposed());
    }

    MigrateToAppCompatUtil.removeUnneededUsages(infos);
    return infos.toArray(new UsageInfo[infos.size()]);
  }

  private void finishMigration() {
    if (myPsiMigration != null) {
      myPsiMigration.finish();
      myPsiMigration = null;
    }
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    finishMigration();
    PsiMigration psiMigration = PsiMigrationManager.getInstance(myProject).startMigration();
    myClassMigrations = Lists.newArrayList();
    myRefsToShorten = Lists.newArrayList();

    try {
      // Mark the command as global, so that `Undo` is available even if the current file in the
      // editor has not been modified by the refactoring.
      CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
      SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);

      for (UsageInfo usage : usages) {
        PsiElement psiElement = null;
        if (usage instanceof ClassMigrationUsageInfo) {
          ClassMigrationUsageInfo classMigrationUsageInfo = (ClassMigrationUsageInfo)usage;
          myClassMigrations.add(classMigrationUsageInfo);
          psiElement = classMigrationUsageInfo.applyChange(psiMigration);
        }
        else if (usage instanceof MigrateToAppCompatUsageInfo) {
          psiElement = ((MigrateToAppCompatUsageInfo)usage).applyChange(psiMigration);
        }

        if (psiElement != null) {
          myRefsToShorten.add(smartPointerManager.createSmartPsiElementPointer(psiElement));
        }
      }

      // Process the build.gradle dependency change at the very *end*.
      // Note: The reason this is explicitly done after all the migrations is to prevent the index
      // from finding the same class from 'appcompat-v7' which was earlier not found and thus created
      // as a dummy class by PsiMigration. This can lead to cases where the same class seems to be
      // fully qualified in java files that are generated instead of an import.
      for (Module module : computeModulesNeedingAppCompat()) {
        GradleBuildModel buildModel = GradleBuildModel.get(module);
        if (buildModel == null) {
          continue;
        }
        AndroidModel base = buildModel.android();
        String version = base == null ? null : base.compileSdkVersion().value();
        JavaProjectModelModificationService.getInstance(myProject)
          .addDependency(module, new AppCompatLibraryDescriptor(version));
      }
    }
    catch (IncorrectOperationException e) {
      RefactoringUIUtil.processIncorrectOperation(myProject, e);
    }
    finally {
      psiMigration.finish();
    }
  }

  @Override
  protected void performPsiSpoilingRefactoring() {
    postProcessClassMigrations(myClassMigrations);
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myProject);
    for (SmartPsiElementPointer<PsiElement> pointer : myRefsToShorten) {
      PsiElement element = pointer.getElement();
      if (element != null) {
        styleManager.shortenClassReferences(element);
        styleManager.optimizeImports(element.getContainingFile());
      }
    }
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.migrate.to.appcompat";
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return AndroidBundle.message("android.refactoring.migratetoappcompat");
  }

  @Override
  protected boolean skipNonCodeUsages() {
    return true;
  }

  @Override
  protected void refreshElements(@NotNull PsiElement[] elements) {
    finishMigration();
    myPsiMigration = startMigration(myProject);
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    if (refUsages.get().length == 0) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        Messages.showInfoMessage(myProject, RefactoringBundle.message("migration.no.usages.found.in.the.project"),
                                 AndroidBundle.message("android.refactoring.migratetoappcompat"));
      }
      return false;
    }
    setPreviewUsages(true);
    return true;
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElements(myElements);
    return data;
  }

  @Nullable
  @Override
  protected RefactoringEventData getAfterData(@NotNull UsageInfo[] usages) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElements(myElements);
    return data;
  }

  /**
   * @return The minimum set of modules that need the appCompat dependency in this project.
   */
  @VisibleForTesting
  @NonNull
  Set<Module> computeModulesNeedingAppCompat() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    Module[] sortedModules = moduleManager.getSortedModules();
    Set<Module> modulesWithTransitiveAppCompat = new SmartHashSet<>();
    Set<Module> modulesNeedingAppCompat = new SmartHashSet<>();
    for (Module module : sortedModules) {
      AndroidModuleModel model = AndroidModuleModel.get(module);
      if (model == null) {
        continue;
      }
      // dependsOn transitively checks for dependencies so we mark the modules that will
      // transitively receive appcompat from another module.
      if (!modulesWithTransitiveAppCompat.contains(module) &&
          !DependencyManagementUtil.dependsOn(module, GoogleMavenArtifactId.APP_COMPAT_V7)) {
        modulesNeedingAppCompat.add(module);
        modulesWithTransitiveAppCompat.add(module);
      }
      // Ensure that the dependent modules get marked as not needing appcompat
      // since we'll add appCompat to the current module.
      modulesWithTransitiveAppCompat.addAll(moduleManager.getModuleDependentModules(module));
    }
    return modulesNeedingAppCompat;
  }

  /**
   * Once all Class migrations are done, we look at {@code myPsiFilesWithActivityImports}
   * and {@code myPsiFilesWithFragmentActivityImports} and do two things.
   * First we add the specific import of Activity or FragmentActivity to the file and then call optimizeImports().
   *
   * The reason this is done is to ensure that any import migration due to a method parameter or
   * a return type does not affect the compilationUnit.
   *
   * @param psiMigration    PsiMigration instance for looking up the Class.
   * @param classMigrations List of {@link ClassMigrationUsageInfo}'s to be processed.
   */
  private void postProcessClassMigrations(@NonNull List<ClassMigrationUsageInfo> classMigrations) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);

    PsiClass activityClass = psiFacade.findClass(CLASS_ACTIVITY, scope);
    PsiClass fragmentActivityClass = psiFacade.findClass(CLASS_SUPPORT_FRAGMENT_ACTIVITY, scope);

    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);

    classMigrations.stream()
      .filter(u -> u.getElement() != null && u.getElement().isValid())
      .map(u -> u.getElement() == null ? null : u.getElement().getContainingFile())
      .filter(Objects::nonNull)
      .distinct()
      .forEach(psiFile -> {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (myPsiFilesWithActivityImports.contains(virtualFile) && activityClass != null) {
          codeStyleManager.addImport((PsiJavaFile)psiFile, activityClass);
        }
        else if (myPsiFilesWithFragmentActivityImports.contains(virtualFile) && fragmentActivityClass != null) {
          codeStyleManager.addImport((PsiJavaFile)psiFile, fragmentActivityClass);
        }
        codeStyleManager.optimizeImports(psiFile);
      });
  }

  // Create an instance of the AppCompatStyleMigration by looking at the compile Sdk version
  private void createAppCompatStyleMigration() {

    AndroidVersion highest = new AndroidVersion(21); // atleast 21
    for (Module module : myModules) {
      GradleBuildModel build = GradleBuildModel.get(module);
      if (build != null && build.android() != null) {
        //noinspection ConstantConditions
        String version = build.android().compileSdkVersion().value();
        if (version != null) {
          try {
            AndroidVersion current = new AndroidVersion(StringUtil.trimStart(version, "android-"));
            if (current.compareTo(highest) > 0) {
              highest = current;
            }
          }
          catch (AndroidVersionException ignore) {
          }
        }
      }
    }
    AndroidVersion finalAndroidVersion = highest;
    Predicate<GradleVersion> filter = v -> v.toString().startsWith(Integer.toString(finalAndroidVersion.getApiLevel()));

    GradleVersion version = IdeGoogleMavenRepository.INSTANCE.findVersion(
      GoogleMavenArtifactId.APP_COMPAT_V7.getMavenGroupId(), GoogleMavenArtifactId.APP_COMPAT_V7.getMavenArtifactId(), filter,
      finalAndroidVersion.isPreview());

    myAppCompatStyleMigration = AppCompatPublicDotTxtLookup.getInstance()
      .createAppCompatStyleMigration(version == null ? "26.1.0" : version.toString());
  }
}
