/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.npw.ConfigureAndroidModuleStep;
import com.android.tools.idea.npw.NewProjectWizardState;
import com.android.tools.idea.sdk.VersionCheck;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.android.tools.lint.checks.ManifestDetector;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.*;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.npw.FormFactorUtils.ATTR_MODULE_NAME;
import static com.android.tools.idea.npw.NewModuleWizardState.ATTR_CREATE_ACTIVITY;
import static com.android.tools.idea.npw.NewModuleWizardState.ATTR_PROJECT_LOCATION;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TARGET_API;

/**
 * Test for template instantiation.
 * <p>
 * Remaining work on templates:
 * <ul>
 *   <li>Make the project templates work for API=1 (currently requires API 7); with API 1
 *       you get a build error because included libraries have targetSdkVersion higher than 1</li>
 *   <li>Fix type conversion, to make the service and fragment templates work</li>
 *   <li>Fix compilation errors in the remaining templates</li>
 *   <li>Should abstract out the state initialization code from the UI such that we
 *       can use the same code path from the test</li>
 * </ul>
 *
 * Remaining work on template test:
 * <ul>
 *   <li>Fix clean model syncing, and hook up clean lint checks</li>
 *   <li>We should test more combinations of parameters</li>
 *   <li>We should test all combinations of build tools</li>
 *   <li>Test creating a project <b>without</b> a template</li>
 * </ul>
 */
public class TemplateTest extends AndroidGradleTestCase {
  /**
   * Whether we should run comprehensive tests or not. This flag allows a simple run to just check a small set of
   * template combinations, and when the flag is set on the build server, a much more comprehensive battery of
   * checks to be performed.
   */
  private static final boolean COMPREHENSIVE =
    Boolean.parseBoolean(System.getProperty("com.android.tools.idea.templates.TemplateTest.COMPREHENSIVE")) ||
    Boolean.TRUE.toString().equals(System.getenv("com.android.tools.idea.templates.TemplateTest.COMPREHENSIVE"));

  /**
   * Whether we should run these tests or not.
   */
  private static final boolean DISABLED =
    Boolean.parseBoolean(System.getProperty("DISABLE_STUDIO_TEMPLATE_TESTS")) ||
    Boolean.TRUE.toString().equals(System.getenv("DISABLE_STUDIO_TEMPLATE_TESTS"));

  /** Whether we should enforce that lint passes cleanly on the projects */
  private static final boolean CHECK_LINT = false; // Needs work on closing projects cleanly
  private static final boolean ALLOW_WARNINGS = true; // TODO: Provide finer granularity

