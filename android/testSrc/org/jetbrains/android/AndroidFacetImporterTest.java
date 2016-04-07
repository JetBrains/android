/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android;

import com.android.sdklib.IAndroidTarget;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.AndroidDexCompilerConfiguration;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.maven.AndroidFacetImporterBase;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.idea.maven.importing.FacetImporterTestCase;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.jps.android.model.impl.AndroidImportableProperty;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.util.Arrays;

import static org.jetbrains.android.sdk.AndroidSdkUtils.getAndroidSdkAdditionalData;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFacetImporterTest extends FacetImporterTestCase<AndroidFacet> {

  private Sdk myJdk;

  @Override
  protected FacetTypeId<AndroidFacet> getFacetTypeId() {
    return AndroidFacet.ID;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myJdk = IdeaTestUtil.getMockJdk17();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(myJdk);
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    final ProjectJdkTable table = ProjectJdkTable.getInstance();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (Sdk sdk : table.getAllJdks()) {
          table.removeJdk(sdk);
        }
      }
    });
  }

  // Temporarily disable this as this seems to fail a lot when run on Jenkins
  public void temporarily_disabled_testNoSdk() throws Exception {
    importProject(getPomContent("apk", "module", ""));
    assertModules("module");
    assertNull(ModuleRootManager.getInstance(getModule("module")).getSdk());
  }

  public void testNewSdk1() throws Exception {
    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      final VirtualFile pom1 = createModulePom("module1", getPomContent("apk", "module1", ""));
      final VirtualFile pom2 = createModulePom("module2", getPomContent("apk", "module2", ""));
      importProjects(pom1, pom2);

      assertModules("module1", "module2");
      final Sdk sdk1 = ModuleRootManager.getInstance(getModule("module1")).getSdk();
      final Sdk sdk2 = ModuleRootManager.getInstance(getModule("module2")).getSdk();
      assertEquals(sdk1, sdk2);
      checkSdk(sdk1);
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  public void testNewSdk2() throws Exception {
    final Sdk sdk = AndroidTestCase.createAndroidSdk(AndroidTestCase.getDefaultTestSdkPath(), AndroidTestCase.getDefaultPlatformDir());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(sdk);
      }
    });

    importProject(getPomContent("apk", "module", ""));
    assertModules("module");
    final Module module = getModule("module");
    final Sdk mavenSdk = ModuleRootManager.getInstance(module).getSdk();
    assertFalse(sdk.equals(mavenSdk));
    checkSdk(mavenSdk);

    assert mavenSdk != null;
    final AndroidSdkAdditionalData mavenSdkData = getAndroidSdkAdditionalData(mavenSdk);
    @SuppressWarnings("ConstantConditions")
    final AndroidSdkData sdkData = mavenSdkData.getAndroidPlatform().getSdkData();
    final IAndroidTarget[] targets = sdkData.getTargets();
    IAndroidTarget lowTarget = null;

    for (IAndroidTarget target : targets) {
      if (target.getVersion().getApiLevel() == 2) {
        lowTarget = target;
      }
    }
    assertNotNull(lowTarget);
    mavenSdkData.setBuildTarget(lowTarget);
    importProject();
    final Sdk mavenSdk2 = ModuleRootManager.getInstance(module).getSdk();
    assertNotSame(mavenSdk, mavenSdk2);
    checkSdk(mavenSdk2, "Maven Android API 17 Platform (1)", "android-17");
  }

  public void testNewSdk3() throws Exception {
    final Sdk sdk = AndroidTestCase.createAndroidSdk(AndroidTestCase.getDefaultTestSdkPath(), AndroidTestCase.getDefaultPlatformDir());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(sdk);
      }
    });

    importProject("<groupId>test</groupId>" +
                  "<artifactId>" + "module" + "</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>apk</packaging>" +
                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>com.jayway.maven.plugins.android.generation2</groupId>" +
                  "      <artifactId>android-maven-plugin</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("module");
    final Module module = getModule("module");
    final Sdk mavenSdk = ModuleRootManager.getInstance(module).getSdk();
    assertNotNull(mavenSdk);
    assertFalse(sdk.equals(mavenSdk));
    checkSdk(mavenSdk);
    setSdk(module, myJdk);
    importProject();
    assertEquals(mavenSdk, ModuleRootManager.getInstance(module).getSdk());
    setSdk(module, sdk);
    importProject();
    assertEquals(mavenSdk, ModuleRootManager.getInstance(module).getSdk());
    final AndroidSdkAdditionalData mavenSdkData = getAndroidSdkAdditionalData(mavenSdk);
    @SuppressWarnings("ConstantConditions")
    final AndroidSdkData sdkData = mavenSdkData.getAndroidPlatform().getSdkData();
    final IAndroidTarget[] targets = sdkData.getTargets();
    IAndroidTarget lowTarget = null;

    for (IAndroidTarget target : targets) {
      if (target.getVersion().getApiLevel() == 2) {
        lowTarget = target;
      }
    }
    assertNotNull(lowTarget);
    mavenSdkData.setBuildTarget(lowTarget);
    importProject();
    assertEquals(mavenSdk, ModuleRootManager.getInstance(module).getSdk());
  }

  public void testNewSdk4() throws Exception {
    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      importProject("<groupId>test</groupId>" +
                    "<artifactId>module</artifactId>" +
                    "<version>1</version>" +
                    "<packaging>apk</packaging>" +
                    "<build>" +
                    "  <plugins>" +
                    "    <plugin>" +
                    "      <groupId>com.jayway.maven.plugins.android.generation2</groupId>" +
                    "      <artifactId>android-maven-plugin</artifactId>" +
                    "    </plugin>" +
                    "  </plugins>" +
                    "</build>" +
                    "<properties>" +
                    "  <android.sdk.platform>2</android.sdk.platform>" +
                    "</properties>");

      assertModules("module");
      final Sdk sdk = ModuleRootManager.getInstance(getModule("module")).getSdk();
      checkSdk(sdk, "Maven Android API 2 Platform", "android-2");
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  public void testNewSdk5() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>module</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>apk</packaging>" +
                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>com.jayway.maven.plugins.android.generation2</groupId>" +
                  "      <artifactId>android-maven-plugin</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>" +
                  "<properties>" +
                  "  <android.sdk.platform>2</android.sdk.platform>" +
                  "  <android.sdk.path>" + AndroidTestCase.getDefaultTestSdkPath() + "</android.sdk.path>" +
                  "</properties>");

    assertModules("module");
    final Sdk sdk = ModuleRootManager.getInstance(getModule("module")).getSdk();
    checkSdk(sdk, "Maven Android API 2 Platform", "android-2");
  }

  private static void setSdk(Module module, Sdk sdk) {
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    modifiableModel.setSdk(sdk);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        modifiableModel.commit();
      }
    });
  }

  public void testFacetProperties1() throws Exception {
    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      importProject(getPomContent("apk", "module", ""));

      assertModules("module");
      final Module module = getModule("module");
      checkSdk(ModuleRootManager.getInstance(module).getSdk());
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      assertNotNull(facet);
      final JpsAndroidModuleProperties properties = facet.getProperties();
      assertEquals(false, properties.LIBRARY_PROJECT);
      assertEquals("/res", properties.RES_FOLDER_RELATIVE_PATH);
      assertEquals(false, properties.USE_CUSTOM_APK_RESOURCE_FOLDER);
      assertEquals("/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);
      assertEquals(false, properties.USE_CUSTOM_COMPILER_MANIFEST);
      assertEquals("", properties.CUSTOM_COMPILER_MANIFEST);
      assertEquals("/libs", properties.LIBS_FOLDER_RELATIVE_PATH);
      assertEquals("/assets", properties.ASSETS_FOLDER_RELATIVE_PATH);
      assertEquals("/target/generated-sources/aidl", properties.GEN_FOLDER_RELATIVE_PATH_AIDL);
      assertEquals("/target/generated-sources/r", properties.GEN_FOLDER_RELATIVE_PATH_APT);
      assertEquals(false, properties.ENABLE_MANIFEST_MERGING);
      assertFalse(AndroidDexCompilerConfiguration.getInstance(myProject).CORE_LIBRARY);
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  public void testFacetProperties2() throws Exception {
    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      importProject(getPomContent(
        "apklib", "module",
        "<mergeManifests>true</mergeManifests>" +
        "<androidManifestFile>${project.build.directory}/manifest/AndroidManifest.xml</androidManifestFile>" +
        "<resourceDirectory>${project.build.directory}/resources</resourceDirectory>" +
        "<assetsDirectory>${project.build.directory}/assets</assetsDirectory>" +
        "<nativeLibrariesDirectory>${project.build.directory}/nativeLibs</nativeLibrariesDirectory>" +
        "<dexCoreLibrary>true</dexCoreLibrary>"));

      assertModules("module");
      final Module module = getModule("module");
      checkSdk(ModuleRootManager.getInstance(module).getSdk());
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      assertNotNull(facet);
      final JpsAndroidModuleProperties properties = facet.getProperties();
      assertEquals(true, properties.LIBRARY_PROJECT);
      assertEquals("/target/resources", properties.RES_FOLDER_RELATIVE_PATH);
      assertEquals(false, properties.USE_CUSTOM_APK_RESOURCE_FOLDER);
      assertEquals("/target/manifest/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);
      assertEquals(false, properties.USE_CUSTOM_COMPILER_MANIFEST);
      assertEquals("", properties.CUSTOM_COMPILER_MANIFEST);
      assertEquals("/target/nativeLibs", properties.LIBS_FOLDER_RELATIVE_PATH);
      assertEquals("/target/assets", properties.ASSETS_FOLDER_RELATIVE_PATH);
      assertEquals("/target/generated-sources/aidl", properties.GEN_FOLDER_RELATIVE_PATH_AIDL);
      assertEquals("/target/generated-sources/r", properties.GEN_FOLDER_RELATIVE_PATH_APT);
      assertEquals(true, properties.ENABLE_MANIFEST_MERGING);
      assertTrue(AndroidDexCompilerConfiguration.getInstance(myProject).CORE_LIBRARY);
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  public void testFacetProperties3() throws Exception {
    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      final String projectRootPath = myProjectRoot.getPath();

      // need to have at least one resource file matched to filtered resource pattern
      final File layoutDir = new File(projectRootPath, "res/layout");
      assertTrue(layoutDir.mkdirs());
      assertTrue(new File(layoutDir, "main.xml").createNewFile());

      // need for existing manifest file
      assertTrue(new File(projectRootPath, "AndroidManifest.xml").createNewFile());

      importProject(getPomContent(
        "apk", "module",
        "<androidManifestFile>${project.build.directory}/filtered-manifest/AndroidManifest.xml</androidManifestFile>" +
        "<resourceDirectory>${project.build.directory}/filtered-res</resourceDirectory>",

        "<plugin>" +
        "  <artifactId>maven-resources-plugin</artifactId>" +
        "  <executions>" +
        "    <execution>" +
        "      <phase>initialize</phase>" +
        "      <goals>" +
        "        <goal>resources</goal>" +
        "      </goals>" +
        "    </execution>" +
        "  </executions>" +
        "</plugin>",

        "<resources>" +
        "  <resource>" +
        "    <directory>${project.basedir}</directory>" +
        "    <filtering>true</filtering>" +
        "    <targetPath>${project.build.directory}/filtered-manifest</targetPath>" +
        "    <includes>" +
        "      <include>AndroidManifest.xml</include>" +
        "    </includes>" +
        "  </resource>" +
        "  <resource>" +
        "    <directory>${project.basedir}/res</directory>" +
        "    <filtering>true</filtering>" +
        "    <targetPath>${project.build.directory}/filtered-res</targetPath>" +
        "    <includes>" +
        "      <include>**/*.xml</include>" +
        "    </includes>" +
        "  </resource>" +
        "  <resource>" +
        "    <directory>${project.basedir}/res</directory>" +
        "    <filtering>false</filtering>" +
        "    <targetPath>${project.build.directory}/filtered-res</targetPath>" +
        "    <excludes>" +
        "      <exclude>**/*.xml</exclude>" +
        "    </excludes>" +
        "  </resource>" +
        "</resources>"));


      assertModules("module");
      final Module module = getModule("module");
      checkSdk(ModuleRootManager.getInstance(module).getSdk());
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      assertNotNull(facet);
      final JpsAndroidModuleProperties properties = facet.getProperties();
      assertEquals("/res", properties.RES_FOLDER_RELATIVE_PATH);
      assertEquals(true, properties.USE_CUSTOM_APK_RESOURCE_FOLDER);
      assertEquals("/target/filtered-res", properties.CUSTOM_APK_RESOURCE_FOLDER);
      assertEquals("/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);
      assertEquals(true, properties.USE_CUSTOM_COMPILER_MANIFEST);
      assertEquals("/target/filtered-manifest/AndroidManifest.xml", properties.CUSTOM_COMPILER_MANIFEST);

      properties.myNotImportedProperties.add(AndroidImportableProperty.MANIFEST_FILE_PATH);
      properties.myNotImportedProperties.add(AndroidImportableProperty.RESOURCES_DIR_PATH);

      importProject(getPomContent(
        "apk", "module",
        "<androidManifestFile>${project.build.directory}/filtered-manifest1/AndroidManifest.xml</androidManifestFile>" +
        "<resourceDirectory>${project.build.directory}/filtered-res1</resourceDirectory>",

        "<plugin>" +
        "  <artifactId>maven-resources-plugin</artifactId>" +
        "  <executions>" +
        "    <execution>" +
        "      <phase>initialize</phase>" +
        "      <goals>" +
        "        <goal>resources</goal>" +
        "      </goals>" +
        "    </execution>" +
        "  </executions>" +
        "</plugin>",

        "<resources>" +
        "  <resource>" +
        "    <directory>${project.basedir}</directory>" +
        "    <filtering>true</filtering>" +
        "    <targetPath>${project.build.directory}/filtered-manifest1</targetPath>" +
        "    <includes>" +
        "      <include>AndroidManifest.xml</include>" +
        "    </includes>" +
        "  </resource>" +
        "  <resource>" +
        "    <directory>${project.basedir}/res</directory>" +
        "    <filtering>true</filtering>" +
        "    <targetPath>${project.build.directory}/filtered-res1</targetPath>" +
        "    <includes>" +
        "      <include>**/*.xml</include>" +
        "    </includes>" +
        "  </resource>" +
        "  <resource>" +
        "    <directory>${project.basedir}/res</directory>" +
        "    <filtering>false</filtering>" +
        "    <targetPath>${project.build.directory}/filtered-res1</targetPath>" +
        "    <excludes>" +
        "      <exclude>**/*.xml</exclude>" +
        "    </excludes>" +
        "  </resource>" +
        "</resources>"));

      assertEquals("/res", properties.RES_FOLDER_RELATIVE_PATH);
      assertEquals(true, properties.USE_CUSTOM_APK_RESOURCE_FOLDER);
      assertEquals("/target/filtered-res", properties.CUSTOM_APK_RESOURCE_FOLDER);
      assertEquals("/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);
      assertEquals(true, properties.USE_CUSTOM_COMPILER_MANIFEST);
      assertEquals("/target/filtered-manifest/AndroidManifest.xml", properties.CUSTOM_COMPILER_MANIFEST);

      properties.myNotImportedProperties.clear();
      importProject();

      assertEquals("/res", properties.RES_FOLDER_RELATIVE_PATH);
      assertEquals(true, properties.USE_CUSTOM_APK_RESOURCE_FOLDER);
      assertEquals("/target/filtered-res1", properties.CUSTOM_APK_RESOURCE_FOLDER);
      assertEquals("/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);
      assertEquals(true, properties.USE_CUSTOM_COMPILER_MANIFEST);
      assertEquals("/target/filtered-manifest1/AndroidManifest.xml", properties.CUSTOM_COMPILER_MANIFEST);
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  public void testExternalAar() throws Exception {
    setRepositoryPath(new File(myDir, "__repo").getPath());
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myaar"), new File(getRepositoryPath(), "com/myaar/1.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myjar"), new File(getRepositoryPath(), "com/myjar/1.0"));

    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      importProject(getPomContent("apk", "module", "") +
                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>com</groupId>\n" +
                    "    <artifactId>myaar</artifactId>\n" +
                    "    <version>1.0</version>\n" +
                    "    <type>aar</type>" +
                    "  </dependency>" +
                    "</dependencies>");

      assertModules("module");
      final Module module = getModule("module");
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      assertNotNull(facet);
      assertFalse(facet.isLibraryProject());

      final OrderEntry[] deps = ModuleRootManager.getInstance(module).getOrderEntries();
      assertEquals(4, deps.length);
      assertInstanceOf(deps[0], ModuleJdkOrderEntry.class);
      assertEquals("< Maven Android API 17 Platform >", deps[0].getPresentableName());
      assertInstanceOf(deps[1], ModuleSourceOrderEntry.class);
      assertInstanceOf(deps[2], LibraryOrderEntry.class);
      assertEquals(deps[2].getPresentableName(), "Maven: com:myaar:aar:1.0");
      final Library aarLib = ((LibraryOrderEntry)deps[2]).getLibrary();
      assertNotNull(aarLib);
      final String[] dep2Urls = aarLib.getUrls(OrderRootType.CLASSES);
      assertEquals(3, dep2Urls.length);
      String classesJarDep = null;
      String myJarDep = null;
      String resDep = null;

      for (String url : dep2Urls) {
        if (url.contains("classes.jar")) {
          classesJarDep = url;
        }
        else if (url.contains("myjar.jar")) {
          myJarDep = url;
        }
        else {
          resDep = url;
        }
      }
      final String extractedAarDirPath = FileUtil.toCanonicalPath(myDir.getPath() + "/project/gen-external-apklibs/com_myaar_1.0");
      assertEquals(VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, extractedAarDirPath + "/classes.jar") +
                   JarFileSystem.JAR_SEPARATOR, classesJarDep);
      assertEquals(VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, extractedAarDirPath + "/libs/myjar.jar") +
                   JarFileSystem.JAR_SEPARATOR, myJarDep);
      assertEquals(VfsUtilCore.pathToUrl(extractedAarDirPath + "/res"), resDep);

      assertInstanceOf(deps[3], LibraryOrderEntry.class);
      assertEquals(deps[3].getPresentableName(), "Maven: com:myjar:1.0");
      final Library jarLib = ((LibraryOrderEntry)deps[3]).getLibrary();
      assertNotNull(jarLib);
      final String[] dep3Urls = jarLib.getUrls(OrderRootType.CLASSES);
      assertEquals(1, dep3Urls.length);
      assertTrue(dep3Urls[0].endsWith("com/myjar/1.0/myjar-1.0.jar!/"));
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  public void testExternalApklib1() throws Exception {
    setRepositoryPath(new File(myDir, "__repo").getPath());
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib"), new File(getRepositoryPath(), "com/myapklib/1.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myjar"), new File(getRepositoryPath(), "com/myjar/1.0"));

    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      importProject(getPomContent("apk", "module", "") +
                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>com</groupId>\n" +
                    "    <artifactId>myapklib</artifactId>\n" +
                    "    <version>1.0</version>\n" +
                    "    <type>apklib</type>" +
                    "  </dependency>" +
                    "</dependencies>");

      assertModules("module", "~apklib-com_myapklib_1.0");
      final Module module = getModule("module");
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      assertNotNull(facet);
      assertFalse(facet.isLibraryProject());

      final Module apklibModule = getModule("~apklib-com_myapklib_1.0");
      final AndroidFacet apklibFacet = AndroidFacet.getInstance(apklibModule);
      assertNotNull(apklibFacet);
      assertTrue(apklibFacet.isLibraryProject());
      checkSdk(ModuleRootManager.getInstance(apklibModule).getSdk(), "Maven Android API 2 Platform", "android-2");

      final Library jarLib = checkAppModuleDeps(module, apklibModule);
      checkApklibModule(apklibModule, jarLib);
      final VirtualFile[] apklibRoots = ModuleRootManager.getInstance(apklibModule).getContentRoots();
      assertEquals(1, apklibRoots.length);
      assertEquals(FileUtil.toCanonicalPath(myDir.getPath() + "/project/gen-external-apklibs/com_myapklib_1.0"), apklibRoots[0].getPath());
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;

      // do not lock files by maven process
      MavenServerManager.getInstance().shutdown(true);
    }
  }

  public void testExternalApklib2() throws Exception {
    setRepositoryPath(new File(myDir, "__repo").getPath());
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib"), new File(getRepositoryPath(), "com/myapklib/1.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myjar"), new File(getRepositoryPath(), "com/myjar/1.0"));

    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      importProject(getPomContent("apk", "module", "") +
                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>com</groupId>\n" +
                    "    <artifactId>myapklib</artifactId>\n" +
                    "    <version>1.0</version>\n" +
                    "    <type>apklib</type>" +
                    "    <scope>provided</scope>" +
                    "  </dependency>" +
                    "</dependencies>");
      assertModules("module");
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;

      // do not lock files by maven process
      MavenServerManager.getInstance().shutdown(true);
    }
  }

  public void testExternalApklib3() throws Exception {
    setRepositoryPath(new File(myDir, "__repo").getPath());
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib"), new File(getRepositoryPath(), "com/myapklib/1.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myjar"), new File(getRepositoryPath(), "com/myjar/1.0"));

    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      final VirtualFile pom1 = createModulePom("module1", getPomContent("apk", "module1", "") +
                                                          "<dependencies>" +
                                                          "  <dependency>" +
                                                          "    <groupId>com</groupId>\n" +
                                                          "    <artifactId>myapklib</artifactId>\n" +
                                                          "    <version>1.0</version>\n" +
                                                          "    <type>apklib</type>" +
                                                          "  </dependency>" +
                                                          "</dependencies>");
      final VirtualFile pom2 = createModulePom("module2", getPomContent("apk", "module2", "") +
                                                          "<dependencies>" +
                                                          "  <dependency>" +
                                                          "    <groupId>com</groupId>\n" +
                                                          "    <artifactId>myapklib</artifactId>\n" +
                                                          "    <version>1.0</version>\n" +
                                                          "    <type>apklib</type>" +
                                                          "  </dependency>" +
                                                          "</dependencies>");
      importProjects(pom1, pom2);
      assertModules("module1", "module2", "~apklib-com_myapklib_1.0");

      final Module module1 = getModule("module1");
      final AndroidFacet facet1 = AndroidFacet.getInstance(module1);
      assertNotNull(facet1);
      assertFalse(facet1.isLibraryProject());

      final Module module2 = getModule("module1");
      final AndroidFacet facet2 = AndroidFacet.getInstance(module2);
      assertNotNull(facet2);
      assertFalse(facet2.isLibraryProject());

      final Module apklibModule = getModule("~apklib-com_myapklib_1.0");
      final AndroidFacet apklibFacet = AndroidFacet.getInstance(apklibModule);
      assertNotNull(apklibFacet);
      assertTrue(apklibFacet.isLibraryProject());

      final Library jarLib1 = checkAppModuleDeps(module1, apklibModule);
      final Library jarLib2 = checkAppModuleDeps(module2, apklibModule);
      assertEquals(jarLib1, jarLib2);
      checkApklibModule(apklibModule, jarLib1);
      final VirtualFile[] apklibRoots = ModuleRootManager.getInstance(apklibModule).getContentRoots();
      assertEquals(1, apklibRoots.length);
      final String apklibRootPath = apklibRoots[0].getPath();
      assertTrue(
        apklibRootPath.equals(FileUtil.toCanonicalPath(myDir.getPath() + "/project/module1/gen-external-apklibs/com_myapklib_1.0")) ||
        apklibRootPath.equals(FileUtil.toCanonicalPath(myDir.getPath() + "/project/module2/gen-external-apklibs/com_myapklib_1.0")));
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;

      // do not lock files by maven process
      MavenServerManager.getInstance().shutdown(true);
    }
  }

  public void testExternalApklib4() throws Exception {
    setRepositoryPath(new File(myDir, "__repo").getPath());
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib1"), new File(getRepositoryPath(), "com/myapklib1/1.0"));

    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      importProject(getPomContent("apk", "module", "") +
                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>com</groupId>\n" +
                    "    <artifactId>myapklib1</artifactId>\n" +
                    "    <version>1.0</version>\n" +
                    "    <type>apklib</type>" +
                    "  </dependency>" +
                    "</dependencies>");

      assertModules("module", "~apklib-com_myapklib1_1.0");
      final Module apklibModule = getModule("~apklib-com_myapklib1_1.0");
      checkSdk(ModuleRootManager.getInstance(apklibModule).getSdk(), "Maven Android API 17 Platform", "android-17");
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;

      // do not lock files by maven process
      MavenServerManager.getInstance().shutdown(true);
    }
  }

  public void testExternalApklib5() throws Exception {
    setRepositoryPath(new File(myDir, "__repo").getPath());
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib2"), new File(getRepositoryPath(), "com/myapklib2/1.0"));

    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      importProject(getPomContent("apk", "module", "") +
                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>com</groupId>\n" +
                    "    <artifactId>myapklib2</artifactId>\n" +
                    "    <version>1.0</version>\n" +
                    "    <type>apklib</type>" +
                    "  </dependency>" +
                    "</dependencies>");

      assertModules("module", "~apklib-com_myapklib2_1.0");
      final Module apklibModule = getModule("~apklib-com_myapklib2_1.0");
      checkSdk(ModuleRootManager.getInstance(apklibModule).getSdk(), "Maven Android API 2 Platform", "android-2");
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;

      // do not lock files by maven process
      MavenServerManager.getInstance().shutdown(true);
    }
  }

  public void testExternalApklib6() throws Exception {
    setRepositoryPath(new File(myDir, "__repo").getPath());
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib3"),
                     new File(getRepositoryPath(), "com/myapklib3/1.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib4_1"),
                     new File(getRepositoryPath(), "com/myapklib4/1.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib4_2"),
                     new File(getRepositoryPath(), "com/myapklib4/2.0"));

    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      importProject(getPomContent("apk", "module", "") +
                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>com</groupId>\n" +
                    "    <artifactId>myapklib3</artifactId>\n" +
                    "    <version>1.0</version>\n" +
                    "    <type>apklib</type>" +
                    "  </dependency>" +
                    "  <dependency>" +
                    "    <groupId>com</groupId>\n" +
                    "    <artifactId>myapklib4</artifactId>\n" +
                    "    <version>1.0</version>\n" +
                    "    <type>apklib</type>" +
                    "  </dependency>" +
                    "</dependencies>");

      assertModules("module", "~apklib-com_myapklib3_1.0", "~apklib-com_myapklib4_1.0", "~apklib-com_myapklib4_2.0");
      assertModuleModuleDeps("module", "~apklib-com_myapklib3_1.0", "~apklib-com_myapklib4_1.0");
      assertModuleModuleDeps("~apklib-com_myapklib3_1.0", "~apklib-com_myapklib4_2.0");
      assertModuleModuleDeps("~apklib-com_myapklib4_1.0");
      assertModuleModuleDeps("~apklib-com_myapklib4_2.0");

      Module module = getModule("~apklib-com_myapklib3_1.0");
      checkSdk(ModuleRootManager.getInstance(module).getSdk(), "Maven Android API 2 Platform", "android-2");
      module = getModule("~apklib-com_myapklib4_1.0");
      checkSdk(ModuleRootManager.getInstance(module).getSdk(), "Maven Android API 17 Platform", "android-17");
      module = getModule("~apklib-com_myapklib4_2.0");
      checkSdk(ModuleRootManager.getInstance(module).getSdk(), "Maven Android API 2 Platform", "android-2");
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;

      // do not lock files by maven process
      MavenServerManager.getInstance().shutdown(true);
    }
  }

  public void testExternalApklib7() throws Exception {
    setRepositoryPath(new File(myDir, "__repo").getPath());
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib5"),
                     new File(getRepositoryPath(), "com/myapklib5/1.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib6_1"),
                     new File(getRepositoryPath(), "com/myapklib6/1.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib6_2"),
                     new File(getRepositoryPath(), "com/myapklib6/2.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib7_1"),
                     new File(getRepositoryPath(), "com/myapklib7/1.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib7_2"),
                     new File(getRepositoryPath(), "com/myapklib7/2.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib7_3"),
                     new File(getRepositoryPath(), "com/myapklib7/3.0"));

    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      importProject(getPomContent("apk", "module", "") +
                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>com</groupId>\n" +
                    "    <artifactId>myapklib5</artifactId>\n" +
                    "    <version>1.0</version>\n" +
                    "    <type>apklib</type>" +
                    "  </dependency>" +
                    "  <dependency>" +
                    "    <groupId>com</groupId>\n" +
                    "    <artifactId>myapklib6</artifactId>\n" +
                    "    <version>2.0</version>\n" +
                    "    <type>apklib</type>" +
                    "  </dependency>" +
                    "  <dependency>" +
                    "    <groupId>com</groupId>\n" +
                    "    <artifactId>myapklib7</artifactId>\n" +
                    "    <version>2.0</version>\n" +
                    "    <type>apklib</type>" +
                    "  </dependency>" +
                    "</dependencies>");

      assertModules("module", "~apklib-com_myapklib5_1.0", "~apklib-com_myapklib6_1.0", "~apklib-com_myapklib6_2.0",
                    "~apklib-com_myapklib7_1.0", "~apklib-com_myapklib7_2.0", "~apklib-com_myapklib7_3.0");
      assertModuleModuleDeps("module", "~apklib-com_myapklib5_1.0", "~apklib-com_myapklib6_2.0", "~apklib-com_myapklib7_2.0");
      assertModuleModuleDeps("~apklib-com_myapklib5_1.0", "~apklib-com_myapklib6_1.0", "~apklib-com_myapklib7_1.0");
      assertModuleModuleDeps("~apklib-com_myapklib6_1.0", "~apklib-com_myapklib7_3.0");
      assertModuleModuleDeps("~apklib-com_myapklib6_2.0", "~apklib-com_myapklib7_1.0", "~apklib-com_myapklib5_1.0");
      assertModuleModuleDeps("~apklib-com_myapklib7_1.0");
      assertModuleModuleDeps("~apklib-com_myapklib7_2.0");
      assertModuleModuleDeps("~apklib-com_myapklib7_3.0", "~apklib-com_myapklib6_2.0");
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;

      // do not lock files by maven process
      MavenServerManager.getInstance().shutdown(true);
    }
  }

  public void testBlockOptionImporting() throws Exception {
    final Sdk sdk = AndroidTestCase.createAndroidSdk(AndroidTestCase.getDefaultTestSdkPath(), AndroidTestCase.getDefaultPlatformDir());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(sdk);
      }
    });

    importProject(getPomContent("apk", "module", ""));
    final AndroidFacet facet = getFacet("module");
    assertNotNull(facet);
    final JpsAndroidModuleProperties properties = facet.getProperties();
    assertEmpty(properties.myNotImportedProperties);

    importProject(getPomContent("apk", "module",
                                "<resourceDirectory>${project.basedir}/res1</resourceDirectory>" +
                                "<assetsDirectory>${project.basedir}/assets1</assetsDirectory>" +
                                "<nativeLibrariesDirectory>${project.basedir}/libs1</nativeLibrariesDirectory>" +
                                "<androidManifestFile>${project.basedir}/manifest1/AndroidManifest.xml</androidManifestFile>"));

    assertEquals("/res1", properties.RES_FOLDER_RELATIVE_PATH);
    assertEquals("/assets1", properties.ASSETS_FOLDER_RELATIVE_PATH);
    assertEquals("/libs1", properties.LIBS_FOLDER_RELATIVE_PATH);
    assertEquals("/manifest1/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);

    properties.myNotImportedProperties.add(AndroidImportableProperty.RESOURCES_DIR_PATH);

    importProject(getPomContent("apk", "module",
                                "<resourceDirectory>${project.basedir}/res2</resourceDirectory>" +
                                "<assetsDirectory>${project.basedir}/assets2</assetsDirectory>" +
                                "<nativeLibrariesDirectory>${project.basedir}/libs2</nativeLibrariesDirectory>" +
                                "<androidManifestFile>${project.basedir}/manifest2/AndroidManifest.xml</androidManifestFile>"));

    assertEquals("/res1", properties.RES_FOLDER_RELATIVE_PATH);
    assertEquals("/assets2", properties.ASSETS_FOLDER_RELATIVE_PATH);
    assertEquals("/libs2", properties.LIBS_FOLDER_RELATIVE_PATH);
    assertEquals("/manifest2/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);

    properties.myNotImportedProperties.add(AndroidImportableProperty.ASSETS_DIR_PATH);

    importProject(getPomContent("apk", "module",
                                "<resourceDirectory>${project.basedir}/res3</resourceDirectory>" +
                                "<assetsDirectory>${project.basedir}/assets3</assetsDirectory>" +
                                "<nativeLibrariesDirectory>${project.basedir}/libs3</nativeLibrariesDirectory>" +
                                "<androidManifestFile>${project.basedir}/manifest3/AndroidManifest.xml</androidManifestFile>"));

    assertEquals("/res1", properties.RES_FOLDER_RELATIVE_PATH);
    assertEquals("/assets2", properties.ASSETS_FOLDER_RELATIVE_PATH);
    assertEquals("/libs3", properties.LIBS_FOLDER_RELATIVE_PATH);
    assertEquals("/manifest3/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);

    properties.myNotImportedProperties.add(AndroidImportableProperty.NATIVE_LIBS_DIR_PATH);

    importProject(getPomContent("apk", "module",
                                "<resourceDirectory>${project.basedir}/res4</resourceDirectory>" +
                                "<assetsDirectory>${project.basedir}/assets4</assetsDirectory>" +
                                "<nativeLibrariesDirectory>${project.basedir}/libs4</nativeLibrariesDirectory>" +
                                "<androidManifestFile>${project.basedir}/manifest4/AndroidManifest.xml</androidManifestFile>"));

    assertEquals("/res1", properties.RES_FOLDER_RELATIVE_PATH);
    assertEquals("/assets2", properties.ASSETS_FOLDER_RELATIVE_PATH);
    assertEquals("/libs3", properties.LIBS_FOLDER_RELATIVE_PATH);
    assertEquals("/manifest4/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);

    properties.myNotImportedProperties.add(AndroidImportableProperty.MANIFEST_FILE_PATH);

    importProject(getPomContent("apk", "module",
                                "<resourceDirectory>${project.basedir}/res5</resourceDirectory>" +
                                "<assetsDirectory>${project.basedir}/assets5</assetsDirectory>" +
                                "<nativeLibrariesDirectory>${project.basedir}/libs5</nativeLibrariesDirectory>" +
                                "<androidManifestFile>${project.basedir}/manifest5/AndroidManifest.xml</androidManifestFile>"));

    assertEquals("/res1", properties.RES_FOLDER_RELATIVE_PATH);
    assertEquals("/assets2", properties.ASSETS_FOLDER_RELATIVE_PATH);
    assertEquals("/libs3", properties.LIBS_FOLDER_RELATIVE_PATH);
    assertEquals("/manifest4/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);

    properties.myNotImportedProperties.clear();

    importProject();

    assertEquals("/res5", properties.RES_FOLDER_RELATIVE_PATH);
    assertEquals("/assets5", properties.ASSETS_FOLDER_RELATIVE_PATH);
    assertEquals("/libs5", properties.LIBS_FOLDER_RELATIVE_PATH);
    assertEquals("/manifest5/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);
  }

  public void testChangeGenRootManually() throws Exception {
    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getDefaultTestSdkPath();
    try {
      FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath(), "maven/changeGenRootManually"), new File(myProjectRoot.getPath()));
      importProject(getPomContent("apk", "module", ""));
      assertModules("module");
      final Module module = getModule("module");
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

      String[] sourceRoots = toRelativePaths(rootManager.getSourceRootUrls());
      assertEquals(new HashSet<>(Arrays.asList(
        "target/generated-sources/r",
        "target/generated-sources/aidl")
      ), new HashSet<>(Arrays.asList(sourceRoots)));

      String[] excludedRootUrls = toRelativePaths(rootManager.getExcludeRootUrls());
      assertEquals(new HashSet<>(Arrays.asList(
        "target/generated-sources/combined-resources",
        "target/generated-sources/combined-assets",
        "target/test-classes",
        "target/classes",
        "target/generated-sources/extracted-dependencies")
      ), new HashSet<>(Arrays.asList(excludedRootUrls)));

      final AndroidFacet facet = AndroidFacet.getInstance(module);
      assertNotNull(facet);
      facet.getProperties().GEN_FOLDER_RELATIVE_PATH_APT = "";
      facet.getProperties().GEN_FOLDER_RELATIVE_PATH_AIDL = "";

      importProject();

      sourceRoots = toRelativePaths(rootManager.getSourceRootUrls());
      assertEquals(2, sourceRoots.length);

      excludedRootUrls = toRelativePaths(rootManager.getExcludeRootUrls());
      assertEquals(new HashSet<>(Arrays.asList(
        "target/test-classes", "target/classes",
        "target/generated-sources/combined-resources",
        "target/generated-sources/combined-assets",
        "target/generated-sources/extracted-dependencies",
        "target/generated-sources/r",
        "target/generated-sources/aidl")
      ), new HashSet<>(Arrays.asList(excludedRootUrls)));
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  private String[] toRelativePaths(String[] urls) {
    final String[] result = new String[urls.length];

    for (int i = 0; i < urls.length; i++) {
      final String absPath = VfsUtilCore.urlToPath(urls[i]);
      final String relPath = FileUtil.getRelativePath(new File(myProjectRoot.getPath()), new File(absPath));
      assertNotNull(relPath);
      result[i] = FileUtil.toSystemIndependentName(relPath);
    }
    return result;
  }

  private static void checkApklibModule(Module apklibModule, Library jarLib) {
    final OrderEntry[] apklibDeps = ModuleRootManager.getInstance(apklibModule).getOrderEntries();
    assertEquals(3, apklibDeps.length);
    assertInstanceOf(apklibDeps[0], ModuleJdkOrderEntry.class);
    assertInstanceOf(apklibDeps[1], ModuleSourceOrderEntry.class);
    assertInstanceOf(apklibDeps[2], LibraryOrderEntry.class);
    final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)apklibDeps[2];
    assertEquals(jarLib, libraryOrderEntry.getLibrary());
  }

  private static Library checkAppModuleDeps(Module module, Module apklibModule) {
    final OrderEntry[] deps = ModuleRootManager.getInstance(module).getOrderEntries();
    assertEquals(4, deps.length);
    assertInstanceOf(deps[0], ModuleJdkOrderEntry.class);
    assertEquals("< Maven Android API 17 Platform >", deps[0].getPresentableName());
    assertInstanceOf(deps[1], ModuleSourceOrderEntry.class);
    assertInstanceOf(deps[2], LibraryOrderEntry.class);
    assertEquals(deps[2].getPresentableName(), "Maven: com:myjar:1.0");
    final Library jarLib = ((LibraryOrderEntry)deps[2]).getLibrary();
    assertNotNull(jarLib);
    final String[] dep3Urls = jarLib.getUrls(OrderRootType.CLASSES);
    assertEquals(1, dep3Urls.length);
    assertTrue(dep3Urls[0].endsWith("com/myjar/1.0/myjar-1.0.jar!/"));
    assertInstanceOf(deps[3], ModuleOrderEntry.class);
    assertEquals(apklibModule, ((ModuleOrderEntry)deps[3]).getModule());
    return jarLib;
  }

  private static String getPomContent(final String packaging, final String artifactId, final String androidPluginConfig) {
    return getPomContent(packaging, artifactId, androidPluginConfig, "", "");
  }

  private static String getPomContent(final String packaging,
                                      final String artifactId,
                                      final String androidConfig,
                                      String plugins,
                                      final String build) {
    return "<groupId>test</groupId>" +
           "<artifactId>" + artifactId + "</artifactId>" +
           "<version>1</version>" +
           "<packaging>" + packaging + "</packaging>" +
           "<build>" +
           "  <plugins>" +
           "    <plugin>" +
           "      <groupId>com.jayway.maven.plugins.android.generation2</groupId>" +
           "      <artifactId>android-maven-plugin</artifactId>" +
           "      <configuration>" +
           "        <sdk>" +
           "          <platform>17</platform>" +
           "        </sdk>" +
           androidConfig +
           "      </configuration>" +
           "    </plugin>" +
           plugins +
           "  </plugins>" +
           build +
           "</build>";
  }

  private void checkSdk(Sdk sdk) {
    checkSdk(sdk, "Maven Android API 17 Platform", "android-17");
  }

  private void checkSdk(Sdk sdk, String sdkName, String buildTarget) {
    assertNotNull(sdk);
    assertEquals(sdkName, sdk.getName());
    assertTrue(FileUtil.pathsEqual(AndroidTestCase.getDefaultTestSdkPath(), sdk.getHomePath()));
    assertEquals(AndroidSdkType.getInstance(), sdk.getSdkType());
    final SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
    assertNotNull(additionalData);
    assertInstanceOf(additionalData, AndroidSdkAdditionalData.class);
    final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)additionalData;
    assertEquals(buildTarget, data.getBuildTargetHashString());
    assertEquals(myJdk, data.getJavaSdk());
    final HashSet<String> urls = new HashSet<>(Arrays.asList(sdk.getRootProvider().getUrls(OrderRootType.CLASSES)));
    assertTrue(urls.containsAll(Arrays.asList(myJdk.getRootProvider().getUrls(OrderRootType.CLASSES))));
  }
}
