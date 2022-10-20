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

import com.android.AndroidXConstants;
import com.android.annotations.NonNull;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.google.common.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleVersion;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.support.AndroidxName;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.java.refactoring.JavaRefactoringBundle;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.migration.PsiMigrationManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.SmartHashSet;
import com.siyeh.ig.psiutils.MethodUtils;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ClassMigrationUsageInfo;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static com.android.SdkConstants.*;
import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;
import static org.jetbrains.android.refactoring.AppCompatMigrationEntry.*;

/**
 * A RefactoringProcessor that can operate on a list of {@link AppCompatMigrationEntry}
 * objects and complete a migration.
 */
public class MigrateToAppCompatProcessor extends BaseRefactoringProcessor {

  protected static final BiFunction<GoogleMavenArtifactId, String, AppCompatStyleMigration> DEFAULT_MIGRATION_FACTORY = (artifact, version) ->
    AppCompatPublicDotTxtLookup.getInstance().createAppCompatStyleMigration(artifact, version);

  static final int MIGRATION_ENTRY_SIZE = 35;
  static final String ATTR_ACTION_VIEW_CLASS = "actionViewClass";
  static final String ANDROID_WIDGET_SEARCH_VIEW_CLASS = "android.widget.SearchView";
  static final String SUPPORT_V7_WIDGET_SEARCH_VIEW_CLASS = "android.support.v7.widget.SearchView";
  static final String ANDROID_WIDGET_SHARE_PROVIDER_CLASS = "android.widget.ShareActionProvider";
  static final String ATTR_ACTION_PROVIDER_CLASS = "actionProviderClass";
  static final String CLASS_SUPPORT_FRAGMENT_ACTIVITY = "android.support.v4.app.FragmentActivity";
  static final AndroidxName ANDROID_SUPPORT_V7_APP_ALERT_DIALOG = new AndroidxName("android.support.v7.app.AlertDialog", "androidx.appcompat.app.AlertDialog");
  static final AndroidxName ANDROID_SUPPORT_V7_WIDGET_SEARCH_VIEW = new AndroidxName("android.support.v7.widget.SearchView", "androidx.appcompat.widget.SearchView");
  static final AndroidxName ANDROID_SUPPORT_V7_WIDGET_SHARE_ACTION_PROVIDER = new AndroidxName("android.support.v7.widget.ShareActionProvider", "androidx.appcompat.widget.ShareActionProvider");
  static final AndroidxName ANDROID_SUPPORT_V4_VIEW_ACTION_PROVIDER = new AndroidxName("android.support.v4.view.ActionProvider", "androidx.core.view.ActionProvider");
  static final AndroidxName ANDROID_SUPPORT_V4_VIEW_MENU_ITEM_COMPAT = new AndroidxName("android.support.v4.view.MenuItemCompat", "androidx.core.view.MenuItemCompat");
  static final AndroidxName ANDROID_SUPPORT_V7_WIDGET_LIST_POPUP_WINDOW = new AndroidxName("android.support.v7.widget.ListPopupWindow", "androidx.appcompat.widget.ListPopupWindow");
  static final AndroidxName ANDROID_SUPPORT_V7_APP_ACTION_BAR = new AndroidxName("android.support.v7.app.ActionBar", "androidx.appcompat.app.ActionBar");
  static final AndroidxName ANDROID_SUPPORT_V4_APP_FRAGMENT_TRANSACTION = new AndroidxName("android.support.v4.app.FragmentTransaction", "androidx.fragment.app.FragmentTransaction");
  static final AndroidxName ANDROID_SUPPORT_V4_APP_FRAGMENT_MANAGER = new AndroidxName("android.support.v4.app.FragmentManager", "androidx.fragment.app.FragmentManager");

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