  /** Manual sdk version selections **/
  private static final int MANUAL_BUILD_API = Integer.parseInt(System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_BUILD_API", "-1"));
  private static final int MANUAL_MIN_API = Integer.parseInt(System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_MIN_API", "-1"));
  private static final int MANUAL_TARGET_API = Integer.parseInt(System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_TARGET_API", "-1"));

  /**
   * The following templates are known to be broken! We need to work through these and fix them such that tests
   * on them can be re-enabled.
   */
  private static final Set<String> KNOWN_BROKEN = Sets.newHashSet();
  static {

  }

  /**
   * Flags used to quickly check each template once (for one version), to get
   * quicker feedback on whether something is broken instead of waiting for
   * all the versions for each template first
   */
  private static final boolean TEST_VARIABLE_COMBINATIONS = true;
  private static final boolean TEST_FEWER_API_VERSIONS = !COMPREHENSIVE;
  private static final boolean TEST_JUST_ONE_MIN_SDK = !COMPREHENSIVE;
  private static final boolean TEST_JUST_ONE_BUILD_TARGET = !COMPREHENSIVE;
  private static final boolean TEST_JUST_ONE_TARGET_SDK_VERSION = !COMPREHENSIVE;
  private static int ourCount = 0;

  private static boolean ourValidatedTemplateManager;

  private final StringEvaluator myStringEvaluator = new StringEvaluator();

  public TemplateTest() {
  }

  @Override
  protected boolean createDefaultProject() {
    // We'll be creating projects manually
    //return false;
    return true; // Doesn't work yet; find out why!
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myApiSensitiveTemplate = true;

    if (!ourValidatedTemplateManager) {
      ourValidatedTemplateManager = true;
      File templateRootFolder = TemplateManager.getTemplateRootFolder();
      if (templateRootFolder == null) {
        AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
        if (sdkData == null) {
          fail("Couldn't find SDK manager");
        } else {
          System.out.println("recentSDK required= " + requireRecentSdk());
          System.out.println("getTestSdkPath= " + getTestSdkPath());
          System.out.println("getPlatformDir=" + getPlatformDir());
          String location = sdkData.getLocation().getPath();
          System.out.println("Using SDK at " + location);
          VersionCheck.VersionCheckResult result = VersionCheck.checkVersion(location);
          System.out.println("Version check=" + result.getRevision());
          File file = new File(location);
          if (!file.exists()) {
            System.out.println("SDK doesn't exist");
          } else {
            File folder = new File(location, FD_TOOLS + File.separator + FD_TEMPLATES);
            boolean exists = folder.exists();
            System.out.println("Template folder exists=" + exists + " for " + folder);
          }
        }
      }
    }
  }

  /**
   * If true, check this template with all the interesting (
   * {@link #isInterestingApiLevel(int)}) api versions
   */
  private boolean myApiSensitiveTemplate;

  /**
   * Set of templates already tested with separate unit test; remainder is
   * checked in {@link #testCreateRemainingTemplates()}
   */
  private static final Set<String> ourTemplatesChecked = Sets.newHashSet();


  /**
   * Is the given api level interesting for testing purposes? This is used to
   * skip gaps, such that we for example only check say api 8, 9, 11, 14, etc
   * -- versions where the <b>templates</b> are doing conditional changes. To
   * be EXTRA comprehensive, occasionally try returning true unconditionally
   * here to test absolutely everything.
   */
  private boolean isInterestingApiLevel(int api, int manualApi) {
    // If a manual api version was specified then accept only that version:
    if (manualApi > 0) {
      return api == manualApi;
    }

    // For templates that aren't API sensitive, only test with latest API
    if (!myApiSensitiveTemplate) {
      return api == SdkVersionInfo.HIGHEST_KNOWN_API;
    }

    // Always accept the highest known version
    if (api == SdkVersionInfo.HIGHEST_KNOWN_API) {
      return true;
    }

    // Relevant versions, used to prune down the set of targets we need to
    // check on. This is determined by looking at the minApi and minBuildApi
    // versions found in the template.xml files.
    switch (api) {
      case 1:
      case 7:
      case 11:
      case 14:
      case 21:
        return true;
      case 9:
      case 13:
      case 8:
      case 3:
        return !TEST_FEWER_API_VERSIONS;
      default:
        return false;
    }
  }

  public void testNewBlankActivity() throws Exception {
    checkCreateTemplate("activities", "BlankActivity", false);
  }

  public void testNewProjectWithBlankActivity() throws Exception {
    checkCreateTemplate("activities", "BlankActivity", true);
  }

  public void testNewEmptyActivity() throws Exception {
    checkCreateTemplate("activities", "EmptyActivity", false);
  }

  public void testNewProjectWithEmptyActivity() throws Exception {
    checkCreateTemplate("activities", "EmptyActivity", true);
  }

  public void testNewTabbedActivity() throws Exception {
    checkCreateTemplate("activities", "TabbedActivity", false);
  }

  public void testNewProjectWithTabbedActivity() throws Exception {
    checkCreateTemplate("activities", "TabbedActivity", true);
  }

  public void testNewNavigationDrawerActivity() throws Exception {
    checkCreateTemplate("activities", "NavigationDrawerActivity", false);
  }

  public void testNewProjectWithNavigationDrawerActivity() throws Exception {
    checkCreateTemplate("activities", "NavigationDrawerActivity", true);
  }

  public void testNewMasterDetailFlow() throws Exception {
    checkCreateTemplate("activities", "MasterDetailFlow", false);
  }

  public void testNewProjectWithMasterDetailFlow() throws Exception {
    checkCreateTemplate("activities", "MasterDetailFlow", true);
  }

  public void testNewFullscreenActivity() throws Exception {
    checkCreateTemplate("activities", "FullscreenActivity", false);
  }

  public void testNewProjectWithFullscreenActivity() throws Exception {
    checkCreateTemplate("activities", "FullscreenActivity", true);
  }

  public void testNewLoginActivity() throws Exception {
    checkCreateTemplate("activities", "LoginActivity", false);
  }

  public void testNewProjectWithLoginActivity() throws Exception {
    checkCreateTemplate("activities", "LoginActivity", true);
  }

  public void testNewScrollActivity() throws Exception {
    checkCreateTemplate("activities", "ScrollActivity", false);
  }

  public void testNewProjectWithScrollActivity() throws Exception {
    checkCreateTemplate("activities", "ScrollActivity", true);
  }

  public void testNewSettingsActivity() throws Exception {
    checkCreateTemplate("activities", "SettingsActivity", false);
  }

  public void testNewProjectWithSettingsActivity() throws Exception {
    checkCreateTemplate("activities", "SettingsActivity", true);
  }

  // Non-activities

  public void testNewBroadcastReceiver() throws Exception {
    // No need to try this template with multiple platforms, one is adequate
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "BroadcastReceiver");
  }

  public void testNewContentProvider() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "ContentProvider");
  }

