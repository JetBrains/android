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
package com.google.idea.blaze.base.run;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.lang.buildfile.references.LabelUtils;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider.TargetState;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.ConsoleOutputFileSettingsUi;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.google.idea.blaze.base.run.targetfinder.FuturesUtil;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.google.idea.blaze.base.run.ui.TargetExpressionListUi;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.LocatableRunConfigurationOptions;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.JComponent;
import org.jdom.Element;

/** A run configuration which executes Blaze commands. */
public class BlazeCommandRunConfiguration
    extends LocatableConfigurationBase<LocatableRunConfigurationOptions>
    implements BlazeRunConfiguration,
        ModuleRunProfile,
        RunConfigurationWithSuppressedDefaultDebugAction {

  private static final Logger logger = Logger.getInstance(BlazeCommandRunConfiguration.class);

  /**
   * Attributes or tags which are common to all run configuration types. We don't want to interfere
   * with the (de)serialization of these.
   *
   * <p>This is here for backwards compatibility deserializing older-style run configurations
   * without the top-level BLAZE_SETTINGS_TAG element.
   */
  private static final ImmutableSet<String> COMMON_SETTINGS =
      ImmutableSet.of(
          "name",
          "nameIsGenerated",
          "default",
          "temporary",
          "method",
          "type",
          "factoryName",
          "selected",
          "option",
          "folderName",
          "editBeforeRun",
          "activateToolWindowBeforeRun",
          "tempConfiguration");

  /**
   * All blaze-specific settings are serialized under this tag, to distinguish them from common
   * settings.
   */
  private static final String BLAZE_SETTINGS_TAG = "blaze-settings";

  private static final String HANDLER_ATTR = "handler-id";
  private static final String TARGET_TAG = "blaze-target";
  private static final String KIND_ATTR = "kind";
  private static final String KEEP_IN_SYNC_TAG = "keep-in-sync";
  private static final String CONTEXT_ELEMENT_ATTR = "context-element";

  /** The blaze-specific parts of the last serialized state of the configuration. */
  private Element blazeElementState = new Element(BLAZE_SETTINGS_TAG);

  /**
   * Used when we don't yet know all the configuration details, but want to provide a 'run/debug'
   * context action anyway.
   */
  @Nullable private volatile PendingRunConfigurationContext pendingContext;

  /** Set up a run configuration with a not-yet-known target pattern. */
  public void setPendingContext(PendingRunConfigurationContext pendingContext) {
    this.pendingContext = pendingContext;
    this.targetPatterns = ImmutableList.of();
    this.targetKindString = null;
    this.contextElementString = pendingContext.getSourceElementString();
    updateHandler();
    EventLoggingService.getInstance().logEvent(getClass(), "async-run-config");
  }

  public void clearPendingContext() {
    this.pendingContext = null;
  }

  /**
   * Returns true if this was previously a pending run configuration, but it turned out to be
   * invalid. We remove these from the project periodically.
   */
  boolean pendingSetupFailed() {
    PendingRunConfigurationContext pendingContext = this.pendingContext;
    if (pendingContext == null || !pendingContext.isDone()) {
      return false;
    }
    if (targetPatterns.isEmpty()) {
      return true;
    }
    // setup failed, but it still has useful information (perhaps the user modified it?)
    this.pendingContext = null;
    return false;
  }

  @Nullable
  public PendingRunConfigurationContext getPendingContext() {
    return pendingContext;
  }

  private volatile ImmutableList<String> targetPatterns = ImmutableList.of();
  // null if the target is null or not a single Label
  @Nullable private volatile String targetKindString;
  // used to recognize previously created pending targets by their corresponding source element
  @Nullable private volatile String contextElementString;

  // for keeping imported configurations in sync with their source XML
  @Nullable private Boolean keepInSync = null;

  private BlazeCommandRunConfigurationHandlerProvider handlerProvider;
  private BlazeCommandRunConfigurationHandler handler;

  public BlazeCommandRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(project, factory, name);
    // start with whatever fallback is present
    handlerProvider =
        BlazeCommandRunConfigurationHandlerProvider.findHandlerProvider(TargetState.KNOWN, null);
    handler = handlerProvider.createHandler(this);
    try {
      handler.getState().readExternal(blazeElementState);
    } catch (InvalidDataException e) {
      logger.error(e);
    }
  }

  /** @return The configuration's {@link BlazeCommandRunConfigurationHandler}. */
  public BlazeCommandRunConfigurationHandler getHandler() {
    return handler;
  }

  /**
   * Gets the configuration's handler's {@link RunConfigurationState} if it is an instance of the
   * given class; otherwise returns null.
   */
  @Nullable
  public <T extends RunConfigurationState> T getHandlerStateIfType(Class<T> type) {
    RunConfigurationState handlerState = handler.getState();
    if (type.isInstance(handlerState)) {
      return type.cast(handlerState);
    } else {
      return null;
    }
  }

  @Override
  public void setKeepInSync(@Nullable Boolean keepInSync) {
    this.keepInSync = keepInSync;
  }

  @Override
  @Nullable
  public Boolean getKeepInSync() {
    return keepInSync;
  }

  @Nullable
  public String getContextElementString() {
    return contextElementString;
  }

  @Override
  public ImmutableList<TargetExpression> getTargets() {
    return parseTargets(targetPatterns);
  }

  /**
   * Returns the single target expression represented by this configuration, or null if there isn't
   * exactly one.
   */
  @Nullable
  public TargetExpression getSingleTarget() {
    ImmutableList<TargetExpression> targets = getTargets();
    return targets.size() == 1 ? targets.get(0) : null;
  }

  public void setTargetInfo(TargetInfo target) {
    String pattern = target.label.toString().trim();
    targetPatterns = pattern.isEmpty() ? ImmutableList.of() : ImmutableList.of(pattern);
    updateTargetKind(target.kindString);
  }

  public void setTargets(ImmutableList<TargetExpression> targets) {
    targetPatterns = targets.stream().map(TargetExpression::toString).collect(toImmutableList());
    updateTargetKindAsync(null);
  }

  /** Sets the target expression and asynchronously kicks off a target kind update. */
  public void setTarget(@Nullable TargetExpression target) {
    targetPatterns =
        target != null ? ImmutableList.of(target.toString().trim()) : ImmutableList.of();
    updateTargetKindAsync(null);
  }

  private void updateHandler() {
    BlazeCommandRunConfigurationHandlerProvider handlerProvider =
        BlazeCommandRunConfigurationHandlerProvider.findHandlerProvider(
            getTargetState(), getTargetKind());
    updateHandlerIfDifferentProvider(handlerProvider);
  }

  private TargetState getTargetState() {
    return targetPatterns.isEmpty() && pendingContext != null
        ? TargetState.PENDING
        : TargetState.KNOWN;
  }

  private void updateHandlerIfDifferentProvider(
      BlazeCommandRunConfigurationHandlerProvider newProvider) {
    if (handlerProvider == newProvider) {
      return;
    }
    try {
      handler.getState().writeExternal(blazeElementState);
    } catch (WriteExternalException e) {
      logger.error(e);
    }
    handlerProvider = newProvider;
    handler = newProvider.createHandler(this);
    try {
      handler.getState().readExternal(blazeElementState);
    } catch (InvalidDataException e) {
      logger.error(e);
    }
  }

  /** Returns an empty list if *any* of the patterns aren't valid target expressions. */
  private static ImmutableList<TargetExpression> parseTargets(List<String> strings) {
    ImmutableList.Builder<TargetExpression> list = ImmutableList.builder();
    for (String s : strings) {
      TargetExpression expr = parseTarget(s);
      if (expr == null) {
        return ImmutableList.of();
      }
      list.add(expr);
    }
    return list.build();
  }

  @Nullable
  private static TargetExpression parseTarget(@Nullable String targetPattern) {
    if (Strings.isNullOrEmpty(targetPattern)) {
      return null;
    }
    // try to canonicalize labels with implicit target names
    Label label = LabelUtils.createLabelFromString(/* blazePackage= */ null, targetPattern);
    return label != null ? label : TargetExpression.fromStringSafe(targetPattern);
  }

  /**
   * Returns the {@link Kind} of the single blaze target corresponding to the configuration's target
   * expression, if it's currently known. Returns null if the target expression points to multiple
   * blaze targets.
   */
  @Nullable
  public Kind getTargetKind() {
    return Kind.fromRuleName(targetKindString);
  }

  /**
   * Queries the kind of the current target pattern, possibly asynchronously, in the case where
   * there's only a single target.
   *
   * @param asyncCallback if the kind is updated asynchronously, this will be run after the kind is
   *     updated. If it's updated synchronously, this will not be run.
   */
  void updateTargetKindAsync(@Nullable Runnable asyncCallback) {
    ImmutableList<TargetExpression> targets = parseTargets(targetPatterns);
    if (targets.size() != 1 || !(targets.get(0) instanceof Label)) {
      // TODO(brendandouglas): any reason to support multiple targets here?
      updateTargetKind(null);
      return;
    }
    Label label = (Label) targets.get(0);
    ListenableFuture<TargetInfo> future = TargetFinder.findTargetInfoFuture(getProject(), label);
    if (future.isDone()) {
      updateTargetKindFromSingleTarget(FuturesUtil.getIgnoringErrors(future));
    } else {
      updateTargetKindFromSingleTarget(null);
      future.addListener(
          () -> {
            updateTargetKindFromSingleTarget(FuturesUtil.getIgnoringErrors(future));
            if (asyncCallback != null) {
              asyncCallback.run();
            }
          },
          MoreExecutors.directExecutor());
    }
  }

  private void updateTargetKindFromSingleTarget(@Nullable TargetInfo target) {
    updateTargetKind(target == null ? null : target.kindString);
  }

  private void updateTargetKind(@Nullable String kind) {
    targetKindString = kind;
    updateHandler();
  }

  /**
   * @return The {@link Kind} name, if the target is a known rule. Otherwise, "target pattern" if it
   *     is a general {@link TargetExpression}, "unknown rule" if it is a {@link Label} without a
   *     known rule, and "unknown target" if there is no target.
   */
  private String getTargetKindName() {
    Kind kind = getTargetKind();
    if (kind != null) {
      return kind.toString();
    }

    ImmutableList<TargetExpression> targets = parseTargets(targetPatterns);
    if (targets.size() > 1) {
      return "target patterns";
    }
    TargetExpression singleTarget = Iterables.getFirst(targets, null);
    if (singleTarget instanceof Label) {
      return "unknown rule";
    } else if (singleTarget != null) {
      return "target pattern";
    } else {
      return "unknown target";
    }
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    // Our handler check is not valid when we don't have BlazeProjectData.
    if (BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData() == null) {
      // With query sync we don't need a sync to run a configuration
      if (Blaze.getProjectType(getProject()) != ProjectType.QUERY_SYNC) {
        throw new RuntimeConfigurationError(
            "Configuration cannot be run until project has been synced.");
      }
    }
    boolean hasBlazeBeforeRunTask =
        RunManagerEx.getInstanceEx(getProject()).getBeforeRunTasks(this).stream()
            .anyMatch(
                task ->
                    task.getProviderId().equals(BlazeBeforeRunTaskProvider.ID) && task.isEnabled());
    if (!hasBlazeBeforeRunTask) {
      throw new RuntimeConfigurationError(
          String.format(
              "Invalid run configuration: the %s before run task is missing. Please re-run sync "
                  + "to add it back",
              Blaze.buildSystemName(getProject())));
    }
    handler.checkConfiguration();
    PendingRunConfigurationContext pendingContext = this.pendingContext;
    if (pendingContext != null && !pendingContext.isDone()) {
      return;
    }
    ImmutableList<String> targetPatterns = this.targetPatterns;
    if (targetPatterns.isEmpty()) {
      throw new RuntimeConfigurationError(
          String.format(
              "You must specify a %s target expression.", Blaze.buildSystemName(getProject())));
    }
    for (String pattern : targetPatterns) {
      if (Strings.isNullOrEmpty(pattern)) {
        throw new RuntimeConfigurationError(
            String.format(
                "You must specify a %s target expression.", Blaze.buildSystemName(getProject())));
      }
      if (!pattern.startsWith("//") && !pattern.startsWith("@")) {
        throw new RuntimeConfigurationError(
            "You must specify the full target expression, starting with // or @");
      }

      String error = TargetExpression.validate(pattern);
      if (error != null) {
        throw new RuntimeConfigurationError(error);
      }
    }
  }

  private static Element getBlazeSettingsCopy(Element element) {
    Element blazeSettings = element.getChild(BLAZE_SETTINGS_TAG);
    if (blazeSettings != null) {
      return blazeSettings.clone();
    }
    // migrate an old-style run configuration
    blazeSettings = element.clone();
    blazeSettings.setName(BLAZE_SETTINGS_TAG);
    for (String common : COMMON_SETTINGS) {
      blazeSettings.removeChildren(common);
      blazeSettings.removeAttribute(common);
    }
    return blazeSettings;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    element = getBlazeSettingsCopy(element);

    String keepInSyncString = element.getAttributeValue(KEEP_IN_SYNC_TAG);
    keepInSync = keepInSyncString != null ? Boolean.parseBoolean(keepInSyncString) : null;
    contextElementString = element.getAttributeValue(CONTEXT_ELEMENT_ATTR);

    ImmutableList.Builder<String> targets = ImmutableList.builder();
    List<Element> targetElements = element.getChildren(TARGET_TAG);
    for (Element targetElement : targetElements) {
      if (targetElement != null && !Strings.isNullOrEmpty(targetElement.getTextTrim())) {
        targets.add(targetElement.getTextTrim());
        // backwards-compatibility with prior per-target kind serialization
        String kind = targetElement.getAttributeValue(KIND_ATTR);
        if (kind != null) {
          targetKindString = kind;
        }
      }
    }
    targetPatterns = targets.build();
    String singleKind = element.getAttributeValue(KIND_ATTR);
    if (singleKind != null) {
      targetKindString = element.getAttributeValue(KIND_ATTR);
    }

    // Because BlazeProjectData is not available when configurations are loading,
    // we can't call setTarget and have it find the appropriate handler provider.
    // So instead, we use the stored provider ID.
    String providerId = element.getAttributeValue(HANDLER_ATTR);
    BlazeCommandRunConfigurationHandlerProvider handlerProvider =
        BlazeCommandRunConfigurationHandlerProvider.getHandlerProvider(providerId);
    if (handlerProvider != null) {
      updateHandlerIfDifferentProvider(handlerProvider);
    }

    blazeElementState = element;
    handler.getState().readExternal(blazeElementState);
  }

  @Override
  @SuppressWarnings("ThrowsUncheckedException")
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);

    blazeElementState.removeChildren(TARGET_TAG);
    for (String target : targetPatterns) {
      if (target.isEmpty()) {
        continue;
      }
      Element targetElement = new Element(TARGET_TAG);
      targetElement.setText(target);
      blazeElementState.addContent(targetElement);
    }
    if (targetKindString != null) {
      blazeElementState.setAttribute(KIND_ATTR, targetKindString);
    }
    if (keepInSync != null) {
      blazeElementState.setAttribute(KEEP_IN_SYNC_TAG, Boolean.toString(keepInSync));
    } else {
      blazeElementState.removeAttribute(KEEP_IN_SYNC_TAG);
    }
    blazeElementState.setAttribute(HANDLER_ATTR, handlerProvider.getId());
    if (contextElementString != null) {
      blazeElementState.setAttribute(CONTEXT_ELEMENT_ATTR, contextElementString);
    }

    handler.getState().writeExternal(blazeElementState);
    // copy our internal state to the provided Element
    element.addContent(blazeElementState.clone());
  }

  @Override
  public BlazeCommandRunConfiguration clone() {
    final BlazeCommandRunConfiguration configuration = (BlazeCommandRunConfiguration) super.clone();
    configuration.blazeElementState = blazeElementState.clone();
    configuration.targetPatterns = targetPatterns;
    configuration.targetKindString = targetKindString;
    configuration.contextElementString = contextElementString;
    configuration.pendingContext = pendingContext;
    configuration.keepInSync = keepInSync;
    configuration.handlerProvider = handlerProvider;
    configuration.handler = handlerProvider.createHandler(this);
    try {
      configuration.handler.getState().readExternal(configuration.blazeElementState);
    } catch (InvalidDataException e) {
      logger.error(e);
    }

    return configuration;
  }

  @Override
  @Nullable
  public RunProfileState getState(Executor executor, ExecutionEnvironment environment)
      throws ExecutionException {
    BlazeCommandRunConfigurationRunner runner = handler.createRunner(executor, environment);
    if (runner != null) {
      environment.putCopyableUserData(BlazeCommandRunConfigurationRunner.RUNNER_KEY, runner);
      return runner.getRunProfileState(executor, environment);
    }
    return null;
  }

  @Override
  @Nullable
  public String suggestedName() {
    return handler.suggestedName(this);
  }

  @Override
  public SettingsEditor<? extends BlazeCommandRunConfiguration> getConfigurationEditor() {
    return new BlazeCommandRunConfigurationSettingsEditor(this);
  }

  @Override
  public Module[] getModules() {
    return new Module[0];
  }

  static class BlazeCommandRunConfigurationSettingsEditor
      extends SettingsEditor<BlazeCommandRunConfiguration> {

    private BlazeCommandRunConfigurationHandlerProvider handlerProvider;
    private BlazeCommandRunConfigurationHandler handler;
    private RunConfigurationStateEditor handlerStateEditor;
    private JComponent handlerStateComponent;
    private Element elementState;

    private final Box editorWithoutSyncCheckBox;
    private final Box editor;
    private final JBCheckBox keepInSyncCheckBox;
    private final JBLabel targetExpressionLabel;
    private final ConsoleOutputFileSettingsUi<BlazeCommandRunConfiguration> outputFileUi;
    private final TargetExpressionListUi targetsUi;

    BlazeCommandRunConfigurationSettingsEditor(BlazeCommandRunConfiguration config) {
      Project project = config.getProject();
      elementState = config.blazeElementState.clone();
      targetsUi = new TargetExpressionListUi(project);
      targetExpressionLabel = new JBLabel(UIUtil.ComponentStyle.LARGE);
      keepInSyncCheckBox = new JBCheckBox("Keep in sync with source XML");
      outputFileUi = new ConsoleOutputFileSettingsUi<>();
      editorWithoutSyncCheckBox = UiUtil.createBox(targetExpressionLabel, targetsUi);
      editor =
          UiUtil.createBox(
              editorWithoutSyncCheckBox, outputFileUi.getComponent(), keepInSyncCheckBox);
      updateEditor(config);
      updateHandlerEditor(config);
      keepInSyncCheckBox.addItemListener(e -> updateEnabledStatus());
    }

    private void updateEditor(BlazeCommandRunConfiguration config) {
      targetExpressionLabel.setText(
          String.format(
              "Target expression (%s handled by %s):",
              config.getTargetKindName(), config.handler.getHandlerName()));
      keepInSyncCheckBox.setVisible(config.keepInSync != null);
      if (config.keepInSync != null) {
        keepInSyncCheckBox.setSelected(config.keepInSync);
      }
      updateEnabledStatus();
    }

    private void updateEnabledStatus() {
      setEnabled(!keepInSyncCheckBox.isVisible() || !keepInSyncCheckBox.isSelected());
    }

    private void setEnabled(boolean enabled) {
      if (handlerStateEditor != null) {
        handlerStateEditor.setComponentEnabled(enabled);
      }
      targetsUi.setEnabled(enabled);
      outputFileUi.setComponentEnabled(enabled);
    }

    private void updateHandlerEditor(BlazeCommandRunConfiguration config) {
      handlerProvider = config.handlerProvider;
      handler = handlerProvider.createHandler(config);
      try {
        handler.getState().readExternal(config.blazeElementState);
      } catch (InvalidDataException e) {
        logger.error(e);
      }
      handlerStateEditor = handler.getState().getEditor(config.getProject());

      if (handlerStateComponent != null) {
        editorWithoutSyncCheckBox.remove(handlerStateComponent);
      }
      handlerStateComponent = handlerStateEditor.createComponent();
      editorWithoutSyncCheckBox.add(handlerStateComponent);
    }

    @Override
    protected JComponent createEditor() {
      return editor;
    }

    @Override
    protected void resetEditorFrom(BlazeCommandRunConfiguration config) {
      elementState = config.blazeElementState.clone();
      updateEditor(config);
      if (config.handlerProvider != handlerProvider) {
        updateHandlerEditor(config);
      }
      targetsUi.setTargetExpressions(config.targetPatterns);
      outputFileUi.resetEditorFrom(config);
      handlerStateEditor.resetEditorFrom(config.handler.getState());
    }

    @Override
    protected void applyEditorTo(BlazeCommandRunConfiguration config) {
      outputFileUi.applyEditorTo(config);
      handlerStateEditor.applyEditorTo(handler.getState());
      try {
        handler.getState().writeExternal(elementState);
      } catch (WriteExternalException e) {
        logger.error(e);
      }
      config.keepInSync = keepInSyncCheckBox.isVisible() ? keepInSyncCheckBox.isSelected() : null;

      // now set the config's state, based on the editor's (possibly out of date) handler
      config.updateHandlerIfDifferentProvider(handlerProvider);
      config.blazeElementState = elementState.clone();
      try {
        config.handler.getState().readExternal(config.blazeElementState);
      } catch (InvalidDataException e) {
        logger.error(e);
      }

      // finally, update the handler
      config.targetPatterns = targetsUi.getTargetExpressions();
      config.updateTargetKindAsync(
          () -> ApplicationManager.getApplication().invokeLater(this::fireEditorStateChanged));
      updateEditor(config);
      if (config.handlerProvider != handlerProvider) {
        updateHandlerEditor(config);
        handlerStateEditor.resetEditorFrom(config.handler.getState());
      } else {
        handlerStateEditor.applyEditorTo(config.handler.getState());
      }
    }
  }
}
