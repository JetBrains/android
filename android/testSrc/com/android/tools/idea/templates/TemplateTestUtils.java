/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.tools.idea.templates.Template.CATEGORY_APPLICATION;
import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_ANDROIDX_SUPPORT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_TOOLS_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_KOTLIN_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_LANGUAGE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_PACKAGE_NAME;
import static com.android.tools.idea.templates.TemplateTest.ATTR_CREATE_ACTIVITY;
import static com.android.tools.idea.templates.TemplateTest.TEST_FEWER_API_VERSIONS;
import static com.android.tools.idea.wizard.WizardConstants.MODULE_TEMPLATE_NAME;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.analytics.LoggedUsage;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.lint.LintIdeClient;
import com.android.tools.idea.lint.LintIdeIssueRegistry;
import com.android.tools.idea.lint.LintIdeRequest;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.KotlinSupport;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.inspections.lint.ProblemData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TemplateTestUtils {
  /**
   * Is the given api level interesting for testing purposes? This is used to skip gaps,
   * such that we for example only check say api 14, 16, 21, 23, etc -- versions where the <b>templates</b> are doing conditional changes.
   *
   * Note: To be EXTRA comprehensive, occasionally try returning true unconditionally here to test absolutely everything.
   */
  public static boolean isInterestingApiLevel(int api, int manualApi, boolean apiSensitiveTemplate) {
    // If a manual api version was specified then accept only that version:
    if (manualApi > 0) {
      return api == manualApi;
    }

    // For templates that aren't API sensitive, only test with latest API
    if (!apiSensitiveTemplate) {
      // Use HIGHEST_KNOWN_STABLE_API rather than HIGHEST_KNOWN_API which
      // can point to preview releases.
      return api == SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;
    }

    // Always accept the highest known version
    if (api == SdkVersionInfo.HIGHEST_KNOWN_STABLE_API) {
      return true;
    }

    // Relevant versions, used to prune down the set of targets we need to
    // check on. This is determined by looking at the minApi and minBuildApi
    // versions found in the template.xml files.
    switch (api) {
      case 14:
      case 16:
      case 21:
      case 23:
        return true;
      case 25:
      case 28:
        return !TEST_FEWER_API_VERSIONS;
      default:
        return false;
    }
  }


  @NotNull
  public static TestNewProjectWizardState createNewProjectState(boolean createWithProject, AndroidSdkData sdkData, Template moduleTemplate) {
    TestNewProjectWizardState projectState = new TestNewProjectWizardState(moduleTemplate);
    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    Template.convertApisToInt(moduleState.getParameters());
    moduleState.put(ATTR_CREATE_ACTIVITY, createWithProject);
    moduleState.put(ATTR_MODULE_NAME, "TestModule");
    moduleState.put(ATTR_PACKAGE_NAME, "test.pkg");
    new TemplateValueInjector(moduleState.getParameters())
      .addGradleVersions(null);

    BuildToolInfo buildTool = sdkData.getLatestBuildTool(false);
    if (buildTool != null) {
      moduleState.put(ATTR_BUILD_TOOLS_VERSION, buildTool.getRevision().toString());
    }

    return projectState;
  }

  @NotNull
  public static Template getModuleTemplateForFormFactor(@NotNull File templateFile) {
    Template activityTemplate = Template.createFromPath(templateFile);
    Template moduleTemplate = getDefaultModuleTemplate();
    TemplateMetadata activityMetadata = activityTemplate.getMetadata();
    assertNotNull(activityMetadata);
    String activityFormFactorName = activityMetadata.getFormFactor();
    if (activityFormFactorName != null) {
      FormFactor activityFormFactor = FormFactor.get(activityFormFactorName);
      TemplateManager manager = TemplateManager.getInstance();
      List<File> applicationTemplates = manager.getTemplatesInCategory(CATEGORY_APPLICATION);
      for (File formFactorTemplateFile : applicationTemplates) {
        TemplateMetadata metadata = manager.getTemplateMetadata(formFactorTemplateFile);
        if (metadata != null && metadata.getFormFactor() != null && FormFactor.get(metadata.getFormFactor()) == activityFormFactor) {
          moduleTemplate = Template.createFromPath(formFactorTemplateFile);
          break;
        }
      }
    }
    return moduleTemplate;
  }

  @NotNull
  public static Template getDefaultModuleTemplate() {
    return Template.createFromName(CATEGORY_PROJECTS, MODULE_TEMPLATE_NAME);
  }


  @NotNull
  public static RenderingContext createRenderingContext(
    @NotNull Template projectTemplate,
    @NotNull Project project,
    @NotNull File projectRoot,
    @NotNull File moduleRoot,
    @Nullable Map<String, Object> parameters) {
    RenderingContext.Builder builder = RenderingContext.Builder.newContext(projectTemplate, project)
      .withOutputRoot(projectRoot)
      .withModuleRoot(moduleRoot);

    if (parameters != null) {
      builder.withParams(parameters);
    }

    return builder.build();
  }


  /**
   * Runs lint and returns a message with information about the first issue with severity at least X or null if there are no such issues.
   */
  @Nullable
  public static String getLintIssueMessage(@NotNull Project project, @NotNull Severity maxSeverity, @NotNull Set<Issue> ignored) {
    BuiltinIssueRegistry registry = new LintIdeIssueRegistry();
    Map<Issue, Map<File, List<ProblemData>>> map = new HashMap<>();
    LintIdeClient client = LintIdeClient.forBatch(project, map, new AnalysisScope(project), new HashSet<>(registry.getIssues()));
    List<Module> modules = Arrays.asList(ModuleManager.getInstance(project).getModules());
    LintRequest request = new LintIdeRequest(client, project, null, modules, false);
    EnumSet<Scope> scope = EnumSet.allOf(Scope.class);
    scope.remove(Scope.CLASS_FILE);
    scope.remove(Scope.ALL_CLASS_FILES);
    scope.remove(Scope.JAVA_LIBRARIES);
    request.setScope(scope);
    LintDriver driver = new LintDriver(registry, client, request);
    driver.analyze();
    if (!map.isEmpty()) {
      for (Map<File, List<ProblemData>> fileListMap : map.values()) {
        for (Map.Entry<File, List<ProblemData>> entry : fileListMap.entrySet()) {
          File file = entry.getKey();
          List<ProblemData> problems = entry.getValue();
          for (ProblemData problem : problems) {
            Issue issue = problem.getIssue();
            if (ignored.contains(issue)) {
              continue;
            }
            if (issue.getDefaultSeverity().compareTo(maxSeverity) < 0) {
              return "Found lint issue " + issue.getId() + " with severity " + issue.getDefaultSeverity() + " in " + file + " at " +
                     problem.getTextRange() + ": " + problem.getMessage();
            }
          }
        }
      }
    }
    return null;
  }

  public static void setAndroidSupport(
    boolean setAndroidx,
    @NotNull TestTemplateWizardState moduleState,
    @Nullable TestTemplateWizardState activityState) {
    moduleState.put(ATTR_ANDROIDX_SUPPORT, setAndroidx);
    if (activityState != null) {
      activityState.put(ATTR_ANDROIDX_SUPPORT, setAndroidx);
    }
  }

  /**
   * Validates this template to make sure it's supported
   *
   * @param currentMinSdk the minimum SDK in the project, or -1 or 0 if unknown (e.g. codename)
   * @param buildApi      the build API, or -1 or 0 if unknown (e.g. codename)
   * @return an error message, or null if there is no problem
   */
  @Nullable
  public static String validateTemplate(TemplateMetadata metadata, int currentMinSdk, int buildApi) {
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

  @NotNull
  public static String getModifiedProjectName(@NotNull String projectName, @Nullable TestTemplateWizardState activityState) {
    if (SystemInfo.isWindows) {
      return "app";
    }
    // Bug 137161906
    if (projectName.startsWith("BasicActivity") &&
        activityState != null &&
        Language.KOTLIN.toString().equals(activityState.getString(ATTR_LANGUAGE))) {
      return projectName;
    }

    String specialChars = "!@#$^&()_+=-.`~";
    String nonAsciiChars = "你所有的基地都属于我们";
    return projectName + specialChars + ',' + nonAsciiChars;
  }


  /**
   * Checks that the most recent log in myUsageTracker is a AndroidStudioEvent.EventKind.TEMPLATE_RENDER event with expected info.
   *
   * @param templateRenderer the expected value of usage.getStudioEvent().getTemplateRenderer(), where usage is the most recent logged usage
   * @param paramMap         the paramMap, containing kotlin support info for template render event
   */
  public static void verifyLastLoggedUsage(
    @NotNull TestUsageTracker usageTracker,
    @NotNull AndroidStudioEvent.TemplateRenderer templateRenderer,
    @NotNull Map<String, Object> paramMap) {
    List<LoggedUsage> usages = usageTracker.getUsages();
    assertFalse(usages.isEmpty());
    // get last logged usage
    LoggedUsage usage = usages.get(usages.size() - 1);
    assertEquals(AndroidStudioEvent.EventKind.TEMPLATE_RENDER, usage.getStudioEvent().getKind());
    assertEquals(templateRenderer, usage.getStudioEvent().getTemplateRenderer());
    assertTrue(paramMap.getOrDefault(ATTR_KOTLIN_VERSION, "unknown") instanceof String);
    assertEquals(
      KotlinSupport.newBuilder()
        .setIncludeKotlinSupport(paramMap.getOrDefault(ATTR_LANGUAGE, "Java").equals(Language.KOTLIN.toString()))
        .setKotlinSupportVersion((String)paramMap.getOrDefault(ATTR_KOTLIN_VERSION, "unknown")).build(),
      usage.getStudioEvent().getKotlinSupport());
  }
}