  public void testNewCustomView() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "CustomView");
  }

  public void testNewIntentService() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "IntentService");
  }

  public void testNewNotification() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "Notification");
  }

  public void testNewDayDream() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "Daydream");
  }

  public void testNewListFragment() throws Exception {
    myApiSensitiveTemplate = true;
    checkCreateTemplate("other", "ListFragment");
  }

  public void testNewAppWidget() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AppWidget");
  }

  public void testNewBlankFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "BlankFragment");
  }

  public void testNewService() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "Service");
  }

  public void testNewPlusOneFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "PlusOneFragment");
  }

  public void testNewAidlFile() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AidlFile");
  }

  public void testNewLayoutResourceFile() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "LayoutResourceFile");
  }

  public void testNewValueResourceFile() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "ValueResourceFile");
  }

  public void testCreateRemainingTemplates() throws Exception {
    if (DISABLED) {
      return;
    }
    ourCount = 0;
    long begin = System.currentTimeMillis();
    TemplateManager manager = TemplateManager.getInstance();
    List<File> other = manager.getTemplates("other");
    for (File templateFile : other) {
      if (!haveChecked(templateFile, false)) {
        checkTemplate(templateFile, false);
      }
    }
    // Also try creating templates, not as part of creating a project
    List<File> activities = manager.getTemplates("activities");
    for (File templateFile : activities) {
      if (!haveChecked(templateFile, false)) {
        checkTemplate(templateFile, false);
      }
      if (!haveChecked(templateFile, true)) {
        checkTemplate(templateFile, true);
      }
    }
    long end = System.currentTimeMillis();
    System.out.println("Successfully checked " + ourCount + " template permutations in "
                       + ((end - begin) / (1000 * 60)) + " minutes");
  }

  public void testJdk7() throws Exception {
    if (DISABLED) {
      return;
    }
    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    assertNotNull(sdkData);

    if (ConfigureAndroidModuleStep.isJdk7Supported(sdkData)) {
      IAndroidTarget[] targets = sdkData.getTargets();
      IAndroidTarget target = targets[targets.length - 1];
      Map<String,Object> overrides = Maps.newHashMap();
      overrides.put(ATTR_JAVA_VERSION, "1.7");
      NewProjectWizardState state = createNewProjectState(true, sdkData);

      // TODO: Allow null activity state!
      File activity = findTemplate("activities", "BlankActivity");
      TemplateWizardState activityState = state.getActivityTemplateState();
      assertNotNull(activity);
      activityState.setTemplateLocation(activity);

      checkApiTarget(19, 19, target, state, "Test17", null, overrides);
    } else {
      System.out.println("JDK 7 not supported by current SDK manager: not testing");
    }
  }

  public void testJdk5() throws Exception {
    if (DISABLED) {
      return;
    }
    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    assertNotNull(sdkData);

    IAndroidTarget[] targets = sdkData.getTargets();
    IAndroidTarget target = targets[targets.length - 1];
    Map<String,Object> overrides = Maps.newHashMap();
    overrides.put(ATTR_JAVA_VERSION, "1.5");
    NewProjectWizardState state = createNewProjectState(true, sdkData);

    // TODO: Allow null activity state!
    File activity = findTemplate("activities", "BlankActivity");
    TemplateWizardState activityState = state.getActivityTemplateState();
    assertNotNull(activity);
    activityState.setTemplateLocation(activity);

    checkApiTarget(8, 18, target, state, "Test15", null, overrides);
  }

  public void testTemplateFormatting() throws Exception {
    Template template = Template.createFromPath(new File(getTestDataPath(), FileUtil.join("templates", "TestTemplate")).getCanonicalFile());
    RenderingContext context = RenderingContext.Builder.newContext(template, myFixture.getProject())
      .withOutputRoot(new File(myFixture.getTempDirPath())).withModuleRoot(new File("dummy")).build();
    template.render(context);
    FileDocumentManager.getInstance().saveAllDocuments();
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    VirtualFile desired = fileSystem.findFileByIoFile(new File(getTestDataPath(),
                                                               FileUtil.join("templates", "TestTemplate", "MergedStringsFile.xml")));
    VirtualFile actual = fileSystem.findFileByIoFile(new File(myFixture.getTempDirPath(),
                                                              FileUtil.join("values", "TestTargetResourceFile.xml")));
    desired.refresh(false, false);
    actual.refresh(false, false);
    PlatformTestUtil.assertFilesEqual(desired, actual);
  }

  public void testRelatedParameters() throws Exception {
    Template template = Template.createFromPath(new File(getTestDataPath(), FileUtil.join("templates", "TestTemplate")));
    Parameter layoutName = template.getMetadata().getParameter("layoutName");
    Parameter activityClass = template.getMetadata().getParameter("activityClass");
    Parameter mainFragment = template.getMetadata().getParameter("mainFragment");
    Parameter activityTitle = template.getMetadata().getParameter("activityTitle");
    Parameter detailsActivity = template.getMetadata().getParameter("detailsActivity");
    Parameter detailsLayoutName = template.getMetadata().getParameter("detailsLayoutName");
    assertSameElements(template.getMetadata().getRelatedParams(layoutName), detailsLayoutName);
    assertSameElements(template.getMetadata().getRelatedParams(activityClass), detailsActivity, mainFragment);
    assertSameElements(template.getMetadata().getRelatedParams(mainFragment), detailsActivity, activityClass);
    assertEmpty(template.getMetadata().getRelatedParams(activityTitle));
    assertSameElements(template.getMetadata().getRelatedParams(detailsActivity), activityClass, mainFragment);
    assertSameElements(template.getMetadata().getRelatedParams(detailsLayoutName), layoutName);
  }

  // ---- Test support code below ----

  /** Checks whether we've already checked the given template in a new project or existing project context */
  private static boolean haveChecked(String category, String name, boolean createWithProject) {
    String key = getCheckKey(category, name, createWithProject);
    return ourTemplatesChecked.contains(key);
  }

  /** Checks whether we've already checked the given template in a new project or existing project context */
  private static boolean haveChecked(File templateFile, boolean createWithProject) {
    return haveChecked(templateFile.getParentFile().getName(), templateFile.getName(), createWithProject);
  }

  /** Marks that we've already checked the given template in a new project or existing project context */
  private static void markChecked(String category, String name, boolean createWithProject) {
    String key = getCheckKey(category, name, createWithProject);
    ourTemplatesChecked.add(key);
  }

  /** Marks that we've already checked the given template in a new project or existing project context */
  private static void markChecked(File templateFile, boolean createWithProject) {
    markChecked(templateFile.getParentFile().getName(), templateFile.getName(), createWithProject);
  }

  /** Checks the given template in the given category, adding it to an existing project */
  private void checkCreateTemplate(String category, String name) throws Exception {
    checkCreateTemplate(category, name, false);
  }

  private static String getCheckKey(String category, String name, boolean createWithProject) {
    return category + ':' + name + ':' + createWithProject;
  }

  /**
   * Checks the given template in the given category
   *
   * @param category the template category
   * @param name the template name
   * @param createWithProject whether the template should be created as part of creating the project (which should
   *                          only be done for activities), or whether it should be added as as a separate template
   *                          into an existing project (which is created first, followed by the template)
   * @throws Exception
   */
  private void checkCreateTemplate(String category, String name, boolean createWithProject) throws Exception {
    if (DISABLED) {
      return;
    }
    File templateFile = findTemplate(category, name);
    assertNotNull(templateFile);
    if (haveChecked(templateFile, createWithProject)) {
      return;
    }
    if (KNOWN_BROKEN.contains(templateFile.getName())) {
      return;
    }
    markChecked(templateFile, createWithProject);
    Stopwatch stopwatch = Stopwatch.createStarted();
    checkTemplate(templateFile, createWithProject);
    stopwatch.stop();
    System.out.println("Checked " + templateFile.getName() + " successfully in " + stopwatch.toString());
  }

  private File findTemplate(String category, String name) {
    ensureSdkManagerAvailable();
    File templateRootFolder = TemplateManager.getTemplateRootFolder();
    assertNotNull(templateRootFolder);
    File file = new File(templateRootFolder, category + File.separator + name);
    assertTrue(file.getPath(), file.exists());
    return file;
  }

  private static NewProjectWizardState createNewProjectState(boolean createWithProject, AndroidSdkData sdkData) {
    final NewProjectWizardState values = new NewProjectWizardState();
    assertNotNull(values);
    Template.convertApisToInt(values.getParameters());
    values.put(ATTR_CREATE_ACTIVITY, createWithProject);
    values.put(ATTR_GRADLE_VERSION, GRADLE_LATEST_VERSION);
    values.put(ATTR_GRADLE_PLUGIN_VERSION, GRADLE_PLUGIN_RECOMMENDED_VERSION);
    values.put(ATTR_MODULE_NAME, "TestModule");
    values.put(ATTR_PACKAGE_NAME, "test.pkg");

    // TODO: Test the icon generator too
    values.put(ATTR_CREATE_ICONS, false);

    final BuildToolInfo buildTool = sdkData.getLatestBuildTool();
    if (buildTool != null) {
      values.put(ATTR_BUILD_TOOLS_VERSION, buildTool.getRevision().toString());
    }
    return values;
  }

  private void checkTemplate(File templateFile, boolean createWithProject) throws Exception {
    if (KNOWN_BROKEN.contains(templateFile.getName())) {
      return;
    }

    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    assertNotNull(sdkData);

    final NewProjectWizardState values = createNewProjectState(createWithProject, sdkData);

    String projectNameBase = "TestProject" + templateFile.getName();

    TemplateWizardState state = values.getActivityTemplateState();
    state.setTemplateLocation(templateFile);

    // Iterate over all (valid) combinations of build target, minSdk and targetSdk
    // TODO: Assert that the SDK manager has a minimum set of SDKs installed needed to be certain
    // the test is comprehensive
    // For now make sure there's at least one
    boolean ranTest = false;

    IAndroidTarget[] targets = sdkData.getTargets();
    for (int i = targets.length - 1; i >= 0; i--) {
      IAndroidTarget target = targets[i];
      if (!target.isPlatform()) {
        continue;
      }
      if (!isInterestingApiLevel(target.getVersion().getApiLevel(), MANUAL_BUILD_API)) {
        continue;
      }

      TemplateMetadata activityMetadata = state.getTemplateMetadata();
      TemplateMetadata projectMetadata = values.getTemplateMetadata();
      assertNotNull(activityMetadata);
      assertNotNull(projectMetadata);

      int lowestSupportedApi = Math.max(projectMetadata.getMinSdk(), activityMetadata.getMinSdk());

      for (int minSdk = lowestSupportedApi;
           minSdk <= SdkVersionInfo.HIGHEST_KNOWN_API;
           minSdk++) {
        // Don't bother checking *every* single minSdk, just pick some interesting ones
        if (!isInterestingApiLevel(minSdk, MANUAL_MIN_API)) {
          continue;
        }

        for (int targetSdk = minSdk;
             targetSdk <= SdkVersionInfo.HIGHEST_KNOWN_API;
             targetSdk++) {
          if (!isInterestingApiLevel(targetSdk, MANUAL_TARGET_API)) {
            continue;
          }

          String status = validateTemplate(projectMetadata, minSdk, target.getVersion().getApiLevel());
          if (status != null) {
            continue;
          }

          // Also make sure activity is enabled for these versions
          status = validateTemplate(activityMetadata, minSdk, target.getVersion().getApiLevel());
          if (status != null) {
            continue;
          }

          // Iterate over all new new project templates

          // should I try all options of theme with all platforms?
          // or just try all platforms, with one setting for each?
          // doesn't seem like I need to multiply
          // just pick the best setting that applies instead for each platform
          Collection<Parameter> parameters = projectMetadata.getParameters();
          projectParameters:
          for (Parameter parameter : parameters) {
            List<Element> options = parameter.getOptions();
            if (parameter.type == Parameter.Type.ENUM) {
              assertNotNull(parameter.id);
              for (Element element : options) {
                Option option = Option.get(element);
                String optionId = option.id;
                int optionMinSdk = option.minSdk;
                int optionMinBuildApi = option.minBuild;
                if (optionMinSdk <= minSdk &&
                    optionMinBuildApi <= target.getVersion().getApiLevel()) {
                  values.put(parameter.id, optionId);
                  if (parameter.id.equals("baseTheme")) {
                    String base = projectNameBase
                                  + "_min_" + minSdk
                                  + "_target_" + targetSdk
                                  + "_build_" + target.getVersion().getApiLevel()
                                  + "_theme_" + optionId;
                    checkApiTarget(minSdk, targetSdk, target, values, base, state, null);
                    ranTest = true;
                    if (!TEST_VARIABLE_COMBINATIONS) {
                      break projectParameters;
                    }
                  }
                }
              }
            }
          }

          if (TEST_JUST_ONE_TARGET_SDK_VERSION) {
            break;
          }
        }

        if (TEST_JUST_ONE_MIN_SDK) {
          break;
        }
      }

      if (TEST_JUST_ONE_BUILD_TARGET) {
        break;
      }
    }
    assertTrue("Didn't run any tests! Make sure you have the right platforms installed.", ranTest);
  }

  /** Checks creating the given project and template for the given SDK versions */
  private void checkApiTarget(
    int minSdk,
    int targetSdk,
    @NonNull IAndroidTarget target,
    @NonNull NewProjectWizardState projectValues,
    @NonNull String projectNameBase,
    @Nullable TemplateWizardState templateValues,
    @Nullable Map<String,Object> overrides) throws Exception {
    Boolean createActivity = (Boolean)projectValues.get(ATTR_CREATE_ACTIVITY);
    if (createActivity == null) {
      createActivity = true;
    }
    TemplateWizardState values = createActivity ? projectValues.getActivityTemplateState() : templateValues;
    assertNotNull(values);

    projectValues.put(ATTR_MIN_API, Integer.toString(minSdk));
    projectValues.put(ATTR_MIN_API_LEVEL, minSdk);
    projectValues.put(ATTR_TARGET_API, targetSdk);
    projectValues.put(ATTR_TARGET_API_STRING, Integer.toString(targetSdk));
    projectValues.put(ATTR_BUILD_API, target.getVersion().getApiLevel());
    projectValues.put(ATTR_BUILD_API_STRING, TemplateMetadata.getBuildApiString(target.getVersion()));
    assertNotNull(values);

    // Next check all other parameters, cycling through booleans and enums.
    Template templateHandler = values.getTemplate();
    assertNotNull(templateHandler);
    TemplateMetadata template = templateHandler.getMetadata();
    assertNotNull(template);
    Collection<Parameter> parameters = template.getParameters();

    if (!createActivity) {
      values.setParameterDefaults();
    }

    if (overrides != null) {
      for (Map.Entry<String, Object> entry : overrides.entrySet()) {
        values.put(entry.getKey(), entry.getValue());
      }
    }

    String projectName;
    for (Parameter parameter : parameters) {
      if (parameter.type == Parameter.Type.SEPARATOR || parameter.type == Parameter.Type.STRING) {
        // TODO: Consider whether we should attempt some strings here
        continue;
      }
      assertNotNull(parameter.id);

      // The initial (default value); revert to this one after cycling,
      Object initial = values.get(parameter.id);

      if (parameter.type == Parameter.Type.ENUM) {
        List<Element> options = parameter.getOptions();
        for (Element element : options) {
          Option option = Option.get(element);
          String optionId = option.id;
          int optionMinSdk = option.minSdk;
          int optionMinBuildApi = option.minBuild;
          int projectMinApi = projectValues.getInt(ATTR_MIN_API_LEVEL);
          int projectBuildApi = projectValues.getInt(ATTR_BUILD_API);
          if (projectMinApi >= optionMinSdk &&
              projectBuildApi >= optionMinBuildApi) {
            values.put(parameter.id, optionId);
            projectName = projectNameBase + "_" + parameter.id + "_" + optionId;
            checkProject(projectName, projectValues, templateValues);
            if (!TEST_VARIABLE_COMBINATIONS) {
              break;
            }
          }
        }
      } else {
        assert parameter.type == Parameter.Type.BOOLEAN;
        if (parameter.id.equals(ATTR_IS_LAUNCHER) && createActivity) {
          // Skipping this one: always true when launched from new project
          continue;
        }
        boolean value = false;
        //noinspection ConstantConditions
        values.put(parameter.id, value);
        //noinspection ConstantConditions
        projectName = projectNameBase + "_" + parameter.id + "_" + value;
        checkProject(projectName, projectValues, templateValues);

        if (!TEST_VARIABLE_COMBINATIONS) {
          break;
        }

        value = true;
        //noinspection ConstantConditions
        values.put(parameter.id, value);
        //noinspection ConstantConditions
        projectName = projectNameBase + "_" + parameter.id + "_" + value;
        checkProject(projectName, projectValues, templateValues);
      }
      values.put(parameter.id, initial);
    }
    projectName = projectNameBase + "_default";
    checkProject(projectName, projectValues, templateValues);
  }

  private static class Option {
    private String id;
    private int minSdk;
    private int minBuild;

    public Option(String id, int minSdk, int minBuild) {
      this.id = id;
      this.minSdk = minSdk;
      this.minBuild = minBuild;
    }

    private static Option get(Element option) {
      String optionId = option.getAttribute(ATTR_ID);
      String minApiString = option.getAttribute(ATTR_MIN_API);
      int optionMinSdk = 1;
      if (minApiString != null && !minApiString.isEmpty()) {
        try {
          optionMinSdk = Integer.parseInt(minApiString);
        } catch (NumberFormatException e) {
          // Templates aren't allowed to contain codenames, should
          // always be an integer
          optionMinSdk = 1;
          fail(e.toString());
        }
      }
      String minBuildApiString = option.getAttribute(ATTR_MIN_BUILD_API);
      int optionMinBuildApi = 1;
      if (minBuildApiString != null && !minBuildApiString.isEmpty()) {
        try {
          optionMinBuildApi = Integer.parseInt(minBuildApiString);
        } catch (NumberFormatException e) {
          // Templates aren't allowed to contain codenames, should
          // always be an integer
          optionMinBuildApi = 1;
          fail(e.toString());
        }
      }

      return new Option(optionId, optionMinSdk, optionMinBuildApi);
    }
  }

  private void checkProject(@NonNull String projectName,
                            @NonNull NewProjectWizardState projectValues,
                            @Nullable final TemplateWizardState templateValues) throws Exception {
    final String modifiedProjectName = projectName + "!@#$^&()_+=-,.`~你所有的基地都属于我们";
    ourCount++;
    projectValues.put(ATTR_RES_OUT, null);
    projectValues.put(ATTR_SRC_OUT, null);
    projectValues.put(ATTR_MANIFEST_OUT, null);
    projectValues.put(ATTR_TEST_OUT, null);

    JavaCodeInsightTestFixture fixture = null;
    File projectDir = null;
    try {
      projectValues.put(ATTR_MODULE_NAME, modifiedProjectName);
      IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
      TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = factory.createFixtureBuilder(modifiedProjectName);
      fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
      fixture.setUp();

      final Project project = fixture.getProject();
      projectDir = new File(project.getBasePath());
      projectValues.put(ATTR_PROJECT_LOCATION, projectDir.getPath());

      // We only need to sync the model if lint needs to look at the synced project afterwards
      boolean syncModel = CHECK_LINT;

      //noinspection ConstantConditions
      createProject(fixture, projectValues, syncModel);

      if (templateValues != null && !projectValues.getBoolean(ATTR_CREATE_ACTIVITY)) {
        templateValues.put(ATTR_PROJECT_LOCATION, projectDir.getPath());
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            File projectRoot = VfsUtilCore.virtualToIoFile(project.getBaseDir());
            Template template = templateValues.getTemplate();
            assert template != null;
            File moduleRoot = new File(projectRoot, modifiedProjectName);
            templateValues.put(ATTR_MODULE_NAME, moduleRoot.getName());
            templateValues.populateDirectoryParameters();
            RenderingContext context = RenderingContext.Builder.newContext(template, project).withOutputRoot(moduleRoot)
              .withModuleRoot(moduleRoot).withParams(templateValues.getParameters()).build();
            template.render(context);
            // Add in icons if necessary
            if (templateValues.getTemplateMetadata() != null  && templateValues.getTemplateMetadata().getIconName() != null) {
              File drawableFolder = new File(FileUtil.join(templateValues.getString(ATTR_RES_OUT)),
                                       FileUtil.join("drawable"));
              drawableFolder.mkdirs();
              String fileName = myStringEvaluator.evaluate(templateValues.getTemplateMetadata().getIconName(),
                                                           templateValues.getParameters());
              File iconFile = new File(drawableFolder, fileName + SdkConstants.DOT_XML);
              File sourceFile = new File(getTestDataPath(), FileUtil.join("drawables", "progress_horizontal.xml"));
              try {
                FileUtil.copy(sourceFile, iconFile);
              }
              catch (IOException e) {
                fail(e.getMessage());
              }
            }
          }
        });
      }



      assertNotNull(project);
      System.out.println("Checking project " + projectName + " in " + project.getBaseDir());

      assertBuildsCleanly(project, ALLOW_WARNINGS);
      if (CHECK_LINT) {
        assertLintsCleanly(project, Severity.INFORMATIONAL, Sets.newHashSet(ManifestDetector.TARGET_NEWER));
        // TODO: Check for other warnings / inspections, such as unused imports?
      }
    } finally {
      if (fixture != null) {
        fixture.tearDown();
      }

      Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
      assertTrue(openProjects.length <= 1); // 1: the project created by default by the test case

      // Clean up; ensure that we don't bleed contents through to the next iteration
      if (projectDir != null && projectDir.exists()) {
        FileUtil.delete(projectDir);
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir);
      }
    }
  }

  /**
   * Validates this template to make sure it's supported
   *
   * @param currentMinSdk the minimum SDK in the project, or -1 or 0 if unknown (e.g. codename)
   * @param buildApi the build API, or -1 or 0 if unknown (e.g. codename)
   *
   * @return an error message, or null if there is no problem
   */
  @Nullable
  private static String validateTemplate(TemplateMetadata metadata, int currentMinSdk, int buildApi) {
    if (!metadata.isSupported()) {
      return "This template requires a more recent version of Android Studio. Please update.";
    }
    int templateMinSdk = metadata.getMinSdk();
    if (templateMinSdk > currentMinSdk && currentMinSdk >= 1) {
      return String.format("This template requires a minimum SDK version of at least %1$d, and the current min version is %2$d",
                           templateMinSdk, currentMinSdk);
    }
    int templateMinBuildApi = metadata.getMinBuildApi();
    if (templateMinBuildApi > buildApi && buildApi >= 1) {
      return String.format("This template requires a build target API version of at least %1$d, and the current version is %2$d",
                           templateMinBuildApi, buildApi);
    }

    return null;
  }
}
