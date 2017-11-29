/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android;

import com.android.SdkConstants;
import com.android.tools.idea.rendering.RenderSecurityManager;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.startup.AndroidCodeStyleSettingsModifier;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.ThreadTracker;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;
import com.intellij.testFramework.fixtures.impl.JavaModuleFixtureBuilderImpl;
import com.intellij.testFramework.fixtures.impl.ModuleFixtureImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.formatter.AndroidXmlCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
public abstract class AndroidTestCase extends AndroidTestBase {
  protected Module myModule;
  protected List<Module> myAdditionalModules;

  protected AndroidFacet myFacet;
  protected CodeStyleSettings mySettings;

  private List<String> myAllowedRoots = new ArrayList<>();
  private boolean myUseCustomSettings;
  private ComponentStack myApplicationComponentStack;
  private ComponentStack myProjectComponentStack;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    IdeaTestFixtureFactory.getFixtureFactory().registerFixtureBuilder(
      AndroidModuleFixtureBuilder.class, AndroidModuleFixtureBuilderImpl.class);
    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    AndroidModuleFixtureBuilder moduleFixtureBuilder = projectBuilder.addModule(AndroidModuleFixtureBuilder.class);
    initializeModuleFixtureBuilderWithSrcAndGen(moduleFixtureBuilder, myFixture.getTempDirPath());

    ArrayList<MyAdditionalModuleData> modules = new ArrayList<>();
    configureAdditionalModules(projectBuilder, modules);

    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    myModule = moduleFixtureBuilder.getFixture().getModule();

    // Must be done before addAndroidFacet, and must always be done, even if a test provides
    // its own custom manifest file. However, in that case, we will delete it shortly below.
    createManifest();

    myFacet = addAndroidFacet(myModule);

    removeFacetOn(myFixture.getProjectDisposable(), myFacet);

    LanguageLevel languageLevel = getLanguageLevel();
    if (languageLevel != null) {
      LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(myModule.getProject());
      if (extension != null) {
        extension.setLanguageLevel(languageLevel);
      }
    }

    myFixture.copyDirectoryToProject(getResDir(), "res");

