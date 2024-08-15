/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run;

import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.ValidationErrorCompat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.run.state.DebuggerSettingsState;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.lang.AdditionalLanguagesHelper;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.qsync.QuerySync;
import com.google.idea.blaze.base.run.state.RunConfigurationFlagsState;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import java.awt.Component;
import java.util.List;
import javax.swing.JComponent;
import org.jdom.Element;

/** A shared state class for run configurations targeting Blaze Android rules. */
public class BlazeAndroidRunConfigurationCommonState implements RunConfigurationState {
  private static final String DEPLOY_TARGET_STATES_TAG = "android-deploy-target-states";
  private static final String USER_BLAZE_FLAG_TAG = "blaze-user-flag";
  private static final String USER_EXE_FLAG_TAG = "blaze-user-exe-flag";

  // "-c dbg" defines both the copt and strip flags below; however, we want to allow users to
  // override -c (to fastbuild or opt) without entirely compromising the generation of debug info.
  private static final ImmutableList<String> NATIVE_DEBUG_FLAGS =
      ImmutableList.of("--copt=-g", "--fission=no", "--strip=never", "-c", "dbg");

  private final RunConfigurationFlagsState blazeFlags;
  private final RunConfigurationFlagsState exeFlags;
  private final DebuggerSettingsState debuggerSettings;

  public BlazeAndroidRunConfigurationCommonState(String buildSystemName) {
    this.blazeFlags =
        new RunConfigurationFlagsState(USER_BLAZE_FLAG_TAG, buildSystemName + " flags:");
    this.exeFlags =
        new RunConfigurationFlagsState(
            USER_EXE_FLAG_TAG, "Executable flags (mobile-install only):");
    this.debuggerSettings = new DebuggerSettingsState(false);
  }

  public RunConfigurationFlagsState getBlazeFlagsState() {
    return blazeFlags;
  }

  public RunConfigurationFlagsState getExeFlagsState() {
    return exeFlags;
  }

  public boolean isNativeDebuggingEnabled() {
    return debuggerSettings.isNativeDebuggingEnabled();
  }

  public void setNativeDebuggingEnabled(boolean nativeDebuggingEnabled) {
    debuggerSettings.setNativeDebuggingEnabled(nativeDebuggingEnabled);
  }

  public ImmutableList<String> getExpandedBuildFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeInvocationContext context) {
    return ImmutableList.<String>builder()
        // Add Native Debugging flags first to allow for overriding -c dbg in .blazeproject or run
        // configurations.
        .addAll(getNativeDebuggerFlags())
        .addAll(
            BlazeFlags.blazeFlags(project, projectViewSet, command, BlazeContext.create(), context))
        .addAll(getBlazeFlagsState().getFlagsForExternalProcesses())
        .build();
  }

  private ImmutableList<String> getNativeDebuggerFlags() {
    return isNativeDebuggingEnabled() ? NATIVE_DEBUG_FLAGS : ImmutableList.of();
  }

  /**
   * We collect errors rather than throwing to avoid missing fatal errors by exiting early for a
   * warning.
   */
  public List<ValidationError> validate(Project project) {
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      return ImmutableList.of();
    }
    List<ValidationError> errors = Lists.newArrayList();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      errors.add(ValidationError.fatal("Project data missing. Please sync your project."));
      return errors;
    }

    if (isNativeDebuggingEnabled()
        && !blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.C)) {
      errors.add(
          ValidationErrorCompat.fatal(
              "Native debugging requires C language support.",
              () ->
                  AdditionalLanguagesHelper.enableLanguageSupport(
                      project, ImmutableList.of(LanguageClass.C))));
    }

    return errors;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    blazeFlags.readExternal(element);
    exeFlags.readExternal(element);
    debuggerSettings.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    blazeFlags.writeExternal(element);
    exeFlags.writeExternal(element);
    debuggerSettings.writeExternal(element);

    // Clear out legacy deploy target state element.
    element.removeChildren(DEPLOY_TARGET_STATES_TAG);
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new BlazeAndroidRunConfigurationCommonStateEditor(this, project);
  }

  private static class BlazeAndroidRunConfigurationCommonStateEditor
      implements RunConfigurationStateEditor {

    private final RunConfigurationStateEditor blazeFlagsEditor;
    private final RunConfigurationStateEditor exeFlagsEditor;
    private final RunConfigurationStateEditor debuggerSettingsEditor;

    BlazeAndroidRunConfigurationCommonStateEditor(
        BlazeAndroidRunConfigurationCommonState state, Project project) {
      blazeFlagsEditor = state.blazeFlags.getEditor(project);
      exeFlagsEditor = state.exeFlags.getEditor(project);
      debuggerSettingsEditor = state.debuggerSettings.getEditor(project);
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      BlazeAndroidRunConfigurationCommonState state =
          (BlazeAndroidRunConfigurationCommonState) genericState;
      blazeFlagsEditor.resetEditorFrom(state.blazeFlags);
      exeFlagsEditor.resetEditorFrom(state.exeFlags);
      debuggerSettingsEditor.resetEditorFrom(state.debuggerSettings);
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      BlazeAndroidRunConfigurationCommonState state =
          (BlazeAndroidRunConfigurationCommonState) genericState;
      blazeFlagsEditor.applyEditorTo(state.blazeFlags);
      exeFlagsEditor.applyEditorTo(state.exeFlags);
      debuggerSettingsEditor.applyEditorTo(state.debuggerSettings);
    }

    @Override
    public JComponent createComponent() {
      List<Component> result =
          Lists.newArrayList(
              blazeFlagsEditor.createComponent(),
              exeFlagsEditor.createComponent(),
              debuggerSettingsEditor.createComponent());
      return UiUtil.createBox(result);
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      blazeFlagsEditor.setComponentEnabled(enabled);
      exeFlagsEditor.setComponentEnabled(enabled);
      debuggerSettingsEditor.setComponentEnabled(enabled);
    }
  }
}
