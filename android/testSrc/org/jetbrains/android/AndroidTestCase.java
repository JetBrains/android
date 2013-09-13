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
import com.android.sdklib.IAndroidTarget;
import com.android.utils.NullLogger;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
public abstract class AndroidTestCase extends UsefulTestCase {
  /** Environment variable or system property containing the full path to an SDK install */
  public static final String SDK_PATH_PROPERTY = "ADT_TEST_SDK_PATH";

  /** Environment variable or system property pointing to the directory name of the platform inside $sdk/platforms, e.g. "android-17" */
  private static final String PLATFORM_DIR_PROPERTY = "ADT_TEST_PLATFORM";

  protected JavaCodeInsightTestFixture myFixture;
  protected Module myModule;
  protected List<Module> myAdditionalModules;

  private boolean myCreateManifest;
  protected AndroidFacet myFacet;

  public AndroidTestCase(boolean createManifest) {
    this.myCreateManifest = createManifest;
    IdeaTestCase.initPlatformPrefix();
  }

  public AndroidTestCase() {
    this(true);
  }

  public static String getAbsoluteTestDataPath() {
    // The following code doesn't work right now that the Android
    // plugin lives in a separate place:
    //String androidHomePath = System.getProperty("android.home.path");
    //if (androidHomePath == null) {
    //  androidHomePath = new File(PathManager.getHomePath(), "android/android").getPath();
    //}
    //return PathUtil.getCanonicalPath(androidHomePath + "/testData");

    // getTestDataPath already gives the absolute path anyway:
    String path = getTestDataPath();
    assertTrue(new File(path).isAbsolute());
    return path;
  }

  public static String getTestDataPath() {
    return getAndroidPluginHome() + "/testData";
  }

  private static String getAndroidPluginHome() {
    // Now that the Android plugin is kept in a separate place, we need to look in
    // a relative position instead
    String adtPath = PathManager.getHomePath() + "/../adt/idea/android";
    if (new File(adtPath).exists()) {
      return adtPath;
    }
    return new File(PathManager.getHomePath(), "android/android").getPath();
  }

  public static String getDefaultTestSdkPath() {
    return getTestDataPath() + "/sdk1.5";
  }

  public static String getDefaultPlatformDir() {
    return "android-1.5";
  }

  protected String getTestSdkPath() {
    if (requireRecentSdk()) {
      String override = System.getProperty(SDK_PATH_PROPERTY);
      if (override != null) {
        assertTrue("Must also define " + PLATFORM_DIR_PROPERTY, System.getProperty(PLATFORM_DIR_PROPERTY) != null);
        assertTrue(override, new File(override).exists());
        return override;
      }
      override = System.getenv(SDK_PATH_PROPERTY);
      if (override != null) {
        assertTrue("Must also define " + PLATFORM_DIR_PROPERTY, System.getenv(PLATFORM_DIR_PROPERTY) != null);
        return override;
      }
      fail("This unit test requires " + SDK_PATH_PROPERTY + " and " + PLATFORM_DIR_PROPERTY + " to be defined.");
    }

    return getDefaultTestSdkPath();
  }

  protected String getPlatformDir() {
    if (requireRecentSdk()) {
      String override = System.getProperty(PLATFORM_DIR_PROPERTY);
      if (override != null) {
        return override;
      }
      override = System.getenv(PLATFORM_DIR_PROPERTY);
      if (override != null) {
        return override;
      }
      fail("This unit test requires " + SDK_PATH_PROPERTY + " and " + PLATFORM_DIR_PROPERTY + " to be defined.");
    }
    return getDefaultPlatformDir();
  }

  /** Is the bundled (incomplete) SDK install adequate or do we need to find a valid install? */
  protected boolean requireRecentSdk() {
    return false;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // this will throw an exception if we don't have a full Android SDK, so we need to do this first thing before any other setup
    String sdkPath = getTestSdkPath();

    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    final JavaModuleFixtureBuilder moduleFixtureBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    final String dirPath = myFixture.getTempDirPath() + getContentRootPath();
    final File dir = new File(dirPath);

    if (!dir.exists()) {
      assertTrue(dir.mkdirs());
    }
    tuneModule(moduleFixtureBuilder, dirPath);

    final ArrayList<MyAdditionalModuleData> modules = new ArrayList<MyAdditionalModuleData>();
    configureAdditionalModules(projectBuilder, modules);

    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    myModule = moduleFixtureBuilder.getFixture().getModule();

    // Must be done before addAndroidFacet, and must always be done, even if !myCreateManifest.
    // We will delete it at the end of setUp; this is needed when unit tests want to rewrite
    // the manifest on their own.
    createManifest();

    myFacet = addAndroidFacet(myModule, sdkPath, getPlatformDir(), isToAddSdk());
    myFixture.copyDirectoryToProject(getResDir(), "res");

    myAdditionalModules = new ArrayList<Module>();

    for (MyAdditionalModuleData data : modules) {
      final Module additionalModule = data.myModuleFixtureBuilder.getFixture().getModule();
      myAdditionalModules.add(additionalModule);
      final AndroidFacet facet = addAndroidFacet(additionalModule, sdkPath, getPlatformDir());
      facet.setLibraryProject(data.myLibrary);
      final String rootPath = getContentRootPath(data.myDirName);
      myFixture.copyDirectoryToProject("res", rootPath + "/res");
      myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML,
                                  rootPath + '/' + SdkConstants.FN_ANDROID_MANIFEST_XML);
      ModuleRootModificationUtil.addDependency(myModule, additionalModule);
    }