    @Nullable
    @Override
    public String getMinVersion() {
      String minStr = super.getMinVersion();
      try {
        int min = Integer.parseInt(minStr);
        return Integer.toString(Math.min(28, min));
      }
      catch (NumberFormatException e) {
        return minStr;
      }
    }
  }

  private final Module[] myModules;
  private final List<AppCompatMigrationEntry> myMigrationMap;
  private AppCompatStyleMigration myAppCompatStyleMigration;
  private PsiElement[] myElements = PsiElement.EMPTY_ARRAY;
  private List<SmartPsiElementPointer<PsiElement>> myRefsToShorten;
  private List<ClassMigrationUsageInfo> myClassMigrations;

  private final BiFunction<GoogleMavenArtifactId, String, AppCompatStyleMigration> myAppCompatStyleMigrationFactory;

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
    this(project, DEFAULT_MIGRATION_FACTORY);
  }

  MigrateToAppCompatProcessor(@NotNull Project project,
                              @NotNull BiFunction<GoogleMavenArtifactId, String, AppCompatStyleMigration> appCompatStyleMigrationFactory) {
    this(project, buildMigrationMap(project), appCompatStyleMigrationFactory);
  }

  @VisibleForTesting
  MigrateToAppCompatProcessor(@NonNull Project project, @NonNull List<AppCompatMigrationEntry> migrationMap,
                              @NotNull BiFunction<GoogleMavenArtifactId, String, AppCompatStyleMigration> appCompatStyleMigrationFactory) {
    super(project, null);
    myModules = ModuleManager.getInstance(project).getModules();
    myMigrationMap = migrationMap;
    myAppCompatStyleMigrationFactory = appCompatStyleMigrationFactory;
    myPsiFilesWithFragmentActivityImports = new SmartHashSet<>();
    myPsiFilesWithActivityImports = new SmartHashSet<>();
    myPsiMigration = startMigration(project);
  }

  private PsiMigration startMigration(Project project) {
    return PsiMigrationManager.getInstance(project).startMigration();
  }

  /**
   * Returns the correct name of the given {@link AndroidxName} based on whether AndroidX is on or not
   */
  @NotNull
  private static String getName(boolean isAndroidx, @NotNull AndroidxName name) {
    return isAndroidx ? name.newName() : name.oldName();
  }

  @VisibleForTesting
  @NotNull
  static List<AppCompatMigrationEntry> buildMigrationMap(@NotNull Project project) {
    boolean isAndroidx = MigrateToAndroidxUtil.isAndroidx(project);

    List<AppCompatMigrationEntry> mapEntries = Lists.newArrayListWithExpectedSize(MIGRATION_ENTRY_SIZE);
    // Change Activity => AppCompatActivity
    mapEntries.add(new ClassMigrationEntry(CLASS_ACTIVITY, getName(isAndroidx, AndroidXConstants.CLASS_APP_COMPAT_ACTIVITY)));
    // ActionBarActivity is deprecated
    mapEntries.add(new ClassMigrationEntry("android.support.v7.appActionBarActivity",
                                           getName(isAndroidx, AndroidXConstants.CLASS_APP_COMPAT_ACTIVITY)));
    mapEntries.add(new ClassMigrationEntry(CLASS_SUPPORT_FRAGMENT_ACTIVITY,
                                           getName(isAndroidx, AndroidXConstants.CLASS_APP_COMPAT_ACTIVITY)));
    mapEntries.add(new ClassMigrationEntry("android.app.ActionBar",
                                           getName(isAndroidx, ANDROID_SUPPORT_V7_APP_ACTION_BAR)));
    // Change method getActionBar => getSupportActionBar
    mapEntries.add(new MethodMigrationEntry(CLASS_ACTIVITY, "getActionBar",
                                            getName(isAndroidx, AndroidXConstants.CLASS_APP_COMPAT_ACTIVITY),
                                            "getSupportActionBar"));

    // Change method setActionBar => setSupportActionBar
    mapEntries.add(new MethodMigrationEntry(CLASS_ACTIVITY, "setActionBar",
                                            getName(isAndroidx, AndroidXConstants.CLASS_APP_COMPAT_ACTIVITY),
                                            "setSupportActionBar"));

    mapEntries.add(new ClassMigrationEntry("android.widget.Toolbar",
                                           getName(isAndroidx, AndroidXConstants.CLASS_TOOLBAR_V7)));

    mapEntries.add(new XmlTagMigrationEntry("android.widget.Toolbar", "",
                                            getName(isAndroidx, AndroidXConstants.CLASS_TOOLBAR_V7), "",
                                            XmlElementMigration.FLAG_LAYOUT));

    // Change usages of Fragment => v4.app.Fragment
    mapEntries.add(new ClassMigrationEntry("android.app.Fragment",
                                           getName(isAndroidx, AndroidXConstants.CLASS_V4_FRAGMENT)));
    mapEntries.add(new ClassMigrationEntry("android.app.FragmentTransaction",
                                           getName(isAndroidx, ANDROID_SUPPORT_V4_APP_FRAGMENT_TRANSACTION)));
    mapEntries.add(new ClassMigrationEntry("android.app.FragmentManager",
                                           getName(isAndroidx, ANDROID_SUPPORT_V4_APP_FRAGMENT_MANAGER)));

    mapEntries.add(new MethodMigrationEntry(CLASS_ACTIVITY, "getFragmentManager",
                                            getName(isAndroidx, AndroidXConstants.CLASS_APP_COMPAT_ACTIVITY),
                                            "getSupportFragmentManager"));

    mapEntries.add(new ClassMigrationEntry(
      "android.app.AlertDialog",
      getName(isAndroidx, ANDROID_SUPPORT_V7_APP_ALERT_DIALOG)));

    mapEntries.add(new ClassMigrationEntry(
      "android.widget.SearchView",
      getName(isAndroidx, ANDROID_SUPPORT_V7_WIDGET_SEARCH_VIEW)));

    mapEntries.add(new ClassMigrationEntry(
      "android.widget.ShareActionProvider",
      getName(isAndroidx, ANDROID_SUPPORT_V7_WIDGET_SHARE_ACTION_PROVIDER)));

    mapEntries.add(new ClassMigrationEntry(
      "android.view.ActionProvider",
      getName(isAndroidx, ANDROID_SUPPORT_V4_VIEW_ACTION_PROVIDER)));

    // The various MenuItem => MenuItemCompat migrations
    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "getActionProvider",
      getName(isAndroidx, ANDROID_SUPPORT_V4_VIEW_MENU_ITEM_COMPAT), "getActionProvider", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "getActionView",
      getName(isAndroidx, ANDROID_SUPPORT_V4_VIEW_MENU_ITEM_COMPAT), "getActionView", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "collapseActionView",
      getName(isAndroidx, ANDROID_SUPPORT_V4_VIEW_MENU_ITEM_COMPAT), "collapseActionView", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "expandActionView",
      getName(isAndroidx, ANDROID_SUPPORT_V4_VIEW_MENU_ITEM_COMPAT), "expandActionView", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "setActionProvider",
      getName(isAndroidx, ANDROID_SUPPORT_V4_VIEW_MENU_ITEM_COMPAT), "setActionProvider", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "setActionView",
      getName(isAndroidx, ANDROID_SUPPORT_V4_VIEW_MENU_ITEM_COMPAT), "setActionView", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "isActionViewExpanded",
      getName(isAndroidx, ANDROID_SUPPORT_V4_VIEW_MENU_ITEM_COMPAT), "isActionViewExpanded", 0));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "setShowAsAction",
      getName(isAndroidx, ANDROID_SUPPORT_V4_VIEW_MENU_ITEM_COMPAT), "setShowAsAction", 0));

    mapEntries.add(new ClassMigrationEntry(
      "android.view.MenuItem.OnActionExpandListener",
      getName(isAndroidx, ANDROID_SUPPORT_V4_VIEW_MENU_ITEM_COMPAT) + ".OnActionExpandListener"));

    mapEntries.add(new ReplaceMethodCallMigrationEntry(
      "android.view.MenuItem", "setOnActionExpandListener",
      getName(isAndroidx, ANDROID_SUPPORT_V4_VIEW_MENU_ITEM_COMPAT), "setOnActionExpandListener", 0));

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
                                                    getName(isAndroidx, ANDROID_SUPPORT_V7_WIDGET_SHARE_ACTION_PROVIDER),
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
                                           getName(isAndroidx, ANDROID_SUPPORT_V7_WIDGET_LIST_POPUP_WINDOW)));

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

    if (myAppCompatStyleMigration == null) {
      myAppCompatStyleMigration = createAppCompatStyleMigration(myModules, myAppCompatStyleMigrationFactory);
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
    return infos.toArray(UsageInfo.EMPTY_ARRAY);
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
    myClassMigrations = new ArrayList<>();
    myRefsToShorten = new ArrayList<>();

    try {
      // Mark the command as global, so that `Undo` is available even if the current file in the
      // editor has not been modified by the refactoring.
      CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
      SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);

      List<MigrateToAppCompatUsageInfo> migrateToAppCompatUsageInfos = Arrays.stream(usages)
        .filter(MigrateToAppCompatUsageInfo.class::isInstance)
        .map(MigrateToAppCompatUsageInfo.class::cast)
        .collect(Collectors.toList());
      for (MigrateToAppCompatUsageInfo usage : MigrateToAppCompatUsageInfoUtilKt.sortToApply(migrateToAppCompatUsageInfos)) {
        PsiElement psiElement;
        if (usage instanceof ClassMigrationUsageInfo) {
          ClassMigrationUsageInfo classMigrationUsageInfo = (ClassMigrationUsageInfo)usage;
          myClassMigrations.add(classMigrationUsageInfo);
          psiElement = classMigrationUsageInfo.applyChange(psiMigration);
        }
        else  {
          psiElement = usage.applyChange(psiMigration);
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
        AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(module);
        if (moduleInfo == null) {
          continue;
        }
        AndroidVersion androidVersion = moduleInfo.getBuildSdkVersion();
        // This is a string which is used to filer artifact versions by matching their full versions with this prefix ("android-nn" strings
        // are also recognised by dropping the 'android-' prefix if present). This way the most recent version with a given major version is
        // found.
        String version = androidVersion != null ? Integer.toString(androidVersion.getApiLevel()) : null;
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
        Messages.showInfoMessage(myProject, JavaRefactoringBundle.message("migration.no.usages.found.in.the.project"),
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
      AndroidModel model = AndroidModel.get(module);
      if (model == null) {
        continue;
      }
      if (!ModuleSystemUtil.isMainModule(module)) {
        continue;
      }
      // dependsOn transitively checks for dependencies so we mark the modules that will
      // transitively receive appcompat from another module.
      if (!modulesWithTransitiveAppCompat.contains(module) &&
          !DependencyManagementUtil.dependsOn(module, GoogleMavenArtifactId.APP_COMPAT_V7) &&
          !DependencyManagementUtil.dependsOn(module, GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7)) {
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
  private static AppCompatStyleMigration createAppCompatStyleMigration(
    @NotNull Module[] modules,
    @NotNull BiFunction<GoogleMavenArtifactId, String, AppCompatStyleMigration> factory) {
    boolean dependsOnAndroidX = false;
    AndroidVersion highest = new AndroidVersion(21); // atleast 21
    for (Module module : modules) {
      dependsOnAndroidX |= DependencyManagementUtil.dependsOn(module, GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7);
      AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(module);
      if (moduleInfo != null) {
        AndroidVersion current = moduleInfo.getBuildSdkVersion();
        if (current != null) {
          if (current.compareTo(highest) > 0) {
            highest = current;
          }
        }
      }
    }
    GoogleMavenArtifactId artifact;
    AndroidVersion finalAndroidVersion = highest;
    Predicate<GradleVersion> filter;

    if (dependsOnAndroidX) {
      artifact = GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7;
      filter = null;
    }
    else {
      artifact = GoogleMavenArtifactId.APP_COMPAT_V7;
      // Only find version that match the API level
      filter = v -> v.toString().startsWith(Integer.toString(finalAndroidVersion.getApiLevel()));
    }

    // For androidx since it it not stable, we need to also look in previews
    GradleVersion version = IdeGoogleMavenRepository.INSTANCE.findVersion(
      artifact.getMavenGroupId(), artifact.getMavenArtifactId(), filter,
      artifact.isAndroidxLibrary() ||finalAndroidVersion.isPreview());

    String defaultVersion = artifact.isAndroidxLibrary() ?
                            "1.0.0-alpha1" :
                            "26.1.0";
    return factory.apply(artifact, version == null ? defaultVersion : version.toString());
  }
}
