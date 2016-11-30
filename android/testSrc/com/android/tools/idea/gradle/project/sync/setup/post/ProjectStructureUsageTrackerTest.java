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
package com.android.tools.idea.gradle.project.sync.setup.post;

import com.android.builder.model.InstantRun;
import com.android.ide.common.repository.GradleVersion;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.LoggedUsage;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.fd.InstantRunConfiguration;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacetType;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.stubs.android.*;
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.google.wireless.android.sdk.stats.*;
import com.intellij.facet.*;
import com.intellij.facet.impl.FacetTypeRegistryImpl;
import com.intellij.facet.impl.ProjectFacetManagerImpl;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockModule;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static com.android.builder.model.AndroidProject.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests for {@link ProjectStructureUsageTracker}.
 */
@Ignore    // Seems to be putting things into a persistent bad state. http://b.android.com/227704
public class ProjectStructureUsageTrackerTest {

  // Used to test the scheduling of usage tracking.
  private VirtualTimeScheduler scheduler;
  // A UsageTracker implementation that allows introspection of logged metrics in tests.
  private TestUsageTracker myUsageTracker;

  @Before
  public void before() {
    scheduler = new VirtualTimeScheduler();
    myUsageTracker = new TestUsageTracker(new AnalyticsSettings(), scheduler);
    UsageTracker.setInstanceForTest(myUsageTracker);
  }

  @After
  public void after() throws Exception {
    myUsageTracker.close();
    UsageTracker.cleanAfterTesting();
  }

  @Test
  public void productStructureUsageTrackingBasicTest() {
    // Creates a mock studio project and calls code to track usage metrics on it.
    trackGradleProject(new MockProjectSpec()
                           .setGradleVersion(new GradleVersion(2, 14, 1))
                           .setAndroidPluginVersion("2.3.0-dev")
                           .setUserEnabledInstantRun(true)
                           .addModule(new MockModuleSpec()
                                      .setApplicationId("app_id")
                                      .setName("module_name")
                                      .setLocation("/some_path")
                                      .setDefaultVariant("debug")
                                      .addBuildType("debug")
                                      .addBuildType("release")
                                      .setSupportsInstantRun(true)));

    List<LoggedUsage> usages = myUsageTracker.getUsages();

    // we expect one metric logged immediately
    assertEquals(1, usages.size());
    LoggedUsage usage = usages.get(0);
    assertEquals(0, usage.getTimestamp());
    assertEquals(AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS, usage.getStudioEvent().getKind());
    assertEquals(GradleBuildDetails.newBuilder()
      .setAndroidPluginVersion("2.3.0-dev")
      .setGradleVersion("2.14.1")
      .setUserEnabledIr(true)
      .setModelSupportsIr(true)
      .setVariantSupportsIr(true)
      .addLibraries(GradleLibrary.newBuilder()
                    .setJarDependencyCount(0)
                    .setAarDependencyCount(0))
      .addModules(GradleModule.newBuilder()
                  .setTotalModuleCount(1)
                  .setAppModuleCount(1)
                  .setLibModuleCount(0))
      .addAndroidModules(GradleAndroidModule.newBuilder()
                         .setModuleName(AndroidStudioUsageTracker.anonymizeUtf8("module_name"))
                         .setIsLibrary(false)
                         .setBuildTypeCount(2)
                         .setFlavorCount(0)
                         .setFlavorDimension(0)
                         .setSigningConfigCount(0))
      .setAppId(AndroidStudioUsageTracker.anonymizeUtf8("app_id"))
                   .build(), usage.getStudioEvent().getGradleBuildDetails());
  }


