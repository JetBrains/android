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
package com.android.tools.idea.lint.common;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.DOT_KTS;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FN_PROJECT_PROGUARD_FILE;
import static com.android.SdkConstants.OLD_PROGUARD_FILE;
import static com.android.tools.lint.detector.api.TextFormat.HTML;
import static com.android.tools.lint.detector.api.TextFormat.RAW;

import com.android.tools.lint.checks.DeprecationDetector;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.checks.WrongIdDetector;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.google.common.collect.Sets;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.ex.CustomEditInspectionToolsSettingsAction;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlStringUtil;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.plugins.groovy.GroovyFileType;

public class LintExternalAnnotator extends ExternalAnnotator<LintEditorResult, LintEditorResult> {
  static {
    LintClient.setClientName(LintClient.CLIENT_STUDIO);
  }

  static final String LINK_PREFIX = "#lint/"; // Should match the codeInsight.linkHandler prefix specified in lint-plugin.xml.
  static final boolean INCLUDE_IDEA_SUPPRESS_ACTIONS = false;

  @Nullable
  @Override
  public LintEditorResult collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    return collectInformation(file);
  }

  @Override
  public LintEditorResult collectInformation(@NotNull PsiFile file) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) {
      return null;
    }

    return collectInformation(file, module);
  }

  protected boolean isRelevant(@NotNull PsiFile file, @NotNull Module module) {
    return LintIdeSupport.get().canAnnotate(file, module);
  }

  @Nullable
  protected LintEditorResult collectInformation(@NotNull PsiFile file, @NotNull Module module) {
    final VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) {
      return null;
    }

    if (!isRelevant(file, module)) {
      return null;
    }

    final Set<Issue> issues = getIssuesFromInspections(file.getProject(), file);
    return new LintEditorResult(module, vFile, file.getText(), issues);
  }

  @Override
  public LintEditorResult doAnnotate(final LintEditorResult lintResult) {
    long startTime = System.currentTimeMillis();

    final LintIdeClient client = LintIdeSupport.get().createEditorClient(lintResult);
    try {
      EnumSet<Scope> scope;
      VirtualFile mainFile = lintResult.getMainFile();
      final FileType fileType = mainFile.getFileType();
      String name = mainFile.getName();
      if (fileType == XmlFileType.INSTANCE) {
        if (name.equals(ANDROID_MANIFEST_XML)) {
          scope = Scope.MANIFEST_SCOPE;
        }
        else if (name.endsWith(DOT_XML)) {
          scope = Scope.RESOURCE_FILE_SCOPE;
        }
        else {
          // Something else, like svg
          return lintResult;
        }
      }
      else if (fileType == JavaFileType.INSTANCE || fileType == KotlinFileType.INSTANCE) {
        scope = Scope.JAVA_FILE_SCOPE;
        if (name.endsWith(DOT_KTS)) {
          scope = EnumSet.of(Scope.GRADLE_FILE, Scope.JAVA_FILE);
        }
      }
      else if (name.equals(OLD_PROGUARD_FILE) || name.equals(FN_PROJECT_PROGUARD_FILE)) {
        scope = EnumSet.of(Scope.PROGUARD_FILE);
      }
      else if (fileType == GroovyFileType.GROOVY_FILE_TYPE) {
        scope = Scope.GRADLE_SCOPE;
      }
      else if (fileType == PropertiesFileType.INSTANCE) {
        scope = Scope.PROPERTY_SCOPE;
      }
      else {
        // #collectionInformation above should have prevented this
        assert false : fileType;
        return lintResult;
      }

      Project project = lintResult.getModule().getProject();
      if (project.isDisposed()) {
        return lintResult;
      }
      if (DumbService.isDumb(project)) {
        return lintResult; // Lint cannot run in dumb mode.
      }

      List<VirtualFile> files = Collections.singletonList(mainFile);
      LintRequest request = new LintIdeRequest(client, project, files,
                                               Collections.singletonList(lintResult.getModule()), true /* incremental */);
      request.setScope(scope);

      LintDriver lint = client.createDriver(request);
      lint.analyze();

      lint.setAnalysisStartTime(startTime);
      LintIdeSupport.get().logSession(lint, lintResult);
    }
    finally {
      Disposer.dispose(client);
    }
    return lintResult;
  }

  @NotNull
  static Set<Issue> getIssuesFromInspections(@NotNull Project project, @Nullable PsiElement context) {
    final IssueRegistry fullRegistry = LintIdeIssueRegistry.get();

    final List<Issue> issueList = fullRegistry.getIssues();
    final Set<Issue> result = Sets.newHashSetWithExpectedSize(issueList.size() + 10);
    for (Issue issue : issueList) {
      final String inspectionShortName = AndroidLintInspectionBase.getInspectionShortNameByIssue(project, issue);
      if (inspectionShortName == null) {
        continue;
      }
      final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionShortName);
      if (key == null) {
        continue;
      }

      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
      final boolean enabled = context != null ? profile.isToolEnabled(key, context) : profile.isToolEnabled(key);

      if (!enabled) {
        continue;
      }
      else if (!issue.isEnabledByDefault()) {
        // If an issue is marked as not enabled by default, lint won't run it, even if it's in the set
        // of issues provided by an issue registry. Since in the IDE we're enforcing the enabled-state via
        // inspection profiles, mark the issue as enabled to allow users to turn on a lint check directly
        // via the inspections UI.
        issue.setEnabledByDefault(true);
      }
      result.add(issue);
    }
    return result;
  }

  @Override
  public void apply(@NotNull PsiFile file, LintEditorResult lintResult, @NotNull AnnotationHolder holder) {
    if (lintResult.isDirty()) {
      return;
    }
    final Project project = file.getProject();
    if (DumbService.isDumb(project)) return;
    LintIdeQuickFixProvider[] fixProviders = LintIdeQuickFixProvider.EP_NAME.getExtensions();
    LintIdeSupport ideSupport = LintIdeSupport.get();

    for (LintProblemData problemData : lintResult.getProblems()) {
      final Issue issue = problemData.getIssue();
      final String message = problemData.getMessage();
      final TextRange range = problemData.getTextRange();
      final LintFix quickfixData = problemData.getQuickfixData();

      if (range.getStartOffset() == range.getEndOffset()) {
        continue;
      }

      final Pair<AndroidLintInspectionBase, HighlightDisplayLevel> pair = getHighlightLevelAndInspection(project, issue, file);
      if (pair == null) {
        continue;
      }
      final AndroidLintInspectionBase inspection = pair.getFirst();
      HighlightDisplayLevel displayLevel = pair.getSecond();

      if (inspection != null) {
        final HighlightDisplayKey key = HighlightDisplayKey.find(inspection.getShortName());

        if (key != null) {
          final PsiElement startElement = file.findElementAt(range.getStartOffset());
          final PsiElement endElement = file.findElementAt(range.getEndOffset() - 1);

          if (startElement != null && endElement != null && !inspection.isSuppressedFor(startElement)) {
            if (problemData.getConfiguredSeverity() != null) {
              HighlightDisplayLevel configuredLevel =
                AndroidLintInspectionBase.toHighlightDisplayLevel(problemData.getConfiguredSeverity());
              if (configuredLevel != null) {
                displayLevel = configuredLevel;
              }
            }

            HighlightSeverity severity = displayLevel.getSeverity();
            ProblemHighlightType type;
            if (issue == DeprecationDetector.ISSUE ||
                issue == GradleDetector.DEPRECATED ||
                issue == GradleDetector.DEPRECATED_CONFIGURATION) {
              type = ProblemHighlightType.LIKE_DEPRECATED;
            } else if (issue == WrongIdDetector.UNKNOWN_ID || issue == WrongIdDetector.UNKNOWN_ID_LAYOUT) {
              type = ProblemHighlightType.ERROR; // like unknown symbol
            } else if (severity == HighlightSeverity.ERROR) {
              // In recent versions of IntelliJ, HighlightInfo.convertSeverityToProblemHighlight
              // maps HighlightSeverity.ERROR to ProblemHighlightType.ERROR which is now documented
              // to be like ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, which gives the wrong
              // impression for most errors; see https://issuetracker.google.com/159532832
              type = ProblemHighlightType.GENERIC_ERROR;
            } else {
              type = HighlightInfo.convertSeverityToProblemHighlight(severity);
            }

            // This description link is not displayed. It is parsed by IDEA to
            // populate the "Show Inspection Description" action.
            String descriptionLink = "<a href=\"" + LINK_PREFIX + issue.getId() + "\"></a>";
            String tooltip = XmlStringUtil.wrapInHtml(descriptionLink + RAW.convertTo(message, HTML));

            AnnotationBuilder builder = holder
              .newAnnotation(severity, message)
              .highlightType(type)
              .range(range)
              .tooltip(tooltip);

            LintIdeQuickFix[] fixes = inspection.getAllFixes(startElement, endElement, message, quickfixData, fixProviders, issue);
            for (LintIdeQuickFix fix : fixes) {
              if (fix.isApplicable(startElement, endElement, AndroidQuickfixContexts.EditorContext.TYPE)) {
                builder = builder.withFix(new MyFixingIntention(fix, startElement, endElement));
              }
            }

            for (IntentionAction intention : inspection.getIntentions(startElement, endElement)) {
              builder = builder.withFix(intention);
            }

            if (ideSupport.canRequestFeedback()) {
              builder = builder.withFix(ideSupport.requestFeedbackIntentionAction(issue));
            }

            String id = key.getID();
            builder = builder.withFix(new SuppressLintIntentionAction(id, startElement));
            if (INCLUDE_IDEA_SUPPRESS_ACTIONS) {
              builder = builder.withFix(new MyDisableInspectionFix(key));
              builder = builder.withFix(new MyEditInspectionToolsSettingsAction(key, inspection));
            }

            if (INCLUDE_IDEA_SUPPRESS_ACTIONS) {
              final SuppressQuickFix[] suppressActions = inspection.getBatchSuppressActions(startElement);
              for (SuppressQuickFix action : suppressActions) {
                if (action.isAvailable(project, startElement)) {
                  ProblemDescriptor descriptor = InspectionManager.getInstance(project).createProblemDescriptor(
                    startElement, endElement, message, type, true, LocalQuickFix.EMPTY_ARRAY);
                  builder = builder.newLocalQuickFix(action, descriptor).key(key).registerFix();
                }
              }
            }

            builder.create();
          }
        }
      }
    }
  }

  @Nullable
  public static Pair<AndroidLintInspectionBase, HighlightDisplayLevel> getHighlightLevelAndInspection(@NotNull Project project,
                                                                                                      @NotNull Issue issue,
                                                                                                      @NotNull PsiElement context) {
    final String inspectionShortName = AndroidLintInspectionBase.getInspectionShortNameByIssue(project, issue);
    if (inspectionShortName == null) {
      return null;
    }

    final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionShortName);
    if (key == null) {
      return null;
    }

    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(context.getProject()).getCurrentProfile();
    if (!profile.isToolEnabled(key, context)) {
      if (!issue.isEnabledByDefault()) {
        // Lint will skip issues (and not report them) for issues that have been disabled,
        // except for those issues that are explicitly enabled via Gradle. Therefore, if
        // we get this far, lint has found this issue to be explicitly enabled, so we let
        // that setting override a local enabled/disabled state in the IDE profile.
      }
      else {
        return null;
      }
    }

    final AndroidLintInspectionBase inspection = (AndroidLintInspectionBase)profile.getUnwrappedTool(inspectionShortName, context);
    if (inspection == null) return null;
    final HighlightDisplayLevel errorLevel = profile.getErrorLevel(key, context);
    return Pair.create(inspection, errorLevel);
  }

  private static class MyDisableInspectionFix implements IntentionAction, Iconable {
    private final DisableInspectionToolAction myDisableInspectionToolAction;

    private MyDisableInspectionFix(@NotNull HighlightDisplayKey key) {
      myDisableInspectionToolAction = new DisableInspectionToolAction(key);
    }

    @NotNull
    @Override
    public String getText() {
      return "Disable inspection";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      myDisableInspectionToolAction.invoke(project, editor, file);
    }

    @Override
    public boolean startInWriteAction() {
      return myDisableInspectionToolAction.startInWriteAction();
    }

    @Nullable
    @Override
    public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
      return myDisableInspectionToolAction.getElementToMakeWritable(file);
    }

    @Override
    public Icon getIcon(@IconFlags int flags) {
      return myDisableInspectionToolAction.getIcon(flags);
    }
  }

  public static class MyFixingIntention implements IntentionAction, HighPriorityAction {
    private final LintIdeQuickFix myQuickFix;
    private final PsiElement myStartElement;
    private final PsiElement myEndElement;

    public MyFixingIntention(@NotNull LintIdeQuickFix quickFix, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      myQuickFix = quickFix;
      myStartElement = startElement;
      myEndElement = endElement;
    }

    @NotNull
    @Override
    public String getText() {
      return myQuickFix.getName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return LintBundle.message("android.lint.quickfixes.family");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      myQuickFix.apply(myStartElement, myEndElement, AndroidQuickfixContexts.EditorContext.getInstance(editor));
    }

    @Override
    public boolean startInWriteAction() {
      return myQuickFix.startInWriteAction();
    }

    @Override
    public String toString() {
      return getText();
    }
  }

  private static class MyEditInspectionToolsSettingsAction extends CustomEditInspectionToolsSettingsAction {
    private MyEditInspectionToolsSettingsAction(@NotNull HighlightDisplayKey key, @NotNull final AndroidLintInspectionBase inspection) {
      super(key, () -> "Edit '" + inspection.getDisplayName() + "' inspection settings");
    }
  }
}