    myAdditionalModules = new ArrayList<>();
    for (MyAdditionalModuleData data : modules) {
      Module additionalModule = data.myModuleFixtureBuilder.getFixture().getModule();
      myAdditionalModules.add(additionalModule);
      AndroidFacet facet = addAndroidFacet(additionalModule);
      removeFacetOn(myFixture.getProjectDisposable(), facet);
      facet.setProjectType(data.myProjectType);
      String rootPath = getAdditionalModulePath(data.myDirName);
      myFixture.copyDirectoryToProject(getResDir(), rootPath + "/res");
      myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, rootPath + '/' + SdkConstants.FN_ANDROID_MANIFEST_XML);
      if (data.myIsMainModuleDependency) {
        ModuleRootModificationUtil.addDependency(myModule, additionalModule);
      }
    }

    if (providesCustomManifest()) {
      deleteManifest();
    }

    if (RenderSecurityManager.RESTRICT_READS) {
      // Unit test class loader includes disk directories which security manager does not allow access to
      RenderSecurityManager.sEnabled = false;
    }

    ArrayList<String> allowedRoots = new ArrayList<>();
    collectAllowedRoots(allowedRoots);
    registerAllowedRoots(allowedRoots, getTestRootDisposable());
    mySettings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    AndroidCodeStyleSettingsModifier.modify(mySettings);
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(mySettings);
    myUseCustomSettings = getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS;
    getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS = true;

    // Layoutlib rendering thread will be shutdown when the app is closed so do not report it as a leak
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "Layoutlib");

    myApplicationComponentStack = new ComponentStack(ApplicationManager.getApplication());
    myProjectComponentStack = new ComponentStack(getProject());

    IdeSdks.removeJdksOn(myFixture.getProjectDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      // Finish dispatching any remaining events before shutting down everything
      UIUtil.dispatchAllInvocationEvents();

      myApplicationComponentStack.restoreComponents();
      myApplicationComponentStack = null;
      myProjectComponentStack.restoreComponents();
      myProjectComponentStack = null;
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
      myModule = null;
      myAdditionalModules = null;
      myFixture.tearDown();
      myFixture = null;
      myFacet = null;
      mySettings = null;

      getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS = myUseCustomSettings;
      if (RenderSecurityManager.RESTRICT_READS) {
        RenderSecurityManager.sEnabled = true;
      }
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * This method saves the project to disk to make sure that {@link Project#getProjectFile()} doesn't return null.
   * The implementation of {@link Project#getProjectFile()} unfortunately depends of the project (.ipr) file being
   * saved to disk. Since saving the project is a slow operation, it would be preferable to make
   * the {@link Project#getProjectFile()} method work regardless of whether the project file is saved or not.
   */
  public void makeSureThatProjectVirtualFileIsNotNull() {
    if (getProject().getProjectFile() == null) {
      ApplicationManagerEx.getApplicationEx().doNotSave(false);
      getProject().save();
      assert getProject().getProjectFile() != null;
    }
  }

  public static void initializeModuleFixtureBuilderWithSrcAndGen(AndroidModuleFixtureBuilder moduleFixtureBuilder, String moduleRoot) {
    moduleFixtureBuilder.setModuleRoot(moduleRoot);
    moduleFixtureBuilder.addContentRoot(moduleRoot);

    //noinspection ResultOfMethodCallIgnored
    new File(moduleRoot + "/src/").mkdir();
    moduleFixtureBuilder.addSourceRoot("src");

    //noinspection ResultOfMethodCallIgnored
    new File(moduleRoot + "/gen/").mkdir();
    moduleFixtureBuilder.addSourceRoot("gen");
  }

  /**
   * Returns the path that any additional modules registered by
   * {@link #configureAdditionalModules(TestFixtureBuilder, List)} or
   * {@link #addModuleWithAndroidFacet(TestFixtureBuilder, List, String, int, boolean)} are
   * installed into.
   */
  protected static String getAdditionalModulePath(@NotNull String moduleName) {
    return "/additionalModules/" + moduleName;
  }

  @Nullable
  protected Module getAdditionalModuleByName(@NotNull String moduleName) {
    return myAdditionalModules.stream()
      .filter(module -> moduleName.equals(module.getName()))
      .findFirst()
      .orElse(null);
  }

  /**
   * Indicates whether this class provides its own {@code AndroidManifest.xml} for its tests. If
   * {@code true}, then {@link #setUp()} calls {@link #deleteManifest()} before finishing.
   */
  protected boolean providesCustomManifest() {
    return false;
  }

  /**
   * Get the "res" directory for this SDK. Children classes can override this if they need to
   * provide a custom "res" location for tests.
   */
  protected String getResDir() {
    return "res";
  }

  /**
   * Defines the project level to set for the test project, or null to get the default language
   * level associated with the test project.
   */
  @Nullable
  protected LanguageLevel getLanguageLevel() {
    return null;
  }

  protected static AndroidXmlCodeStyleSettings getAndroidCodeStyleSettings() {
    return AndroidXmlCodeStyleSettings.getInstance(CodeStyleSchemes.getInstance().getDefaultScheme().getCodeStyleSettings());
  }

  /**
   * Hook point for child test classes to register directories that can be safely accessed by all
   * of its tests.
   *
   * @see {@link VfsRootAccess}
   */
  protected void collectAllowedRoots(List<String> roots) throws IOException {
  }

  private void registerAllowedRoots(List<String> roots, @NotNull Disposable disposable) {
    List<String> newRoots = new ArrayList<>(roots);
    newRoots.removeAll(myAllowedRoots);

    String[] newRootsArray = ArrayUtil.toStringArray(newRoots);
    VfsRootAccess.allowRootAccess(newRootsArray);
    myAllowedRoots.addAll(newRoots);

    Disposer.register(disposable, () -> {
      VfsRootAccess.disallowRootAccess(newRootsArray);
      myAllowedRoots.removeAll(newRoots);
      myAllowedRoots = null;
    });
  }

  public static AndroidFacet addAndroidFacet(Module module) {
    return addAndroidFacet(module, true);
  }

  private static AndroidFacet addAndroidFacet(Module module, boolean attachSdk) {
    Sdk sdk;
    if (attachSdk) {
      sdk = addLatestAndroidSdk(module);
    }
    else {
      sdk = null;
    }
    AndroidFacetType type = AndroidFacet.getFacetType();
    String facetName = "Android";
    AndroidFacet facet = addFacet(module, type, facetName);
    return facet;
  }

  @NotNull
  public static <T extends Facet> T addFacet(Module module, FacetType<T, ? extends FacetConfiguration> type, String facetName) {
    FacetManager facetManager = FacetManager.getInstance(module);
    T facet = facetManager.createFacet(type, facetName, null);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    facetModel.addFacet(facet);
    ApplicationManager.getApplication().runWriteAction(facetModel::commit);
    if (sdk != null) {
      Disposer.register(facet, ()-> WriteAction.run(()->ProjectJdkTable.getInstance().removeJdk(sdk)));
    }
    return facet;
  }

  protected void configureAdditionalModules(
    @NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder, @NotNull List<MyAdditionalModuleData> modules) {
  }

  protected final void addModuleWithAndroidFacet(
    @NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
    @NotNull List<MyAdditionalModuleData> modules,
    @NotNull String moduleName,
    int projectType) {
    // By default, created module is declared as a main module's dependency
    addModuleWithAndroidFacet(projectBuilder, modules, moduleName, projectType, true);
  }

  protected final void addModuleWithAndroidFacet(
    @NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
    @NotNull List<MyAdditionalModuleData> modules,
    @NotNull String moduleName,
    int projectType,
    boolean isMainModuleDependency) {
    AndroidModuleFixtureBuilder moduleFixtureBuilder = projectBuilder.addModule(AndroidModuleFixtureBuilder.class);
    moduleFixtureBuilder.setModuleName(moduleName);
    // A module named "lib" goes under additionalModules/lib/lib.iml
    initializeModuleFixtureBuilderWithSrcAndGen(
      moduleFixtureBuilder, myFixture.getTempDirPath() + getAdditionalModulePath(moduleName));
    modules.add(new MyAdditionalModuleData(moduleFixtureBuilder, moduleName, projectType, isMainModuleDependency));
  }

  protected final void createManifest() throws IOException {
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  protected final void createProjectProperties() throws IOException {
    myFixture.copyFileToProject(SdkConstants.FN_PROJECT_PROPERTIES, SdkConstants.FN_PROJECT_PROPERTIES);
  }

  protected final void deleteManifest() throws IOException {
    deleteManifest(myModule);
  }

  protected final void deleteManifest(final Module module) throws IOException {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertNotNull(facet);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        String manifestRelativePath = facet.getProperties().MANIFEST_FILE_RELATIVE_PATH;
        VirtualFile manifest = AndroidRootUtil.getFileByRelativeModulePath(module, manifestRelativePath, true);
        if (manifest != null) {
          try {
            manifest.delete(this);
          }
          catch (IOException e) {
            fail("Could not delete default manifest");
          }
        }
      }
    });
  }

  protected final Map<RefEntity, CommonProblemDescriptor[]> doGlobalInspectionTest(
    @NotNull GlobalInspectionTool inspection, @NotNull String globalTestDir, @NotNull AnalysisScope scope) {
    return doGlobalInspectionTest(new GlobalInspectionToolWrapper(inspection), globalTestDir, scope);
  }

  /**
   * Given an inspection and a path to a directory that contains an "expected.xml" file, run the
   * inspection on the current test project and verify that its output matches that of the
   * expected file.
   */
  protected final Map<RefEntity, CommonProblemDescriptor[]> doGlobalInspectionTest(
    @NotNull GlobalInspectionToolWrapper wrapper, @NotNull String globalTestDir, @NotNull AnalysisScope scope) {
    myFixture.enableInspections(wrapper.getTool());

    scope.invalidate();

    InspectionManagerEx inspectionManager = (InspectionManagerEx)InspectionManager.getInstance(getProject());
    GlobalInspectionContextForTests globalContext =
      CodeInsightTestFixtureImpl.createGlobalContextForTool(scope, getProject(), inspectionManager, wrapper);

    InspectionTestUtil.runTool(wrapper, scope, globalContext);
    InspectionTestUtil.compareToolResults(globalContext, wrapper, false, getTestDataPath() + globalTestDir);

    return globalContext.getPresentation(wrapper).getProblemElements();
  }

  public <T> void registerApplicationComponent(@NotNull Class<T> key, @NotNull T instance) {
    myApplicationComponentStack.registerComponentInstance(key, instance);
  }

  public <T> void registerApplicationComponentImplementation(@NotNull Class<T> key, @NotNull T instance) {
    myApplicationComponentStack.registerComponentImplementation(key, instance);
  }

  public <T> void registerProjectComponent(@NotNull Class<T> key, @NotNull T instance) {
    myProjectComponentStack.registerComponentInstance(key, instance);
  }

  public <T> void registerProjectComponentImplementation(@NotNull Class<T> key, @NotNull T instance) {
    myProjectComponentStack.registerComponentImplementation(key, instance);
  }

  protected final static class MyAdditionalModuleData {
    final AndroidModuleFixtureBuilder myModuleFixtureBuilder;
    final String myDirName;
    final int myProjectType;
    final boolean myIsMainModuleDependency;

    private MyAdditionalModuleData(
      @NotNull AndroidModuleFixtureBuilder moduleFixtureBuilder, @NotNull String dirName, int projectType, boolean isMainModuleDependency) {
      myModuleFixtureBuilder = moduleFixtureBuilder;
      myDirName = dirName;
      myProjectType = projectType;
      myIsMainModuleDependency = isMainModuleDependency;
    }
  }

  public interface AndroidModuleFixtureBuilder<T extends ModuleFixture> extends JavaModuleFixtureBuilder<T> {
    void setModuleRoot(@NotNull String moduleRoot);

    void setModuleName(@NotNull String moduleName);
  }

  public static class AndroidModuleFixtureBuilderImpl extends JavaModuleFixtureBuilderImpl<ModuleFixtureImpl>
    implements AndroidModuleFixtureBuilder<ModuleFixtureImpl> {

    private File myModuleRoot;
    private String myModuleName;

    public AndroidModuleFixtureBuilderImpl(TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
      super(fixtureBuilder);
    }

    @Override
    public void setModuleRoot(@NotNull String moduleRoot) {
      myModuleRoot = new File(moduleRoot);
      if (!myModuleRoot.exists()) {
        Verify.verify(myModuleRoot.mkdirs());
      }
    }

    @Override
    public void setModuleName(@NotNull String moduleName) {
      Preconditions.checkArgument(!"app".equals(moduleName), "'app' is reserved for main module");
      myModuleName = moduleName;
    }

    @Override
    protected Module createModule() {
      Project project = myFixtureBuilder.getFixture().getProject();
      Verify.verifyNotNull(project);
      Preconditions.checkNotNull(myModuleRoot);

      // the (unnamed) root module will be app.iml
      String moduleFilePath =
        myModuleRoot + (myModuleName == null ? "/app" : "/" + myModuleName) + ModuleFileType.DOT_DEFAULT_EXTENSION;
      return ModuleManager.getInstance(project).newModule(moduleFilePath, ModuleTypeId.JAVA_MODULE);
    }

    @Override
    protected ModuleFixtureImpl instantiateFixture() {
      return new ModuleFixtureImpl(this);
    }
  }

  public static void removeFacetOn(@NotNull Disposable disposable, @NotNull Facet facet) {
    Disposer.register(disposable, () -> WriteAction.run(() -> {
      Module module = facet.getModule();
      if (!module.isDisposed()) {
        FacetManager facetManager = FacetManager.getInstance(module);
        ModifiableFacetModel model = facetManager.createModifiableModel();
        model.removeFacet(facet);
        model.commit();
      }
    }));
  }
}