  @Test
  public void productStructureUsageTrackingComplexProjectTest() {
    // tests metrics for a more complex project with jars, aars, libraries, flavors & dimensions as well as native code.
    trackGradleProject(new MockProjectSpec()
                         .setGradleVersion(new GradleVersion(2, 14, 1))
                         .setAndroidPluginVersion("2.3.0-dev")
                         .setUserEnabledInstantRun(true)
                         .addModule(new MockModuleSpec()
                                      .setApplicationId("app_id")
                                      .setName("module_name")
                                      .setLocation("/some_path")
                                      .setDefaultVariant("debug")
                                      .addBuildType("debug")
                                      .addBuildType("ship")
                                      .setSupportsInstantRun(true)
                                     .addJar("guava.jar")
                                     .addAar("gson.aar"))
                         .addModule(new MockModuleSpec()
                                      .setApplicationId("app_id")
                                      .setIsLibrary(true)
                                      .setName("library")
                                      .setLocation("/other_path")
                                      .setDefaultVariant("debug")
                                      .addBuildType("debug")
                                      .addBuildType("release")
                                      .addFlavor("x86")
                                      .addFlavor("arm")
                                      .addDimension("abi")
                                      .addNativeLibrary("libssl.so")
                                      .setCppBuildSystem("cmake")
                                      .setNativeModelVersion("2.3.0")
                                      .setSupportsInstantRun(false)));

    List<LoggedUsage> usages = myUsageTracker.getUsages();
    assertEquals(1, usages.size());
    LoggedUsage usage = usages.get(0);
    assertEquals(0, usage.getTimestamp());
    assertEquals(AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS, usage.getStudioEvent().getKind());
    assertEquals(GradleBuildDetails.newBuilder()
                   .setAndroidPluginVersion("2.3.0-dev")
                   .setGradleVersion("2.14.1")
                   .setUserEnabledIr(true)
                   .setModelSupportsIr(true)
                   .setVariantSupportsIr(true)
                   .addLibraries(GradleLibrary.newBuilder()
                                   .setJarDependencyCount(1)
                                   .setAarDependencyCount(1))
                   .addModules(GradleModule.newBuilder()
                                 .setTotalModuleCount(2)
                                 .setAppModuleCount(1)
                                 .setLibModuleCount(1))
                   .addAndroidModules(GradleAndroidModule.newBuilder()
                                        .setModuleName(AndroidStudioUsageTracker.anonymizeUtf8("module_name"))
                                        .setIsLibrary(false)
                                        .setBuildTypeCount(2)
                                        .setFlavorCount(0)
                                        .setFlavorDimension(0)
                                        .setSigningConfigCount(0))
                   .addAndroidModules(GradleAndroidModule.newBuilder()
                                        .setModuleName(AndroidStudioUsageTracker.anonymizeUtf8("library"))
                                        .setIsLibrary(true)
                                        .setBuildTypeCount(2)
                                        .setFlavorCount(2)
                                        .setFlavorDimension(1)
                                        .setSigningConfigCount(0))
                   .addNativeAndroidModules(GradleNativeAndroidModule.newBuilder()
                                              .setModuleName(AndroidStudioUsageTracker.anonymizeUtf8("library"))
                                            .setBuildSystemType(GradleNativeAndroidModule.NativeBuildSystemType.CMAKE))
                   .setAppId(AndroidStudioUsageTracker.anonymizeUtf8("app_id"))
                   .build(), usage.getStudioEvent().getGradleBuildDetails());
  }

  @Test
  public void productStructureUsageTrackingGradleExperimentalNativeBuilds() {
    // Tests metrics for a project that uses Gradle experimental C++ builds.
    trackGradleProject(new MockProjectSpec()
                         .setGradleVersion(new GradleVersion(2, 14, 1))
                         .setAndroidPluginVersion("2.3.0-dev")
                         .setUserEnabledInstantRun(true)
                         .addModule(new MockModuleSpec()
                                      .setApplicationId("app_id")
                                      .setIsLibrary(true)
                                      .setName("library")
                                      .setLocation("/other_path")
                                      .setDefaultVariant("debug")
                                      .addBuildType("debug")
                                      .addBuildType("release")
                                      .addFlavor("x86")
                                      .addFlavor("arm")
                                      .addDimension("abi")
                                      .addNativeLibrary("libssl.so")
                                      .setNativeModelVersion("2.0.0")
                                      .setCppBuildSystem("gradle")
                                      .setSupportsInstantRun(false)));

    List<LoggedUsage> usages = myUsageTracker.getUsages();
    assertEquals(1, usages.size());
    LoggedUsage usage = usages.get(0);
    assertEquals(0, usage.getTimestamp());
    assertEquals(AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS, usage.getStudioEvent().getKind());
    assertEquals(GradleBuildDetails.newBuilder()
                   .setAndroidPluginVersion("2.3.0-dev")
                   .setGradleVersion("2.14.1")
                   .setUserEnabledIr(true)
                   .setModelSupportsIr(true)
                   .setVariantSupportsIr(false)
                   .addModules(GradleModule.newBuilder()
                                 .setTotalModuleCount(1)
                                 .setAppModuleCount(0)
                                 .setLibModuleCount(1))
                   .addAndroidModules(GradleAndroidModule.newBuilder()
                                        .setModuleName(AndroidStudioUsageTracker.anonymizeUtf8("library"))
                                        .setIsLibrary(true)
                                        .setBuildTypeCount(2)
                                        .setFlavorCount(2)
                                        .setFlavorDimension(1)
                                        .setSigningConfigCount(0))
                   .addNativeAndroidModules(GradleNativeAndroidModule.newBuilder()
                                              .setModuleName(AndroidStudioUsageTracker.anonymizeUtf8("library"))
                                              .setBuildSystemType(GradleNativeAndroidModule.NativeBuildSystemType.GRADLE_EXPERIMENTAL))
                   .setAppId(AndroidStudioUsageTracker.anonymizeUtf8("app_id"))
                   .build(), usage.getStudioEvent().getGradleBuildDetails());
  }

