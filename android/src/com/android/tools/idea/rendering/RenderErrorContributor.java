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
package com.android.tools.idea.rendering;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.CLASS_FLEXBOX_LAYOUT;
import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.FRAGMENT_CONTAINER_VIEW;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.VALUE_FILL_PARENT;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;
import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.ide.common.rendering.api.ILayoutLog.TAG_RESOURCES_PREFIX;
import static com.android.ide.common.rendering.api.ILayoutLog.TAG_RESOURCES_RESOLVE_THEME_ATTR;
import static com.android.tools.idea.rendering.RenderLogger.TAG_STILL_BUILDING;
import static com.android.tools.idea.res.IdeResourcesUtil.isViewPackageNeeded;
import static com.android.tools.lint.detector.api.Lint.editDistance;
import static com.android.tools.lint.detector.api.Lint.stripIdPrefix;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.ide.common.resources.ResourceResolver;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.psi.TagToClassMapper;
import com.android.tools.idea.rendering.errors.ComposeRenderErrorContributor;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.utils.HtmlBuilder;
import com.android.xml.AndroidManifest;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.MigrateToAndroidxUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class that finds {@link RenderErrorModel.Issue}s in a {@link RenderResult}.
 */
public class RenderErrorContributor {
  private static final String RENDER_SESSION_IMPL_FQCN = "com.android.layoutlib.bridge.impl.RenderSessionImpl";

  // These priorities can be used to promote certain issues to the top of the list
  protected static final int HIGH_PRIORITY = 100;
  @SuppressWarnings("unused") protected static final int MEDIUM_PRIORITY = 10;
  @SuppressWarnings("unused") protected static final int LOW_PRIORITY = 10;

  protected static final Logger LOG = Logger.getInstance(RenderErrorContributor.class);
  private static final String APP_COMPAT_REQUIRED_MSG = "You need to use a Theme.AppCompat";

  private final Set<RenderErrorModel.Issue> myIssues = new LinkedHashSet<>();
  private final HtmlLinkManager myLinkManager;
  private final HyperlinkListener myLinkHandler;
  @NotNull private final Module myModule;
  @NotNull protected final PsiFile mySourceFile;
  @NotNull private final RenderLogger myLogger;
  @Nullable private final RenderContext myRenderContext;
  private final boolean myHasRequestedCustomViews;
  private final DataContext myDataContext;
  private final EditorDesignSurface myDesignSurface;