    if (!myCreateManifest) {
      deleteManifest();
    }
  }

  protected boolean isToAddSdk() {
    return true;
  }

  protected String getContentRootPath() {
    return "";
  }

  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
  }

  protected void addModuleWithAndroidFacet(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                           @NotNull List<MyAdditionalModuleData> modules,
                                           @NotNull String dirName,
                                           boolean library) {
    final JavaModuleFixtureBuilder moduleFixtureBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    final String moduleDirPath = myFixture.getTempDirPath() + getContentRootPath(dirName);
    new File(moduleDirPath).mkdirs();
    tuneModule(moduleFixtureBuilder, moduleDirPath);
    modules.add(new MyAdditionalModuleData(moduleFixtureBuilder, dirName, library));
  }

  protected static String getContentRootPath(@NotNull String moduleName) {
    return "/additionalModules/" + moduleName;
  }

  protected String getResDir() {
    return "res";
  }

  public static void tuneModule(JavaModuleFixtureBuilder moduleBuilder, String moduleDirPath) {
    moduleBuilder.addContentRoot(moduleDirPath);

    new File(moduleDirPath + "/src/").mkdir();
    moduleBuilder.addSourceRoot("src");

    new File(moduleDirPath + "/gen/").mkdir();
    moduleBuilder.addSourceRoot("gen");
  }

  protected void createManifest() throws IOException {
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  protected void createProjectProperties() throws IOException {
    myFixture.copyFileToProject(SdkConstants.FN_PROJECT_PROPERTIES, SdkConstants.FN_PROJECT_PROPERTIES);
  }

  protected void deleteManifest() throws IOException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        String manifestRelativePath = myFacet.getProperties().MANIFEST_FILE_RELATIVE_PATH;
        VirtualFile manifest = AndroidRootUtil.getFileByRelativeModulePath(myModule, manifestRelativePath, true);
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

  @Override
  public void tearDown() throws Exception {
    myModule = null;
    myAdditionalModules = null;
    myFixture.tearDown();
    myFixture = null;
    myFacet = null;
    super.tearDown();
  }

  public static AndroidFacet addAndroidFacet(Module module, String sdkPath, String platformDir) {
    return addAndroidFacet(module, sdkPath, platformDir, true);
  }

  public static AndroidFacet addAndroidFacet(Module module, String sdkPath, String platformDir, boolean addSdk) {
    FacetManager facetManager = FacetManager.getInstance(module);
    AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), "Android", null);

    if (addSdk) {
      addAndroidSdk(module, sdkPath, platformDir);
    }
    final ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    facetModel.addFacet(facet);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        facetModel.commit();
      }
    });
    return facet;
  }

  protected static void addAndroidSdk(Module module, String sdkPath, String platformDir) {
    Sdk androidSdk = createAndroidSdk(sdkPath, platformDir);
    ModuleRootModificationUtil.setModuleSdk(module, androidSdk);
  }

  protected static Sdk createAndroidSdk(String sdkPath, String platformDir) {
    Sdk sdk = ProjectJdkTable.getInstance().createSdk("android_test_sdk", AndroidSdkType.getInstance());
    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(sdkPath);

    VirtualFile androidJar;
    if (platformDir.equals(getDefaultPlatformDir())) {
      // Compatibility: the unit tests were using android.jar outside the sdk1.5 install;
      // we need to use that one, rather than the real one in sdk1.5, in order for the
      // tests to pass. Longer term, we should switch the unit tests over to all using
      // a valid SDK.
      String androidJarPath = sdkPath + "/../android.jar!/";
      androidJar = JarFileSystem.getInstance().findFileByPath(androidJarPath);
    } else {
      androidJar = LocalFileSystem.getInstance().findFileByPath(sdkPath + "/platforms/" + platformDir + "/android.jar");
    }
    sdkModificator.addRoot(androidJar, OrderRootType.CLASSES);

    VirtualFile resFolder = LocalFileSystem.getInstance().findFileByPath(sdkPath + "/platforms/" + platformDir + "/data/res");
    sdkModificator.addRoot(resFolder, OrderRootType.CLASSES);

    VirtualFile docsFolder = LocalFileSystem.getInstance().findFileByPath(sdkPath + "/docs/reference");
    sdkModificator.addRoot(docsFolder, JavadocOrderRootType.getInstance());

    AndroidSdkAdditionalData data = new AndroidSdkAdditionalData(sdk);
    AndroidSdkData sdkData = AndroidSdkData.parse(sdkPath, NullLogger.getLogger());
    assertNotNull(sdkData);
    IAndroidTarget target = sdkData.findTargetByName("Android 4.2"); // TODO: Get rid of this hardcoded version number
    if (target == null) {
      IAndroidTarget[] targets = sdkData.getTargets();
      for (IAndroidTarget t : targets) {
        if (t.getLocation().contains(platformDir)) {
          target = t;
          break;
        }
      }
      if (target == null && targets.length > 0) {
        target = targets[targets.length - 1];
      }
    }
    assertNotNull(target);
    data.setBuildTarget(target);
    sdkModificator.setSdkAdditionalData(data);
    sdkModificator.commitChanges();
    return sdk;
  }

  protected Project getProject() {
    return myFixture.getProject();
  }

  protected static class MyAdditionalModuleData {
    final JavaModuleFixtureBuilder myModuleFixtureBuilder;
    final String myDirName;
    final boolean myLibrary;

    private MyAdditionalModuleData(@NotNull JavaModuleFixtureBuilder moduleFixtureBuilder,
                                   @NotNull String dirName,
                                   boolean library) {
      myModuleFixtureBuilder = moduleFixtureBuilder;
      myDirName = dirName;
      myLibrary = library;
    }
  }
}