  @Test
  public void productStructureUsageTrackingNoNativeModel() {
    // Tests metrics for a project that has C++ but no native model (old style C++ support).
    trackGradleProject(new MockProjectSpec()
                         .setGradleVersion(new GradleVersion(2, 14, 1))
                         .setAndroidPluginVersion("2.3.0-dev")
                         .setUserEnabledInstantRun(true)
                         .addModule(new MockModuleSpec()
                                      .setApplicationId("app_id")
                                      .setIsLibrary(true)
                                      .setName("library")
                                      .setLocation("/other_path")
                                      .setDefaultVariant("debug")
                                      .addBuildType("debug")
                                      .addBuildType("release")
                                      .addFlavor("x86")
                                      .addFlavor("arm")
                                      .addDimension("abi")
                                      .addNativeLibrary("libssl.so")
                                      .setCppBuildSystem("gradle")
                                      .setSupportsInstantRun(false)));

    List<LoggedUsage> usages = myUsageTracker.getUsages();
    assertEquals(1, usages.size());
    LoggedUsage usage = usages.get(0);
    assertEquals(0, usage.getTimestamp());
    assertEquals(AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS, usage.getStudioEvent().getKind());
    assertEquals(GradleBuildDetails.newBuilder()
                   .setAndroidPluginVersion("2.3.0-dev")
                   .setGradleVersion("2.14.1")
                   .setUserEnabledIr(true)
                   .setModelSupportsIr(true)
                   .setVariantSupportsIr(false)
                   .addModules(GradleModule.newBuilder()
                                 .setTotalModuleCount(1)
                                 .setAppModuleCount(0)
                                 .setLibModuleCount(1))
                   .addAndroidModules(GradleAndroidModule.newBuilder()
                                        .setModuleName(AndroidStudioUsageTracker.anonymizeUtf8("library"))
                                        .setIsLibrary(true)
                                        .setBuildTypeCount(2)
                                        .setFlavorCount(2)
                                        .setFlavorDimension(1)
                                        .setSigningConfigCount(0))
                   .addNativeAndroidModules(GradleNativeAndroidModule.newBuilder()
                                              .setModuleName("")
                                              .setBuildSystemType(GradleNativeAndroidModule.NativeBuildSystemType.GRADLE_EXPERIMENTAL))
                   .setAppId(AndroidStudioUsageTracker.anonymizeUtf8("app_id"))
                   .build(), usage.getStudioEvent().getGradleBuildDetails());
  }

  @Test
  public void getApplicationIdTest(){
    // Tests the getApplicationId helper.
    Project projectWithApp = buildStudioProject(new MockProjectSpec()
                         .setGradleVersion(new GradleVersion(2, 14, 1))
                         .setAndroidPluginVersion("2.3.0-dev")
                         .setUserEnabledInstantRun(true)
                         .addModule(new MockModuleSpec()
                                      .setApplicationId("app_id")
                                      .setName("module_name")
                                      .setLocation("/some_path")
                                      .setDefaultVariant("debug")
                                      .addBuildType("debug")
                                      .addBuildType("release")
                                      .setSupportsInstantRun(true)));
    assertEquals("app_id", ProjectStructureUsageTracker.getApplicationId(projectWithApp));

    Project projectWithLibrary = buildStudioProject(new MockProjectSpec()
                                                  .setGradleVersion(new GradleVersion(2, 14, 1))
                                                  .setAndroidPluginVersion("2.3.0-dev")
                                                  .setUserEnabledInstantRun(true)
                                                  .addModule(new MockModuleSpec()
                                                               .setIsLibrary(true)
                                                               .setName("module_name")
                                                               .setLocation("/some_path")
                                                               .setDefaultVariant("debug")
                                                               .addBuildType("debug")
                                                               .addBuildType("release")
                                                               .setSupportsInstantRun(true)));
    assertNull(ProjectStructureUsageTracker.getApplicationId(projectWithLibrary));
  }

