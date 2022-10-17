// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.android;

import static com.android.tools.idea.testing.ThreadingAgentTestUtilKt.maybeCheckThreadingAgentIsRunning;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

import com.android.SdkConstants;
import com.android.testutils.TestUtils;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.TestAndroidModel;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.AndroidTestUtils;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.idea.testing.Sdks;
import com.android.tools.idea.testing.ThreadingCheckerHookTestImpl;
import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampoline;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.intellij.application.options.CodeStyle;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ThreadTracker;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.ModuleFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.JavaModuleFixtureBuilderImpl;
import com.intellij.testFramework.fixtures.impl.ModuleFixtureImpl;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.UIUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.formatter.AndroidJavaPredefinedCodeStyle;
import org.jetbrains.android.formatter.AndroidXmlCodeStyleSettings;
import org.jetbrains.android.formatter.AndroidXmlPredefinedCodeStyle;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * NOTE: If you are writing a new test, consider using JUnit4 with
 * {@link com.android.tools.idea.testing.AndroidProjectRule} instead. This allows you to use
 * features introduced in JUnit4 (such as parameterization) while also providing a more
 * compositional approach - instead of your test class inheriting dozens and dozens of methods you
 * might not be familiar with, those methods will be constrained to the rule.
 */
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
  private IdeComponents myIdeComponents;
  private ThreadingCheckerHookTestImpl threadingCheckerHook;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    IdeaTestFixtureFactory.getFixtureFactory().registerFixtureBuilder(
      AndroidModuleFixtureBuilder.class, AndroidModuleFixtureBuilderImpl.class);
    AndroidTempDirTestFixture tempDirFixture = new AndroidTempDirTestFixture(getName());
    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory()
        .createFixtureBuilder(getName(), tempDirFixture.getProjectDir().getParentFile().toPath(), true);
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture(), tempDirFixture);
    AndroidModuleFixtureBuilder moduleFixtureBuilder = projectBuilder.addModule(AndroidModuleFixtureBuilder.class);
    initializeModuleFixtureBuilderWithSrcAndGen(moduleFixtureBuilder, myFixture.getTempDirPath());
    setUpThreadingChecks();

    ArrayList<MyAdditionalModuleData> modules = new ArrayList<>();
    configureAdditionalModules(projectBuilder, modules);

    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    myModule = moduleFixtureBuilder.getFixture().getModule();

    // Must be done before addAndroidFacet, and must always be done, even if a test provides
    // its own custom manifest file. However, in that case, we will delete it shortly below.
    createManifest();

    Path jdkPath = TestUtils.getJava11Jdk();
    WriteAction.runAndWait(() -> {
      cleanJdkTable();
      setupJdk(jdkPath);
    });
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
      facet.getConfiguration().setProjectType(data.myProjectType);
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

    ArrayList<String> allowedRoots = new ArrayList<>();
    collectAllowedRoots(allowedRoots);
    registerAllowedRoots(allowedRoots, getTestRootDisposable());
    mySettings = CodeStyle.createTestSettings(CodeStyleSettingsManager.getSettings(getProject()));
    // Note: we apply the Android Studio code style so that tests running as the Android plugin in IDEA behave the same.
    applyAndroidCodeStyleSettings(mySettings);
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(mySettings);
    myUseCustomSettings = getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS;
    getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS = true;

    // Layoutlib rendering thread will be shutdown when the app is closed so do not report it as a leak
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "Layoutlib");
    IdeSdks.removeJdksOn(myFixture.getProjectDisposable());

    myApplicationComponentStack = new ComponentStack(ApplicationManager.getApplication());
    myProjectComponentStack = new ComponentStack(getProject());
    myIdeComponents = new IdeComponents(myFixture);

    IdeSdks.removeJdksOn(myFixture.getProjectDisposable());

    ProjectTypeService.setProjectType(getProject(), new ProjectType("Android"));
  }

  private void setupJdk(Path path) {
    assert path.isAbsolute() : "JDK path should be an absolute path: " + path;

    VfsRootAccess.allowRootAccess(getTestRootDisposable(), path.toString());
    @Nullable Sdk addedSdk = SdkConfigurationUtil.createAndAddSDK(path.toString(), JavaSdk.getInstance());
    if (addedSdk != null) {
      Disposer.register(getTestRootDisposable(), () -> {
        WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().removeJdk(addedSdk));
      });
    }
  }

  private void cleanJdkTable() {
    for (Sdk jdk : ProjectJdkTable.getInstance().getAllJdks()) {
      ProjectJdkTable.getInstance().removeJdk(jdk);
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      // Finish dispatching any remaining events before shutting down everything
      UIUtil.dispatchAllInvocationEvents();
      tearDownThreadingChecks();

      myApplicationComponentStack.restore();
      myApplicationComponentStack = null;
      myProjectComponentStack.restore();
      myProjectComponentStack = null;
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
      myModule = null;
      myAdditionalModules = null;
      myFacet = null;
      mySettings = null;

      getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS = myUseCustomSettings;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      try {
        myFixture.tearDown();
      }
      catch (Throwable e) {
        addSuppressedException(e);
      }
      finally {
        super.tearDown();
      }
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
      PlatformTestUtil.saveProject(getProject());
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
    // Higher language levels trigger JavaPlatformModuleSystem checks which fail for our light PSI classes. For now set the language level
    // to what real AS actually uses.
    // TODO(b/110679859): figure out how to stop JavaPlatformModuleSystem from thinking the light classes are not accessible.
    return LanguageLevel.JDK_1_8;
  }

  private void setUpThreadingChecks() {
    if (!shouldPerfomThreadingChecks()) {
      return;
    }
    maybeCheckThreadingAgentIsRunning();
    threadingCheckerHook = new ThreadingCheckerHookTestImpl();
    ThreadingCheckerTrampoline.installHook(threadingCheckerHook);
  }

  private void tearDownThreadingChecks() {
    if (threadingCheckerHook == null) {
      return;
    }
    ThreadingCheckerTrampoline.removeHook(threadingCheckerHook);
    if (threadingCheckerHook.getHasThreadingViolation()) {
      addSuppressedException(new RuntimeException(threadingCheckerHook.getErrorMessage()));
    }
  }

  protected boolean shouldPerfomThreadingChecks() {
    return false;
  }

  protected static AndroidXmlCodeStyleSettings getAndroidCodeStyleSettings() {
    return AndroidXmlCodeStyleSettings.getInstance(CodeStyleSchemes.getInstance().getDefaultScheme().getCodeStyleSettings());
  }

  public static void applyAndroidCodeStyleSettings(CodeStyleSettings settings) {
    new AndroidJavaPredefinedCodeStyle().apply(settings);
    new AndroidXmlPredefinedCodeStyle().apply(settings);
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

    String[] newRootsArray = ArrayUtilRt.toStringArray(newRoots);
    VfsRootAccess.allowRootAccess(disposable, newRootsArray);
    myAllowedRoots.addAll(newRoots);

    Disposer.register(disposable, () -> {
      myAllowedRoots.removeAll(newRoots);
      myAllowedRoots = null;
    });
  }

  public static AndroidFacet addAndroidFacet(Module module) {
    return addAndroidFacet(module, true);
  }

  public static AndroidFacet addAndroidFacet(Module module, boolean attachSdk) {
    AndroidFacetType type = AndroidFacet.getFacetType();
    String facetName = "Android";
    AndroidFacet facet = addFacet(module, type, facetName);
    if (attachSdk) {
      Sdks.addLatestAndroidSdk(facet, module);
    }
    return facet;
  }

  @NotNull
  public static <T extends Facet> T addFacet(Module module, FacetType<T, ? extends FacetConfiguration> type, String facetName) {
    FacetManager facetManager = FacetManager.getInstance(module);
    T facet = facetManager.createFacet(type, facetName, null);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    facetModel.addFacet(facet);
    ApplicationManager.getApplication().runWriteAction(facetModel::commit);
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

  /**
   * Enables namespacing in the main module and sets the app namespace according to the given package name.
   */
  protected void enableNamespacing(@NotNull String appPackageName) {
    enableNamespacing(myFacet, appPackageName);
  }

  /**
   * Enables namespacing in the given module and sets the app namespace according to the given package name.
   */
  protected void enableNamespacing(@NotNull AndroidFacet facet, @NotNull String appPackageName) {
    AndroidModel.set(facet, TestAndroidModel.namespaced(facet));
    runWriteCommandAction(getProject(), () -> Manifest.getMainManifest(facet).getPackage().setValue(appPackageName));
    LocalResourceManager.getInstance(facet.getModule()).invalidateAttributeDefinitions();
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
        VirtualFile manifest = AndroidRootUtil.getPrimaryManifestFile(facet);
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

  public <T> void registerApplicationComponent(@NotNull Class<T> key, @NotNull T instance) {
    myApplicationComponentStack.registerComponentInstance(key, instance);
  }

  public <T> void registerApplicationService(@NotNull Class<T> key, @NotNull T instance) {
    myApplicationComponentStack.registerServiceInstance(key, instance);
  }

  public <T> void registerProjectComponent(@NotNull Class<T> key, @NotNull T instance) {
    myProjectComponentStack.registerComponentInstance(key, instance);
  }

  public <T> void registerProjectService(@NotNull Class<T> key, @NotNull T instance) {
    myProjectComponentStack.registerServiceInstance(key, instance);
  }

  public <T> void replaceApplicationService(@NotNull Class<T> serviceType, @NotNull T newServiceInstance) {
    myIdeComponents.replaceApplicationService(serviceType, newServiceInstance);
  }

  public <T> void replaceProjectService(@NotNull Class<T> serviceType, @NotNull T newServiceInstance) {
    myIdeComponents.replaceProjectService(serviceType, newServiceInstance);
  }

  /** Waits 2 seconds for the app resource repository to finish currently pending updates. */
  protected void waitForResourceRepositoryUpdates() throws InterruptedException, TimeoutException {
    waitForResourceRepositoryUpdates(2, TimeUnit.SECONDS);
  }

  protected void waitForResourceRepositoryUpdates(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
    AndroidTestUtils.waitForResourceRepositoryUpdates(myFacet, timeout, unit);
  }

  protected final static class MyAdditionalModuleData {
    final AndroidModuleFixtureBuilder<?> myModuleFixtureBuilder;
    final String myDirName;
    final int myProjectType;
    final boolean myIsMainModuleDependency;

    private MyAdditionalModuleData(
      @NotNull AndroidModuleFixtureBuilder<?> moduleFixtureBuilder, @NotNull String dirName, int projectType, boolean isMainModuleDependency) {
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
      JavaCodeInsightFixtureAdtTestCase.addJdk(this);
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

    @NotNull
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

    @NotNull
    @Override
    protected ModuleFixtureImpl instantiateFixture() {
      return new ModuleFixtureImpl(this);
    }
  }

  public static void removeFacetOn(@NotNull Disposable disposable, @NotNull Facet<?> facet) {
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