  protected RenderErrorContributor(@Nullable EditorDesignSurface surface, @NotNull RenderResult result, @Nullable DataContext dataContext) {
    // To get rid of memory leak, get needed RenderResult attributes to avoid referencing RenderResult.
    myModule = result.getModule();
    mySourceFile = result.getSourceFile();
    myLogger = result.getLogger();
    myRenderContext = result.getRenderContext();
    myHasRequestedCustomViews = result.hasRequestedCustomViews();
    myDesignSurface = surface;
    myLinkManager = result.getLogger().getLinkManager();
    myLinkHandler = e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        JEditorPane pane = (JEditorPane)e.getSource();
        if (e instanceof HTMLFrameHyperlinkEvent) {
          HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
          HTMLDocument doc = (HTMLDocument)pane.getDocument();
          doc.processHTMLFrameHyperlinkEvent(evt);
          return;
        }

        performClick(e.getDescription());
      }
    };

    myDataContext = dataContext;
  }

  private static boolean isHiddenFrame(@NotNull StackTraceElement frame) {
    String className = frame.getClassName();
    return
      className.startsWith("sun.reflect.") ||
      className.equals("android.view.BridgeInflater") ||
      className.startsWith("com.android.tools.") ||
      className.startsWith("org.jetbrains.");
  }

  private static boolean isInterestingFrame(@NotNull StackTraceElement frame) {
    String className = frame.getClassName();
    return !(className.startsWith("android.")
             || className.startsWith("org.jetbrains.")
             || className.startsWith("com.android.")
             || className.startsWith("java.")
             || className.startsWith("javax.")
             || className.startsWith("sun."));
  }

  private static boolean isFramework(@NotNull StackTraceElement frame) {
    String className = frame.getClassName();
    return (className.startsWith("android.")
            || className.startsWith("java.")
            || className.startsWith("javax.")
            || className.startsWith("sun."));
  }

  private static boolean isVisible(@NotNull StackTraceElement frame) {
    String className = frame.getClassName();
    return !(isFramework(frame) || className.startsWith("sun."));
  }

  @NotNull
  private static Collection<String> getAllViews(@Nullable final Module module) {
    if (module == null) {
      return Collections.emptyList();
    }
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ReadAction.compute(() -> getAllViews(module));
    }

    if (DumbService.getInstance(module.getProject()).isDumb()) {
      // This method should not be called in dumb mode, but if it is, we can just return an empty list. This will disable the feature
      // where we return suggestions for correcting views.
      LOG.warn("getAllViews called in Dumb mode, no views will be returned");
      return Collections.emptyList();
    }

    return TagToClassMapper.getInstance(module).getClassMap(CLASS_VIEW).values().stream()
      .map(PsiClass::getQualifiedName)
      .collect(Collectors.toSet());
  }

  /**
   * Returns a new {@link RenderErrorModel.Issue.Builder} that will add the created issue to the issues list when
   * {@link RenderErrorModel.Issue.Builder#build()} is called.
   * The returned builder also is pre-configured with the default link handler.
   */
  protected RenderErrorModel.Issue.Builder addIssue() {
    return new RenderErrorModel.Issue.Builder() {
      @NotNull
      @Override
      public RenderErrorModel.Issue build() {
        RenderErrorModel.Issue built = super.build();
        myIssues.add(built);
        return built;
      }
    }.setLinkHandler(myLinkHandler);
  }

  private void reportMissingStyles(@NotNull RenderLogger logger) {
    if (logger.seenTagPrefix(TAG_STILL_BUILDING)) {
      addIssue()
        .setSummary("Project Still Building: May cause rendering errors until the build is done")
        .build();
    }
    else if (logger.seenTagPrefix(TAG_RESOURCES_RESOLVE_THEME_ATTR)) {
      addIssue()
        .setSummary("Missing styles")
        .setHtmlContent(new HtmlBuilder()
                          .addBold("Missing styles. Is the correct theme chosen for this layout?")
                          .newline()
                          .addIcon(HtmlBuilderHelper.getTipIconPath())
                          .add(
                            "Use the Theme combo box above the layout to choose a different layout, or fix the theme style references.")
        )
        .build();
    }
  }

  private void reportMissingSize(@NotNull HtmlBuilder builder,
                                 @NotNull RenderLogger logger,
                                 @NotNull String fill,
                                 @NotNull XmlTag tag,
                                 @NotNull String id,
                                 @NotNull String attribute) {
    Module module = logger.getModule();
    if (module == null) {
      return;
    }
    String wrapUrl = myLinkManager.createCommandLink(new SetAttributeFix(tag, attribute, ANDROID_URI, VALUE_WRAP_CONTENT));
    String fillUrl = myLinkManager.createCommandLink(new SetAttributeFix(tag, attribute, ANDROID_URI, fill));

    builder.add(String.format("%1$s does not set the required %2$s attribute: ", id, attribute))
      .newline()
      .addNbsps(4)
      .addLink("Set to wrap_content", wrapUrl)
      .add(", ")
      .addLink("Set to " + fill, fillUrl)
      .newline();
  }

  private void reportMissingSizeAttributes(@NotNull final RenderLogger logger,
                                           @NotNull RenderContext renderTaskContext,
                                           @Nullable XmlFile psiFile) {
    Module module = logger.getModule();
    if (module == null) {
      return;
    }
    if (logger.isMissingSize()) {
      HtmlBuilder builder = new HtmlBuilder();

      // Emit hyperlink about missing attributes; the action will operate on all of them
      builder.addBold("NOTE: One or more layouts are missing the layout_width or layout_height attributes. " +
                      "These are required in most layouts.").newline();
      final ResourceResolver resourceResolver = renderTaskContext.getConfiguration().getResourceResolver();
      if (psiFile == null) {
        LOG.error("PsiFile is missing in RenderTask used in RenderErrorPanel!");
        return;
      }

      // See whether we should offer match_parent instead of fill_parent
      AndroidModuleInfo moduleInfo = StudioAndroidModuleInfo.getInstance(module);
      final String fill = moduleInfo == null
                          || moduleInfo.getBuildSdkVersion() == null
                          || moduleInfo.getBuildSdkVersion().getApiLevel() >= 8
                          ? VALUE_MATCH_PARENT : VALUE_FILL_PARENT;

      ApplicationManager.getApplication()
                        .runReadAction(() -> AddMissingAttributesFix.findViewsMissingSizes(psiFile, resourceResolver).stream()
                                                                    .map(SmartPsiElementPointer::getElement)
                                                                    .filter(Objects::nonNull)
                                                                    .filter(XmlTag::isValid)
                                                                    .forEach(tag -> {
                                                                      boolean missingWidth =
                                                                        !AddMissingAttributesFix.definesWidth(tag, resourceResolver);
                                                                      boolean missingHeight =
                                                                        !AddMissingAttributesFix.definesHeight(tag, resourceResolver);
                                                                      assert missingWidth || missingHeight;

                                                                      String id = tag.getAttributeValue(ATTR_ID);
                                                                      if (id == null || id.isEmpty()) {
                                                                        id = '<' + tag.getName() + '>';
                                                                      }
                                                                      else {
                                                                        id = '"' + stripIdPrefix(id) + '"';
                                                                      }

                                                                      if (missingWidth) {
                                                                        reportMissingSize(builder, logger, fill, tag, id,
                                                                                          ATTR_LAYOUT_WIDTH);
                                                                      }
                                                                      if (missingHeight) {
                                                                        reportMissingSize(builder, logger, fill, tag, id,
                                                                                          ATTR_LAYOUT_HEIGHT);
                                                                      }
                                                                    }));

      builder.newline()
        .add("Or: ")
        .addLink("Automatically add all missing attributes",
                 myLinkManager.createCommandLink(new AddMissingAttributesFix(psiFile, resourceResolver))).newline()
        .newline().newline();

      addIssue()
        .setSeverity(HighlightSeverity.ERROR)
        .setSummary("One or more layouts are missing the layout_width or layout_height attributes")
        .setHtmlContent(builder)
        .build();
    }
  }

  private static void addHtmlForIssue164378(@NotNull Throwable throwable,
                                            Module module,
                                            HtmlLinkManager linkManager,
                                            HtmlBuilder builder,
                                            boolean addShowExceptionLink) {
    builder.add("Rendering failed with a known bug. ");
    if (module == null) {
      // Unlikely, but just in case.
      builder.add("Please rebuild the project and then clear the cache by clicking the refresh icon above the preview.").newline();
      return;
    }
    builder.addLink("Please try a ", "rebuild", ".", linkManager.createBuildProjectUrl());
    builder.newline().newline();
    if (!addShowExceptionLink) {
      return;
    }
    ShowExceptionFix showExceptionFix = new ShowExceptionFix(module.getProject(), throwable);
    builder.addLink("Show Exception", linkManager.createRunnableLink(showExceptionFix));
  }

  @VisibleForTesting
  public void performClick(@NotNull String url) {
    myLinkManager.handleUrl(url, myModule, mySourceFile, myDataContext, true, myDesignSurface);
  }

  private void reportRelevantCompilationErrors(@NotNull RenderLogger logger) {
    Module module = logger.getModule();
    if (module == null || module.isDisposed()) {
      return;
    }

    Project project = module.getProject();
    WolfTheProblemSolver wolfgang = WolfTheProblemSolver.getInstance(project);

    if (!wolfgang.hasProblemFilesBeneath(module)) {
      return;
    }


    HtmlBuilder builder = new HtmlBuilder();
    String summary = null;
    if (logger.seenTagPrefix(TAG_RESOURCES_PREFIX)) {
      // Do we have errors in the res/ files?
      // See if it looks like we have aapt problems
      boolean haveResourceErrors = wolfgang.hasProblemFilesBeneath(virtualFile -> virtualFile.getFileType() == XmlFileType.INSTANCE);
      if (haveResourceErrors) {
        summary = "Resource errors";
        builder.addBold("This project contains resource errors, so aapt did not succeed, " +
                        "which can cause rendering failures. Fix resource problems first.")
          .newline().newline();
      }
    }
    else if (myHasRequestedCustomViews) {
      boolean hasJavaErrors = wolfgang.hasProblemFilesBeneath(virtualFile -> virtualFile.getFileType() == JavaFileType.INSTANCE);
      if (hasJavaErrors) {
        summary = "Compilation errors";
        builder.addBold("This project contains Java compilation errors, " +
                        "which can cause rendering failures for custom views. " +
                        "Fix compilation problems first.")
          .newline().newline();
      }
    }

    if (summary == null) {
      return;
    }
    addIssue()
      .setSeverity(HighlightSeverity.ERROR)
      .setSummary(summary)
      .setHtmlContent(builder)
      .build();
  }

  private boolean reportSandboxError(@NotNull Throwable throwable, boolean newlineBefore, boolean newlineAfter) {
    if (!(throwable instanceof SecurityException)) {
      return false;
    }

    HtmlBuilder builder = new HtmlBuilder();
    if (newlineBefore) {
      builder.newline();
    }
    builder.addLink("Turn off custom view rendering sandbox", myLinkManager.createDisableSandboxUrl());

    String lastFailedPath = RenderSecurityManager.getLastFailedPath();
    if (lastFailedPath != null) {
      builder.newline().newline()
        .add("Diagnostic info for Studio bug report:").newline()
        .add("Failed path: ").add(lastFailedPath).newline();
      String tempDir = System.getProperty("java.io.tmpdir");
      builder.add("Normal temp dir: ").add(tempDir).newline();
      File normalized = new File(tempDir);
      builder.add("Normalized temp dir: ").add(normalized.getPath()).newline();
      try {
        builder.add("Canonical temp dir: ").add(normalized.getCanonicalPath()).newline();
      }
      catch (IOException e) {
        // ignore
      }
      builder.add("os.name: ").add(SystemInfo.OS_NAME).newline()
        .add("os.version: ").add(SystemInfo.OS_VERSION).newline()
        .add("java.runtime.version: ").add(SystemInfo.JAVA_RUNTIME_VERSION);
    }

    if (newlineAfter) {
      builder.newline().newline();
    }

    reportThrowable(builder, throwable, false);
    addRefreshAction(builder);

    addIssue()
      .setSeverity(HighlightSeverity.ERROR)
      .setSummary("Rendering sandbox error")
      .setHtmlContent(builder)
      .build();
    return true;
  }

  /**
   * Display the problem list encountered during a render.
   *
   * @return if the throwable was hidden.
   */
  private boolean reportThrowable(@NotNull HtmlBuilder builder,
                                  @NotNull final Throwable throwable,
                                  boolean hideIfIrrelevant) {
    StackTraceElement[] frames = throwable.getStackTrace();
    int end = -1;
    boolean haveInterestingFrame = false;
    for (int i = 0; i < frames.length; i++) {
      StackTraceElement frame = frames[i];
      if (isInterestingFrame(frame)) {
        haveInterestingFrame = true;
      }
      String className = frame.getClassName();
      if (className.equals(RENDER_SESSION_IMPL_FQCN)) {
        end = i;
        break;
      }
    }

    if (end == -1 || !haveInterestingFrame) {
      // Not a recognized stack trace range: just skip it
      if (hideIfIrrelevant) {
        return true;
      }
      else {
        // List just the top frames
        for (int i = 0; i < frames.length; i++) {
          StackTraceElement frame = frames[i];
          if (!isVisible(frame)) {
            end = i;
            if (end == 0) {
              // Find end instead
              for (int j = 0; j < frames.length; j++) {
                frame = frames[j];
                String className = frame.getClassName();
                if (className.equals(RENDER_SESSION_IMPL_FQCN)) {
                  end = j;
                  break;
                }
              }
            }
            break;
          }
        }
      }
    }

    builder.addHtml(StringUtil.replace(throwable.toString(), "\n", "<BR/>")).newline();

    boolean wasHidden = false;
    int indent = 2;
    File platformSource = null;
    boolean platformSourceExists = true;
    for (int i = 0; i < end; i++) {
      StackTraceElement frame = frames[i];
      if (isHiddenFrame(frame)) {
        wasHidden = true;
        continue;
      }

      String className = frame.getClassName();
      String methodName = frame.getMethodName();
      builder.addNbsps(indent);
      builder.add("at ").add(className).add(".").add(methodName);
      String fileName = frame.getFileName();
      if (fileName != null && !fileName.isEmpty()) {
        int lineNumber = frame.getLineNumber();
        String location = fileName + ':' + lineNumber;
        if (isInterestingFrame(frame)) {
          if (wasHidden) {
            builder.addNbsps(indent).add("    ...").newline();
            wasHidden = false;
          }
          String url = myLinkManager.createOpenStackUrl(className, methodName, fileName, lineNumber);
          builder.add("(").addLink(location, url).add(")");
        }
        else {
          // Try to link to local documentation
          String url = null;
          if (isFramework(frame) && platformSourceExists) { // try to link to documentation, if available
            if (platformSource == null) {
              IAndroidTarget target = myRenderContext != null ?
                                      myRenderContext.getConfiguration().getRealTarget() :
                                      null;
              platformSource = target != null ? AndroidSdks.getInstance().findPlatformSources(target) : null;
              platformSourceExists = platformSource != null;
            }

            if (platformSourceExists) {
              File classFile = new File(platformSource, frame.getClassName().replace('.', File.separatorChar) + DOT_JAVA);
              if (!classFile.exists()) {
                // Probably an innerclass like foo.bar.Outer.Inner; the above would look for foo/bar/Outer/Inner.java; try
                // again at foo/bar/
                File parentFile = classFile.getParentFile();
                classFile = new File(parentFile.getParentFile(), parentFile.getName() + DOT_JAVA);
                if (!classFile.exists()) {
                  classFile = null; // in theory we should keep trying this repeatedly for more deeply nested inner classes
                }
              }
              if (classFile != null) {
                url = HtmlLinkManager.createFilePositionUrl(classFile, lineNumber, 0);
              }
            }
          }
          if (url != null) {
            builder.add("(").addLink(location, url).add(")");
          }
          else {
            builder.add("(").add(location).add(")");
          }
        }
        builder.newline();
      }
    }

    builder.addLink("Copy stack to clipboard", myLinkManager.createRunnableLink(() -> {
      String text = Throwables.getStackTraceAsString(throwable);
      try {
        CopyPasteManager.getInstance().setContents(new StringSelection(text));
        HtmlLinkManager.showNotification("Stack trace copied to clipboard");
      }
      catch (Exception ignore) {
      }
    }));
    return false;
  }

  private void addRefreshAction(@NotNull HtmlBuilder builder) {
    builder.newlineIfNecessary()
      .newline()
      .addIcon(HtmlBuilderHelper.getTipIconPath())
      .addLink("Tip: Try to ", "refresh", " the layout.",
               myLinkManager.createRefreshRenderUrl()).newline();
  }

  /**
   * Tries to report an "RTL not enabled" error and returns whether this was successful.
   */
  private boolean reportRtlNotEnabled(@NotNull RenderLogger logger) {
    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> {
      Project project = logger.getProject();
      if (project == null || project.isDisposed()) {
        return false;
      }

      Module module = logger.getModule();
      if (module == null) {
        return false;
      }

      AndroidFacet facet = AndroidFacet.getInstance(module);
      Manifest manifest = facet != null ? Manifest.getMainManifest(facet) : null;
      Application application = manifest != null ? manifest.getApplication() : null;
      if (application == null) {
        return false;
      }

      final XmlTag applicationTag = application.getXmlTag();
      if (applicationTag == null) {
        return false;
      }

      HtmlBuilder builder = new HtmlBuilder();
      builder.add("(")
        .addLink("Add android:supportsRtl=\"true\" to the manifest", logger.getLinkManager().createRunnableLink(() -> {
          new SetAttributeFix(applicationTag, AndroidManifest.ATTRIBUTE_SUPPORTS_RTL, ANDROID_URI, VALUE_TRUE).executeCommand();

          if (myDesignSurface != null) {
            myDesignSurface.forceUserRequestedRefresh();
          }
        })).add(")");

      addIssue()
        .setSeverity(HighlightSeverity.ERROR)
        .setSummary("RTL support requires android:supportsRtl=\"true\" in the manifest")
        .setHtmlContent(builder)
        .build();
      return true;
    });
  }

  /**
   * Tries to report a resources format error and returns whether this was successful.
   */
  private boolean reportTagResourceFormat(@NotNull RenderProblem message) {
    Object clientData = message.getClientData();
    if (!(clientData instanceof String[])) {
      return false;
    }
    String[] strings = (String[])clientData;
    if (strings.length != 2) {
      return false;
    }

    RenderContext renderContext = myRenderContext;
    if (renderContext == null) {
      return false;
    }
    IAndroidTarget target = renderContext.getConfiguration().getRealTarget();
    if (target == null) {
      return false;
    }
    AndroidPlatform platform = renderContext.getModule().getAndroidPlatform();
    if (platform == null) {
      return false;
    }
    AndroidTargetData targetData = AndroidTargetData.get(platform.getSdkData(), target);
    AttributeDefinitions definitionLookup = targetData.getPublicAttrDefs(mySourceFile.getProject());
    String attributeName = strings[0];
    String currentValue = strings[1];
    AttributeDefinition definition = definitionLookup.getAttrDefByName(attributeName);
    if (definition == null) {
      return false;
    }
    Set<AttributeFormat> formats = definition.getFormats();
    if (formats.contains(AttributeFormat.FLAGS) || formats.contains(AttributeFormat.ENUM)) {
      String[] values = definition.getValues();
      if (values.length > 0) {
        HtmlBuilder builder = new HtmlBuilder();
        builder.add("Change ").add(currentValue).add(" to: ");
        boolean first = true;
        for (String value : values) {
          if (first) {
            first = false;
          }
          else {
            builder.add(", ");
          }
          builder.addLink(value, myLinkManager.createReplaceAttributeValueUrl(attributeName, currentValue, value));
        }

        addRefreshAction(builder);
        addIssue()
          //TODO: Review
          .setSummary("Incorrect resource value format")
          .setHtmlContent(builder)
          .build();
        return true;
      }
    }
    return false;
  }

  private void reportOtherProblems(@NotNull RenderLogger logger) {
    List<RenderProblem> messages = logger.getMessages();

    if (messages.isEmpty()) {
      return;
    }

    Set<String> seenTags = Sets.newHashSet();
    for (RenderProblem message : messages) {
      String tag = message.getTag();
      if (tag != null && seenTags.contains(tag)) {
        continue;
      }
      seenTags.add(tag);

      if (tag != null) {
        switch (tag) {
          case ILayoutLog.TAG_RESOURCES_FORMAT -> {
            if (reportTagResourceFormat(message)) {
              continue;
            }
          }
          case ILayoutLog.TAG_RTL_NOT_ENABLED -> {
            if (reportRtlNotEnabled(logger)) {
              continue;
            }
          }
          case ILayoutLog.TAG_RTL_NOT_SUPPORTED -> {
            addIssue()
              .setSeverity(HighlightSeverity.ERROR)
              .setSummary("RTL support requires API level >= 17")
              .setHtmlContent(new HtmlBuilder().addHtml(message.getHtml()))
              .build();
            continue;
          }
          case ILayoutLog.TAG_THREAD_CREATION -> {
            Throwable throwable = message.getThrowable();
            HtmlBuilder builder = new HtmlBuilder();
            reportThrowable(builder, throwable, false);
            addIssue()
              .setSeverity(HighlightSeverity.WARNING)
              .setSummary(message.getHtml())
              .setHtmlContent(builder)
              .build();
            continue;
          }
        }
      }

      HtmlBuilder builder = new HtmlBuilder();

      String html = message.getHtml();
      Throwable throwable = message.getThrowable();

      String summary = "Render problem";
      if (throwable != null) {
        if (!reportSandboxError(throwable, false, true)) {
          if (ComposeRenderErrorContributor.isHandledByComposeContributor(throwable)) continue; // This is handled as a warning above.
          if (reportThrowable(builder, throwable, !html.isEmpty() || !message.isDefaultHtml())) {
            // The error was hidden.
            if (!html.isEmpty()) {
              builder.getStringBuilder().append(html);
              builder.newlineIfNecessary();
            }

            summary = throwable.getLocalizedMessage() != null ?
                      throwable.getLocalizedMessage() :
                      summary;
          }
        }
        else {
          // This was processed as a Sandbox error
          continue;
        }
      }
      else {
        if (html.contains("has been edited more recently")) {
          summary = "Build out-of-date";
        }

        builder.getStringBuilder().append(html);
        builder.newlineIfNecessary();
      }

      addRefreshAction(builder);
      addIssue()
        .setSeverity(ProblemSeverities.toHighlightSeverity(message.getSeverity()))
        .setSummary(summary)
        .setHtmlContent(builder)
        .build();
    }
  }

  private boolean addTypoSuggestions(@NotNull HtmlBuilder builder,
                                     @NotNull String actual,
                                     @Nullable Collection<String> views,
                                     boolean compareWithPackage) {
    if (views == null || views.isEmpty()) {
      return false;
    }

    // Look for typos and try to match with custom views and android views
    String actualBase = actual.substring(actual.lastIndexOf('.') + 1);
    String match = compareWithPackage ? actual : actualBase;
    int maxDistance = actualBase.length() >= 4 ? 2 : 1;

    for (String suggested : views) {
      String suggestedBase = suggested.substring(suggested.lastIndexOf('.') + 1);
      String matchWith = compareWithPackage ? suggested : suggestedBase;
      if (Math.abs(actualBase.length() - suggestedBase.length()) > maxDistance) {
        // The string lengths differ more than the allowed edit distance;
        // no point in even attempting to compute the edit distance (requires
        // O(n*m) storage and O(n*m) speed, where n and m are the string lengths)
        continue;
      }

      boolean sameBase = actualBase.equals(suggestedBase);
      if (!compareWithPackage && sameBase) {
        // This view is an exact match for one of the known views.
        // That probably means it's a valid class, but the project needs to be built.
        continue;
      }

      if (compareWithPackage) {
        if (!sameBase) {
          // If they differ in the base name, handled by separate call with !compareWithPackage
          continue;
        }
        else if (actualBase.equals(actual) && !actualBase.equals(suggested) && isViewPackageNeeded(suggested, -1)) {
          // Custom view needs to be specified with a fully qualified path
          builder.addLink(String.format("Change to %1$s", suggested),
                          myLinkManager.createReplaceTagsUrl(actual, suggested));
          builder.add(", ");
          continue;
        }
      }

      if (compareWithPackage && Math.abs(match.length() - matchWith.length()) > maxDistance) {
        continue;
      }

      if (match.equals(matchWith)) {
        // Exact match: Likely that we're looking for a valid package, but project has
        // not yet been built
        return true;
      }

      if (editDistance(match, matchWith, maxDistance + 1) <= maxDistance) {
        // Suggest this class as a typo for the given class
        String labelClass = (suggestedBase.equals(actual) || actual.indexOf('.') != -1) ? suggested : suggestedBase;
        builder.addLink(String.format("Change to %1$s", labelClass),
                        myLinkManager.createReplaceTagsUrl(actual,
                                                           // Only show full package name if class name
                                                           // is the same
                                                           (isViewPackageNeeded(suggested, -1) ? suggested : suggestedBase)));
        builder.add(", ");
      }
    }

    return false;
  }

  private void reportRenderingFidelityProblems(@NotNull RenderLogger logger) {
    List<RenderProblem> fidelityWarnings = logger.getFidelityWarnings();
    if (fidelityWarnings.isEmpty()) {
      return;
    }

    HtmlBuilder builder = new HtmlBuilder();
    builder.add("The graphics preview in the layout editor may not be accurate:").newline();
    builder.beginList();
    int count = 0;
    for (final RenderProblem warning : fidelityWarnings) {
      builder.listItem();
      warning.appendHtml(builder.getStringBuilder());
      final Object clientData = warning.getClientData();
      if (clientData != null) {
        builder.addLink(" (Ignore for this session)", myLinkManager.createRunnableLink(() -> {
          RenderLogger.ignoreFidelityWarning(clientData);
          if (myDesignSurface != null) {
            myDesignSurface.forceUserRequestedRefresh();
          }
        }));
      }
      builder.newline();
      count++;
      // Only display the first 3 render fidelity issues
      if (count == 3) {
        @SuppressWarnings("ConstantConditions")
        int remaining = fidelityWarnings.size() - count;
        if (remaining > 0) {
          builder.add("(").addHtml(Integer.toString(remaining)).add(" additional render fidelity issues hidden)");
          break;
        }
      }
    }
    builder.endList();
    builder.addLink("Ignore all fidelity warnings for this session", myLinkManager.createRunnableLink(() -> {
      RenderLogger.ignoreAllFidelityWarnings();
      if (myDesignSurface != null) {
        myDesignSurface.forceUserRequestedRefresh();
      }
    }));
    builder.newline();

    addIssue()
      .setSeverity(HighlightSeverity.WEAK_WARNING)
      .setSummary("Layout fidelity warning")
      .setHtmlContent(builder)
      .build();
  }

  private void reportMissingClasses(@NotNull RenderLogger logger) {
    Set<String> missingClasses = logger.getMissingClasses();
    if (missingClasses.isEmpty()) {
      return;
    }

    HtmlBuilder builder = new HtmlBuilder();
    if (missingClasses.contains("CalendarView")) {
      builder.add("The ").addBold("CalendarView").add(" widget does not work correctly with this render target. " +
                                                      "As a workaround, try using the API 15 (Android 4.0.3) render target library by selecting it from the " +
                                                      "toolbar menu above.");
      if (missingClasses.size() == 1) {
        addIssue()
          .setSeverity(HighlightSeverity.WARNING)
          .setSummary("CalendarView does not work correctly with this render target")
          .setHtmlContent(builder)
          .build();
        return;
      }
    }

    boolean missingResourceClass = logger.isMissingResourceClass() && logger.getResourceClass() != null && logger.hasLoadedClasses();

    builder.add("The following classes could not be found:");
    builder.beginList();

    Collection<String> customViews = null;
    Collection<String> androidViewClassNames = null;
    Module module = logger.getModule();
    if (module != null) {
      Ref<Collection<String>> viewsRef = new Ref<>(Collections.emptyList());
      // We yield to write actions here because UI responsiveness takes priority over typo suggestions.
      ProgressIndicatorUtils.runWithWriteActionPriority(() -> viewsRef.set(getAllViews(module)), new EmptyProgressIndicator());
      Collection<String> views = viewsRef.get();
      if (!views.isEmpty()) {
        customViews = Lists.newArrayListWithExpectedSize(Math.max(10, views.size() - 80)); // most will be framework views
        androidViewClassNames = Lists.newArrayListWithExpectedSize(views.size());
        for (String fqcn : views) {
          if (fqcn.startsWith("android.") && !isViewPackageNeeded(fqcn, -1)) {
            androidViewClassNames.add(fqcn);
          }
          else {
            customViews.add(fqcn);
          }
        }
      }
    }

    if (missingResourceClass) {
      builder.listItem();
      builder.add(logger.getResourceClass());
    }

    boolean foundCustomView = false;
    for (String className : missingClasses) {
      builder.listItem();
      builder.add(className);
      builder.add(" (");

      foundCustomView |= addTypoSuggestions(builder, className, customViews, false);
      addTypoSuggestions(builder, className, customViews, true);
      addTypoSuggestions(builder, className, androidViewClassNames, false);

      if (myLinkManager == null) {
        return;
      }

      if (CLASS_CONSTRAINT_LAYOUT.isEquals(className)) {
        builder.newline().addNbsps(3);
        Project project = logger.getProject();
        boolean useAndroidX = project == null || MigrateToAndroidxUtil.isAndroidx(project);
        GoogleMavenArtifactId artifact = useAndroidX ?
                                         GoogleMavenArtifactId.ANDROIDX_CONSTRAINT_LAYOUT :
                                         GoogleMavenArtifactId.CONSTRAINT_LAYOUT;
        builder.addLink("Add constraint-layout library dependency to the project",
                        myLinkManager.createAddDependencyUrl(artifact));
        builder.add(", ");
      }
      if (CLASS_FLEXBOX_LAYOUT.equals(className)) {
        builder.newline().addNbsps(3);
        builder.addLink("Add flexbox layout library dependency to the project",
                        myLinkManager.createAddDependencyUrl(GoogleMavenArtifactId.FLEXBOX_LAYOUT));
        builder.add(", ");
      }

      builder.addLink("Fix Build Path", myLinkManager.createEditClassPathUrl());

      //DesignSurface surface = renderTask.getDesignSurface();
      //if (surface != null && surface.getType() == LAYOUT_EDITOR) {
      builder.add(", ");
      builder.addLink("Edit XML", myLinkManager.createShowTagUrl(className));
      //}

      // Offer to create the class, but only if it looks like a custom view
      // TODO: Check to see if it looks like it's the name of a custom view and the
      // user didn't realize a FQN is required here
      if (className.indexOf('.') != -1) {
        builder.add(", ");
        builder.addLink("Create Class", myLinkManager.createNewClassUrl(className));
      }
      builder.add(")");
    }
    builder.endList();

    builder
      .addIcon(HtmlBuilderHelper.getTipIconPath())
      .addLink("Tip: Try to ", "build", " the project.",
                    myLinkManager.createBuildProjectUrl())
      .newline()
      .addIcon(HtmlBuilderHelper.getTipIconPath())
      .addLink("Tip: Try to ", "refresh", " the layout.",
               myLinkManager.createRefreshRenderUrl())
      .newline();
    if (foundCustomView) {
      builder.newline()
        .add("One or more missing custom views were found in the project, but does not appear to have been compiled yet.");
    }

    addIssue()
      .setSeverity(HighlightSeverity.ERROR)
      .setSummary("Missing classes")
      .setHtmlContent(builder)
      .build();
  }

  private void reportBrokenClasses(@NotNull RenderLogger logger) {
    Map<String, Throwable> brokenClasses = logger.getBrokenClasses();
    if (brokenClasses.isEmpty()) {
      return;
    }

    HtmlBuilder builder = new HtmlBuilder();
    final Module module = logger.getModule();

    for (Throwable throwable : brokenClasses.values()) {
      if (RenderLogger.isIssue164378(throwable)) {
        addHtmlForIssue164378(throwable, module, myLinkManager, builder, false);
        break;
      }
    }

    builder.add("The following classes could not be instantiated:");

    boolean listContainsElements = false;
    Throwable firstThrowable = null;
    builder.beginList();
    for (Map.Entry<String, Throwable> entry : brokenClasses.entrySet()) {
      String className = entry.getKey();
      Throwable throwable = entry.getValue();

      if (throwable != null && throwable.getMessage() != null && throwable.getMessage().startsWith(APP_COMPAT_REQUIRED_MSG)) {
        // This is already handled by #reportAppCompatRequired
        continue;
      }

      listContainsElements = true;
      builder.listItem()
        .add(className)
        .add(" (")
        .addLink("Open Class", myLinkManager.createOpenClassUrl(className));
      if (throwable != null && module != null) {
        builder.add(", ");
        ShowExceptionFix detailsFix = new ShowExceptionFix(module.getProject(), throwable);
        builder.addLink("Show Exception", myLinkManager.createRunnableLink(detailsFix));
      }
      builder.add(", ")
        .addLink("Clear Cache", myLinkManager.createClearCacheUrl())
        .add(")");

      if (firstThrowable == null && throwable != null) {
        firstThrowable = throwable;
      }
    }

    if (!listContainsElements) {
      return;
    }

    builder.endList()
      .addIcon(HtmlBuilderHelper.getTipIconPath())
      .addLink("Tip: Use ", "View.isInEditMode()", " in your custom views to skip code or show sample data when shown in the IDE.",
               "http://developer.android.com/reference/android/view/View.html#isInEditMode()")
      .newline().newline()
      .add("If this is an unexpected error you can also try to ")
      .addLink("", "build the project", ", then ", myLinkManager.createBuildProjectUrl())
      .addLink("manually ", "refresh the layout", ".", myLinkManager.createRefreshRenderUrl());

    if (firstThrowable != null) {
      builder.newline().newline()
        .addHeading("Exception Details", HtmlBuilderHelper.getHeaderFontColor()).newline();
      reportThrowable(builder, firstThrowable, false);
      reportSandboxError(firstThrowable, true, false);
    }
    builder.newline().newline();

    addIssue()
      .setSeverity(HighlightSeverity.ERROR, HIGH_PRIORITY)
      .setSummary("Failed to instantiate one or more classes")
      .setHtmlContent(builder)
      .build();
  }

  private void reportUnknownFragments(@NotNull final RenderLogger logger) {
    List<String> fragmentNames = logger.getMissingFragments();
    if (fragmentNames == null || fragmentNames.isEmpty()) {
      return;
    }

    final String fragmentTagName;
    if (MigrateToAndroidxUtil.isAndroidx(logger.getProject())) {
      fragmentTagName = FRAGMENT_CONTAINER_VIEW;
    }
    else {
      fragmentTagName = VIEW_FRAGMENT;
    }
    final String fragmentTagDisplayName = "<" + fragmentTagName + ">";

    HtmlBuilder builder = new HtmlBuilder();
    builder.add("A ").addHtml("<code>").add(fragmentTagDisplayName).addHtml("</code>").add(" tag allows a layout file to dynamically include " +
                                                                                 "different layouts at runtime. ")
      .add("At layout editing time the specific layout to be used is not known. You can choose which layout you would " +
           "like previewed while editing the layout.");
    builder.beginList();

    // TODO: Add link to not warn any more for this session

    for (final String className : fragmentNames) {
      builder.listItem();
      boolean isIdentified = className != null && !className.isEmpty();
      boolean isActivityKnown = isIdentified && !className.startsWith(PREFIX_RESOURCE_REF);
      if (isIdentified) {
        builder.add("<").add(fragmentTagName).add(" ").addBold(className).add(" ...>");
      }
      else {
        builder.add(fragmentTagDisplayName);
      }
      builder.add(" (");

      if (isActivityKnown) {
        final Module module = logger.getModule();
        ApplicationManager.getApplication().runReadAction(() -> {
          // TODO: Look up layout references in the given layout, if possible
          // Find activity class
          // Look for R references in the layout
          assert module != null;
          Project project = module.getProject();
          GlobalSearchScope scope = GlobalSearchScope.allScope(project);
          PsiClass clz = DumbService.getInstance(project).isDumb() ?
                         null :
                         JavaPsiFacade.getInstance(project).findClass(className, scope);
          String layoutName = mySourceFile.getName();
          boolean separate = false;
          if (clz != null) {
            // TODO: Should instead find all R.layout elements
            // HACK AHEAD!
            String matchText = clz.getText();
            final Pattern LAYOUT_FIELD_PATTERN = Pattern.compile("R\\.layout\\.([a-z0-9_]+)");
            Matcher matcher = LAYOUT_FIELD_PATTERN.matcher(matchText);
            Set<String> layouts = Sets.newTreeSet();
            int index = 0;
            while (true) {
              if (matcher.find(index)) {
                layouts.add(matcher.group(1));
                index = matcher.end();
              }
              else {
                break;
              }
            }
            for (String layout : layouts) {
              if (layout.equals(layoutName)) { // Don't include self
                continue;
              }
              if (separate) {
                builder.add(", ");
              }
              builder.addLink("Use @layout/" + layout, myLinkManager.createAssignLayoutUrl(className, layout));
              separate = true;
            }
          }

          if (separate) {
            builder.add(", ");
          }
          builder.addLink("Pick Layout...", myLinkManager.createPickLayoutUrl(className));
        });
      }
      else {
        builder.addLink("Choose Fragment Class...", myLinkManager.createAssignFragmentUrl(className));
      }
      builder.add(")");
    }
    builder.endList()
      .newline()
      // TODO: URLs
      .addLink("Do not warn about " + fragmentTagDisplayName + " tags in this session", myLinkManager.createIgnoreFragmentsUrl())
      .newline();

    addIssue()
      .setSeverity(HighlightSeverity.ERROR)
      .setSummary("Unknown fragments")
      .setHtmlContent(builder)
      .build();
  }

  /**
   * Support lib classes will fail to instantiate if the preview is not using the right theme.
   */
  private void reportAppCompatRequired(@NotNull RenderLogger logger) {
    Map<String, Throwable> brokenClasses = logger.getBrokenClasses();

    if (brokenClasses.isEmpty()) {
      return;
    }

    brokenClasses.values().stream()
      .filter(Objects::nonNull)
      .filter(t -> t.getMessage() != null && t.getMessage().startsWith(APP_COMPAT_REQUIRED_MSG))
      .findAny()
      .ifPresent(t -> addIssue()
        .setSeverity(HighlightSeverity.ERROR, HIGH_PRIORITY + 1) // Reported above broken classes
        .setSummary("Using the design library requires using Theme.AppCompat or a descendant")
        .setHtmlContent(new HtmlBuilder()
                          .add("Select ").addItalic("Theme.AppCompat").add(" or a descendant in the theme selector."))
        .build());
  }

  public Collection<RenderErrorModel.Issue> reportIssues() {
    RenderLogger logger = myLogger;
    RenderContext renderContext = myRenderContext;

    reportMissingStyles(logger);
    reportAppCompatRequired(logger);
    if (renderContext != null) {
      reportRelevantCompilationErrors(logger);
      reportMissingSizeAttributes(logger,
                                  renderContext,
                                  (mySourceFile instanceof XmlFile) ? (XmlFile)mySourceFile : null);
      reportMissingClasses(logger);
    }
    reportBrokenClasses(logger);
    reportOtherProblems(logger);
    reportUnknownFragments(logger);
    reportRenderingFidelityProblems(logger);
    myIssues.addAll(ComposeRenderErrorContributor.reportComposeErrors(logger, myLinkManager, myLinkHandler));

    return getIssues();
  }

  protected HtmlLinkManager getLinkManager() {
    return myLinkManager;
  }

  protected Collection<RenderErrorModel.Issue> getIssues() {
    return Collections.unmodifiableCollection(myIssues);
  }

  public static class Provider {
    public static final ExtensionPointName<Provider> EP_NAME =
      new ExtensionPointName<>("com.android.rendering.renderErrorContributor");

    public boolean isApplicable(Project project) {
      return true;
    }

    public RenderErrorContributor getContributor(@Nullable EditorDesignSurface surface, @NotNull RenderResult result, @Nullable DataContext dataContext) {
      return new RenderErrorContributor(surface, result, dataContext);
    }
  }
}