  @Test
  public void stringToBuildSystemTypeTest() {
    assertEquals(GradleNativeAndroidModule.NativeBuildSystemType.NDK_BUILD,
                 ProjectStructureUsageTracker.stringToBuildSystemType("ndk-build"));
    assertEquals(GradleNativeAndroidModule.NativeBuildSystemType.CMAKE,
                 ProjectStructureUsageTracker.stringToBuildSystemType("cmake"));
    assertEquals(GradleNativeAndroidModule.NativeBuildSystemType.NDK_COMPILE,
                 ProjectStructureUsageTracker.stringToBuildSystemType("ndkCompile"));
    assertEquals(GradleNativeAndroidModule.NativeBuildSystemType.GRADLE_EXPERIMENTAL,
                 ProjectStructureUsageTracker.stringToBuildSystemType("gradle"));
    assertEquals(GradleNativeAndroidModule.NativeBuildSystemType.UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE,
                 ProjectStructureUsageTracker.stringToBuildSystemType("blaze"));
  }

  /**
   * Builds a set of mock objects representing an Android Studio project with a set of modules
   * and calls the tracking code to report metrics on this project.
   */
  private void trackGradleProject(MockProjectSpec mockProjectSpec) {
    Project project = buildStudioProject(mockProjectSpec);
    ProjectStructureUsageTracker projectStructureUsageTracker = new ProjectStructureUsageTracker(project);
    projectStructureUsageTracker.trackProjectStructure();
    scheduler.advanceBy(0);
  }

  /**
   * Builds a set of mock objects representing an Android Studio project with a set of modules.
   */
  @NotNull
  private Project buildStudioProject(final MockProjectSpec mockProjectSpec) {
    // IntelliJ objects often require a disposable, we don't really need it here in these tests so we'll use a noop version.
    Disposable noopDisposable = () -> {
    };
    // register a root area for extensions.
    Extensions.registerAreaClass("IDEA_PROJECT", null);

    // Create and register a mock application, use the virtual time scheduler for executing thread pool actions.
    MockApplication application = new MockApplication(noopDisposable) {
      @Override
      public Future<?> executeOnPooledThread(@NotNull Runnable action) {
        return scheduler.submit(action);
      }
    };
    ApplicationManager.setApplication(application, noopDisposable);

    // clean and register the FacetType extension, and add our facets
    Extensions.cleanRootArea(noopDisposable);
    Extensions.getArea(null).registerExtensionPoint(FacetType.EP_NAME.getName(), AndroidFacetType.class.getCanonicalName());
    FacetTypeRegistryImpl facetTypeRegistry = new FacetTypeRegistryImpl();
    application.getPicoContainer().registerComponentInstance(FacetTypeRegistry.class.getName(), facetTypeRegistry);
    facetTypeRegistry.registerFacetType(new AndroidFacetType());
    facetTypeRegistry.registerFacetType(new NdkFacetType());

    // set the project's gradle version.
    application.getPicoContainer()
      .registerComponentInstance(GradleVersions.class.getName(), new GradleVersions(new GradleProjectSettingsFinder()) {
        @Override
        public GradleVersion getGradleVersion(@NotNull Project project) {
          return mockProjectSpec.getGradleVersion();
        }
      });

    // set the user's instant run configuration.
    InstantRunConfiguration instantRunConfiguration = new InstantRunConfiguration();
    application.getPicoContainer().registerComponentInstance(InstantRunConfiguration.class.getName(), instantRunConfiguration);
    instantRunConfiguration.INSTANT_RUN = mockProjectSpec.getUserEnabledInstantRun();


    // Create a mock project.
    DefaultPicoContainer projectContainer = new DefaultPicoContainer(application.getPicoContainer());
    Project project = new MockProject(projectContainer, noopDisposable);
    projectContainer.registerComponentInstance(ProjectFacetManager.class.getName(), new ProjectFacetManagerImpl(project));


    // register each module.
    Module[] modules = new Module[mockProjectSpec.getModules().size()];
    for (int i = 0; i < mockProjectSpec.getModules().size(); i++) {
      MockModuleSpec moduleSpec = mockProjectSpec.getModules().get(i);
      MockModule module = new MockModule(project, noopDisposable);
      module.setName(moduleSpec.getName());
      FacetManagerImpl facetManager = new FacetManagerImpl(module, project.getMessageBus());
      facetManager.addFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
      if (!moduleSpec.getNativeLibraries().isEmpty()) {
        facetManager.addFacet(NdkFacet.getFacetType(), NdkFacet.getFacetName(), null);
      }
      AndroidProjectStub androidProject = new AndroidProjectStub(moduleSpec.getName());
      androidProject.getFlavorDimensions().addAll(moduleSpec.getDimensions());
      androidProject.setModelVersion(mockProjectSpec.getAndroidPluginVersion());
      androidProject.setProjectType(moduleSpec.getIsLibrary() ? PROJECT_TYPE_LIBRARY : PROJECT_TYPE_APP);
      if (!moduleSpec.getNativeLibraries().isEmpty()) {
        androidProject
          .setPluginGeneration(moduleSpec.getCppBuildSystem().equals("ndkCompile") ? GENERATION_ORIGINAL : GENERATION_COMPONENT);
      }

      for (String flavor : moduleSpec.getFlavors()) {
        androidProject.addProductFlavor(flavor);
      }

      for (String buildType : moduleSpec.getBuildTypes()) {
        androidProject.addBuildType(buildType);
        if (moduleSpec.getFlavors().isEmpty()) {
          configureVariant(moduleSpec, androidProject, buildType, buildType);
        }
      }

      for (String flavor : moduleSpec.getFlavors()) {
        for (String buildType : moduleSpec.getBuildTypes()) {
          configureVariant(moduleSpec, androidProject, flavor, buildType);
        }
      }

      AndroidModuleModel androidModuleModel =
        new AndroidModuleModel(moduleSpec.getName(), new File(moduleSpec.getLocation()), androidProject, moduleSpec.getDefaultVariant());
      facetManager.getFacetByType(AndroidFacet.ID).setAndroidModel(androidModuleModel);

      if (!moduleSpec.getNativeLibraries().isEmpty() && moduleSpec.getNativeModelVersion() != null) {
        NativeAndroidProjectStub nap = new NativeAndroidProjectStub(moduleSpec.getName());
        nap.setModelVersion(moduleSpec.getNativeModelVersion());
        nap.getBuildSystems().add(moduleSpec.getCppBuildSystem());
        facetManager.getFacetByType(NdkFacet.getFacetTypeId()).setNdkModuleModel(
          new NdkModuleModel(moduleSpec.getName(), new File("dummy"), nap));
      }
      module.addComponent(FacetManager.class, facetManager);
      modules[i] = module;
    }
    application.getPicoContainer().registerComponentInstance(ModuleManager.class.getName(), new ModuleManagerStub(project, modules));
    return project;
  }

  private static void configureVariant(MockModuleSpec moduleSpec,
                                       AndroidProjectStub androidProject,
                                       String flavor,
                                       String buildType) {
    VariantStub variant = androidProject.addVariant(flavor, buildType);
    AndroidArtifactStub mainArtifact = variant.getMainArtifact();
    mainArtifact.setInstantRun(new InstantRunStub(new File("dummy"), moduleSpec.getSupportsInstantRun(),
                                                  moduleSpec.getSupportsInstantRun() ?
                                                  InstantRun.STATUS_SUPPORTED : InstantRun.STATUS_NOT_SUPPORTED_FOR_NON_DEBUG_VARIANT));
    mainArtifact.setApplicationId(moduleSpec.getApplicationId());
    for (String jar : moduleSpec.getJars()) {
      mainArtifact.getDependencies().addJar(new File(jar));
    }
    for (String aar : moduleSpec.getAars()) {
      AndroidLibraryStub lib = new AndroidLibraryStub(new File(aar), new File(aar), "", variant.getName());
      mainArtifact.getDependencies().addLibrary(lib);
    }
    for (String nativeLib : moduleSpec.getNativeLibraries()) {
      mainArtifact.getNativeLibraries().add(new NativeLibraryStub(nativeLib));
    }
  }

  /**
   * Used in tests to define variables for creating a mock studio project.
   */
  private static class MockProjectSpec {
    private GradleVersion gradleVersion;
    private String androidPluginVersion;
    private List<MockModuleSpec> modules = new ArrayList<>();
    private boolean userEnabledInstantRun;

    public GradleVersion getGradleVersion() {
      return gradleVersion;
    }

    public MockProjectSpec setGradleVersion(GradleVersion gradleVersion) {
      this.gradleVersion = gradleVersion;
      return this;
    }

    public String getAndroidPluginVersion() {
      return androidPluginVersion;
    }

    public MockProjectSpec setAndroidPluginVersion(String androidPluginVersion) {
      this.androidPluginVersion = androidPluginVersion;
      return this;
    }

    public List<MockModuleSpec> getModules() {
      return modules;
    }

    public MockProjectSpec addModule(MockModuleSpec module) {
      modules.add(module);
      return this;
    }

    public boolean getUserEnabledInstantRun() {
      return userEnabledInstantRun;
    }

    public MockProjectSpec setUserEnabledInstantRun(boolean userEnabledInstantRun) {
      this.userEnabledInstantRun = userEnabledInstantRun;
      return this;
    }
  }


  /**
   * Used in tests to define variables for creating a mock studio module.
   */
  private static class MockModuleSpec {
    private String applicationId;
    private String name;
    private String location;
    private String defaultVariant;
    private List<String> flavors = new ArrayList<>();
    private List<String> dimensions = new ArrayList<>();
    private List<String> buildTypes = new ArrayList<>();
    private boolean isLibrary;
    private boolean supportsInstantRun;
    private List<String> jars = new ArrayList<>();
    private List<String> aars = new ArrayList<>();
    private List<String> nativeLibraries = new ArrayList<>();
    private String myCppBuildSystem;
    private String nativeModelVersion;

    public String getName() {
      return name;
    }

    public MockModuleSpec setName(String name) {
      this.name = name;
      return this;
    }

    public String getLocation() {
      return location;
    }

    public MockModuleSpec setLocation(String location) {
      this.location = location;
      return this;
    }

    public String getDefaultVariant() {
      return defaultVariant;
    }

    public MockModuleSpec setDefaultVariant(String defaultVariant) {
      this.defaultVariant = defaultVariant;
      return this;
    }

    public List<String> getFlavors() {
      return flavors;
    }

    public MockModuleSpec addFlavor(String flavor) {
      flavors.add(flavor);
      return this;
    }

    public List<String> getDimensions() {
      return dimensions;
    }

    public MockModuleSpec addDimension(String dimension) {
      dimensions.add(dimension);
      return this;
    }

    public List<String> getBuildTypes() {
      return buildTypes;
    }

    public MockModuleSpec addBuildType(String buildType) {
      buildTypes.add(buildType);
      return this;
    }


    public boolean getIsLibrary() {
      return isLibrary;
    }

    public MockModuleSpec setIsLibrary(boolean isLibrary) {
      this.isLibrary = isLibrary;
      return this;
    }

    public boolean getSupportsInstantRun() {
      return supportsInstantRun;
    }

    public MockModuleSpec setSupportsInstantRun(boolean supportsInstantRun) {
      this.supportsInstantRun = supportsInstantRun;
      return this;
    }

    public String getApplicationId() {
      return applicationId;
    }

    public MockModuleSpec setApplicationId(String applicationId) {
      this.applicationId = applicationId;
      return this;
    }

    public List<String> getJars() {
      return jars;
    }

    public MockModuleSpec addJar(String jar) {
      jars.add(jar);
      return this;
    }

    public List<String> getAars() {
      return aars;
    }

    public MockModuleSpec addAar(String aar) {
      aars.add(aar);
      return this;
    }

    public List<String> getNativeLibraries() {
      return nativeLibraries;
    }

    public MockModuleSpec addNativeLibrary(String nativeLibrary) {
      nativeLibraries.add(nativeLibrary);
      return this;
    }

    public String getCppBuildSystem() {
      return myCppBuildSystem;
    }

    public MockModuleSpec setCppBuildSystem(String cppBuildSystem) {
      myCppBuildSystem = cppBuildSystem;
      return this;
    }

    public String getNativeModelVersion() {
      return nativeModelVersion;
    }

    public MockModuleSpec setNativeModelVersion(String nativeModelVersion) {
      this.nativeModelVersion = nativeModelVersion;
      return this;
    }
  }
}