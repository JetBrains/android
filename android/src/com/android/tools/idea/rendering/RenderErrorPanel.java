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

package com.android.tools.idea.rendering;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidProject;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.Density;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.service.notification.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.utils.HtmlBuilder;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.Document;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;
import static com.android.ide.common.rendering.api.LayoutLog.TAG_RESOURCES_PREFIX;
import static com.android.ide.common.rendering.api.LayoutLog.TAG_RESOURCES_RESOLVE_THEME_ATTR;
import static com.android.tools.idea.gradle.util.GradleUtil.hasLayoutRenderingIssue;
import static com.android.tools.idea.rendering.HtmlLinkManager.URL_ACTION_CLOSE;
import static com.android.tools.idea.rendering.RenderLogger.TAG_STILL_BUILDING;
import static com.android.tools.idea.res.ResourceHelper.isViewPackageNeeded;
import static com.android.tools.lint.detector.api.LintUtils.editDistance;
import static com.android.tools.lint.detector.api.LintUtils.stripIdPrefix;
import static com.intellij.openapi.util.SystemInfo.JAVA_VERSION;
import static org.jetbrains.android.sdk.AndroidSdkUtils.getAndroidSdkAdditionalData;
import static org.jetbrains.android.sdk.AndroidSdkUtils.isAndroidSdk;

/**
 * Panel which can show render errors, along with embedded hyperlinks to perform actions such as
 * showing relevant source errors, or adding missing attributes, etc.
 * <p>
 * Partially based on {@link com.intellij.codeInspection.ui.Browser}, the inspections result HTML pane, modified
 * to show render errors instead
 */
public class RenderErrorPanel extends JPanel {
  public static final boolean SIZE_ERROR_PANEL_DYNAMICALLY = true;
  private static final int ERROR_PANEL_OPACITY = UIUtil.isUnderDarcula() ? 224 : 208; // out of 255
  /** Class of the render session implementation class; for render errors, we cut off stack dumps at this frame */
  private static final String RENDER_SESSION_IMPL_FQCN = "com.android.layoutlib.bridge.impl.RenderSessionImpl";
  private static final Logger LOG = Logger.getInstance(RenderErrorPanel.class);

  private JEditorPane myHTMLViewer;
  private final HyperlinkListener myHyperLinkListener;
  private RenderResult myResult;
  private HighlightSeverity mySeverity; // severity of messages shown, currently just warning or error
  private HtmlLinkManager myLinkManager;
  private final JScrollPane myScrollPane;
  /**
   * By default, if ONLY fidelity warnings are found, they are showed collapsed in one warning line. If true, the
   * fidelity warnings will be showed expanded as any other errors.
   */
  private boolean myExpandFidelityWarnings;

  public RenderErrorPanel() {
    super(new BorderLayout());
    setOpaque(false);
    myHTMLViewer = new JEditorPane(UIUtil.HTML_MIME, "<HTML><BODY>Render Problems</BODY></HTML>");
    myHTMLViewer.setEditable(false);
    myHyperLinkListener = new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          JEditorPane pane = (JEditorPane)e.getSource();
          if (e instanceof HTMLFrameHyperlinkEvent) {
            HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
            HTMLDocument doc = (HTMLDocument)pane.getDocument();
            doc.processHTMLFrameHyperlinkEvent(evt);
            return;
          }

          String url = e.getDescription();
          if (url.equals(URL_ACTION_CLOSE)) {
            close();
            return;
          }
          performClick(url);
        }
      }
    };
    myHTMLViewer.addHyperlinkListener(myHyperLinkListener);
    myHTMLViewer.setMargin(JBUI.insets(3, 3, 3, 3));

    myScrollPane = ScrollPaneFactory.createScrollPane(myHTMLViewer);
    setupStyle();

    add(myScrollPane, BorderLayout.CENTER);
  }

  public void dispose(){
    removeAll();
    if (myHTMLViewer != null) {
      myHTMLViewer.removeHyperlinkListener(myHyperLinkListener);
      myHTMLViewer = null;
    }
  }

  @Nullable
  public String showErrors(@NotNull final RenderResult result) {
    RenderLogger logger = result.getLogger();
    if (!logger.hasProblems()) {
      showErrors(null, null, null);
      return null;
    }

    try {
      String html = generateHtml(result, result.getLogger().getLinkManager());
      showErrors(html, result, logger.getLinkManager());
      return html;
    }
    catch (Exception e) {
      showEmpty();
      return null;
    }
  }

  public void showErrors(@Nullable String html, @Nullable RenderResult result, @Nullable HtmlLinkManager linkManager) {
    HighlightSeverity maxSeverity = result != null && result.getLogger().hasErrors() ? HighlightSeverity.ERROR : HighlightSeverity.WARNING;
    showErrors(maxSeverity, html, result, linkManager);
  }

  public void showWarning(@Nullable String html) {
    showErrors(HighlightSeverity.WARNING, html, null, null);
  }

  private void showErrors(@NonNull HighlightSeverity severity,
                         @Nullable String html,
                         @Nullable RenderResult result,
                         @Nullable HtmlLinkManager linkManager) {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().invokeLater(() -> showErrors(severity, html, result, linkManager));
      return;
    }

    mySeverity = severity;
    if (html == null) {
      myResult = null;
      showEmpty();
      return;
    }
    try {
      myHTMLViewer.read(new StringReader(html), null);
      setupStyle();
      myHTMLViewer.setCaretPosition(0);
      myResult = result;
      myLinkManager = linkManager;
    }
    catch (Exception e) {
      showEmpty();
    }
  }

  @VisibleForTesting
  public void performClick(@NotNull String url) {
    Module module = myResult.getModule();
    PsiFile file = myResult.getFile();
    DataContext dataContext = DataManager.getInstance().getDataContext(this);
    assert dataContext != null;

    myLinkManager.handleUrl(url, module, file, dataContext, myResult);
  }

  private void close() {
    this.setVisible(false);
  }

  private void setupStyle() {
    // Make the scrollPane transparent
    JViewport viewPort = myScrollPane.getViewport();
    viewPort.setOpaque(false);
    viewPort.setBackground(null);
    myScrollPane.setOpaque(false);
    myScrollPane.setBackground(null);

    Document document = myHTMLViewer.getDocument();
    if (!(document instanceof StyledDocument)) {
      return;
    }

    StyledDocument styledDocument = (StyledDocument)document;

    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = colorsManager.getGlobalScheme();

    Style style = styledDocument.addStyle("active", null);
    StyleConstants.setFontFamily(style, scheme.getEditorFontName());
    StyleConstants.setFontSize(style, scheme.getEditorFontSize());
    styledDocument.setCharacterAttributes(0, document.getLength(), style, false);

    // Make background semitransparent
    Color background = myHTMLViewer.getBackground();
    if (background != null) {
      //noinspection UseJBColor
      background = new Color(background.getRed(), background.getGreen(), background.getBlue(), ERROR_PANEL_OPACITY);
      myHTMLViewer.setBackground(background);
    }
  }

  @VisibleForTesting
  public JEditorPane getEditorPane() {
    return myHTMLViewer;
  }

  public int getPreferredHeight(@SuppressWarnings("UnusedParameters") int width) {
    return myHTMLViewer.getPreferredSize().height;
  }

  @Nullable
  public HighlightSeverity getSeverity() {
    return mySeverity;
  }

  public String generateHtml(@NotNull RenderResult result, @NotNull HtmlLinkManager linkManager) {
    myResult = result;
    myLinkManager = linkManager;

    RenderLogger logger = result.getLogger();
    RenderTask renderTask = result.getRenderTask();
    assert logger.hasProblems();

    HtmlBuilder builder = new HtmlBuilder(new StringBuilder(300));
    builder.openHtmlBody();

    if (logger.hasErrors() || myExpandFidelityWarnings) {
      // Construct close button. Sadly <img align="right"> doesn't work in JEditorPanes; would
      // have looked a lot nicer with the image flushed to the right!
      builder.addHtml("<A HREF=\"");
      builder.addHtml(URL_ACTION_CLOSE);
      builder.addHtml("\">");
      builder.addIcon(HtmlBuilderHelper.getCloseIconPath());
      builder.addHtml("</A>");
      builder.addNbsp();
      builder.addHeading("Rendering Problems", HtmlBuilderHelper.getHeaderFontColor()).newline();

      reportMissingStyles(logger, builder);
      if (renderTask != null) {
        reportOldNinePathRenderLib(logger, builder, renderTask);
        reportRelevantCompilationErrors(logger, builder, renderTask);
        reportMissingSizeAttributes(logger, builder, renderTask);
        reportMissingClasses(logger, builder, renderTask);
      }
      reportBrokenClasses(logger, builder);
      reportInstantiationProblems(logger, builder);
      reportOtherProblems(logger, builder);
      reportUnknownFragments(logger, builder);

      if (renderTask != null) {
        reportRenderingFidelityProblems(logger, builder, renderTask);
      }
    }
    else {
      // We only have fidelity warnings so display a small warning that allows the user to fully expand them
      builder
        .addIcon(HtmlBuilderHelper.getWarningIconPath())
        .addNbsp()
        .add("Fidelity warnings")
        .addLink(" (show) ", myLinkManager.createRunnableLink(() -> {
          myExpandFidelityWarnings = true;
          if (myResult != null) {
            RenderTask task = myResult.getRenderTask();
            DesignSurface surface = task != null ? task.getDesignSurface() : null;
            if (surface != null) {
              // Just request a repaint (no full model invalidation) since the model and the errors will still be valid
              surface.requestRender(false);
            }
          }
        }));
    }

    builder.closeHtmlBody();

    return builder.getHtml();
  }

  private void reportMissingClasses(@NotNull RenderLogger logger, @NotNull HtmlBuilder builder, @NotNull RenderTask renderTask) {
    Set<String> missingClasses = logger.getMissingClasses();
    if (missingClasses != null && !missingClasses.isEmpty()) {
      if (missingClasses.contains("CalendarView")) {
        builder.add("The ").addBold("CalendarView").add(" widget does not work correctly with this render target. " +
            "As a workaround, try using the API 15 (Android 4.0.3) render target library by selecting it from the " +
            "toolbar menu above.");
        if (missingClasses.size() == 1) {
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
        Collection<String> views = getAllViews(module);
        if (!views.isEmpty()) {
          customViews = Lists.newArrayListWithExpectedSize(Math.max(10, views.size() - 80)); // most will be framework views
          androidViewClassNames = Lists.newArrayListWithExpectedSize(views.size());
          for (String fqcn : views) {
            if (fqcn.startsWith("android.") && !isViewPackageNeeded(fqcn, -1)) {
              androidViewClassNames.add(fqcn);
            } else {
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

        if (SdkConstants.CLASS_CONSTRAINT_LAYOUT.equals(className)) {
          builder.newline().addNbsps(3);
          builder.addLink("Add constraint-layout library dependency to the project", myLinkManager.createInstallArtifactUrl(SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT));
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

      builder.addIcon(HtmlBuilderHelper.getTipIconPath());
      builder.addLink("Tip: Try to ", "build", " the project.",
                      myLinkManager.createCompileModuleUrl());
      if (foundCustomView) {
        builder.newline();
        builder.add("One or more missing custom views were found in the project, but does not appear to have been compiled yet.");
      }
      builder.newline().newline();
    }
  }

  private boolean addTypoSuggestions(@NotNull HtmlBuilder builder,
                                  String actual,
                                  @Nullable Collection<String> views,
                                  boolean compareWithPackage) {
    if (views == null || views.isEmpty()) {
      return false;
    }

    // Look for typos and try to match with custom views and android views
    String actualBase = actual.substring(actual.lastIndexOf('.') + 1);
    String match = compareWithPackage ? actual : actualBase;
    int maxDistance = actualBase.length() >= 4 ? 2 : 1;

    if (views.size() > 0) {
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
          } else if (actualBase.equals(actual) && !actualBase.equals(suggested) && isViewPackageNeeded(suggested, -1)) {
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

        if (editDistance(match, matchWith) <= maxDistance) {
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
    }

    return false;
  }

  private void reportUnknownFragments(@NotNull final RenderLogger logger, @NotNull final HtmlBuilder builder) {
    List<String> fragmentNames = logger.getMissingFragments();
    if (fragmentNames != null && !fragmentNames.isEmpty()) {

      builder.add("A ").addHtml("<code>").add("<fragment>").addHtml("</code>").add(" tag allows a layout file to dynamically include " +
                                                                                   "different layouts at runtime. ");
      builder.add("At layout editing time the specific layout to be used is not known. You can choose which layout you would " +
                  "like previewed while editing the layout.");
      builder.beginList();

      // TODO: Add link to not warn any more for this session

      for (final String className : fragmentNames) {
        builder.listItem();
        boolean isIdentified = className != null && !className.isEmpty();
        boolean isActivityKnown = isIdentified && !className.startsWith(PREFIX_RESOURCE_REF);
        if (isIdentified) {
          builder.add("<fragment ");
          builder.addBold(className);
          builder.add(" ...>");
        } else {
          builder.add("<fragment>");
        }
        builder.add(" (");

        if (isActivityKnown) {
          final Module module = logger.getModule();
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              // TODO: Look up layout references in the given layout, if possible
              // Find activity class
              // Look for R references in the layout
              assert module != null;
              Project project = module.getProject();
              GlobalSearchScope scope = GlobalSearchScope.allScope(project);
              PsiClass clz = JavaPsiFacade.getInstance(project).findClass(className, scope);
              String layoutName = myResult.getFile().getName();
              boolean separate = false;
              if (clz != null) {
                // TODO: Should instead find all R.layout elements
                // HACK AHEAD!
                String matchText = clz.getText();
                final Pattern LAYOUT_FIELD_PATTERN = Pattern.compile("R\\.layout\\.([a-z0-9_]+)"); //$NON-NLS-1$
                Matcher matcher = LAYOUT_FIELD_PATTERN.matcher(matchText);
                Set<String> layouts = Sets.newTreeSet();
                int index = 0;
                while (true) {
                  if (matcher.find(index)) {
                    layouts.add(matcher.group(1));
                    index = matcher.end();
                  } else {
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
            }
          });
        } else {
          builder.addLink("Choose Fragment Class...", myLinkManager.createAssignFragmentUrl(className));
        }
        builder.add(")");
      }
      builder.endList();
      builder.newline();
// TODO: URLs
      builder.addLink("Do not warn about <fragment> tags in this session", myLinkManager.createIgnoreFragmentsUrl());
      builder.newline();
    }
  }

  @NotNull
  private static Collection<String> getAllViews(@Nullable final Module module) {
    if (module == null) {
      return Collections.emptyList();
    }
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Collection<String>>() {
        @Override
        public Collection<String> compute() {
          return getAllViews(module);
        }
      });
    }

    Set<String> names = new java.util.HashSet<String>();
    for (PsiClass psiClass : findInheritors(module, CLASS_VIEW)) {
      String name = psiClass.getQualifiedName();
      if (name != null ) {
        names.add(name);
      }
    }

    return names;
  }

  @NotNull
  private static Collection<PsiClass> findInheritors(@NotNull final Module module, @NotNull final String name) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiClass>>() {
        @Override
        public Collection<PsiClass> compute() {
          return findInheritors(module, name);
        }
      });
    }

    Project project = module.getProject();
    try {
      PsiClass base = JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project));
      if (base != null) {
        GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
        return ClassInheritorsSearch.search(base, scope, true).findAll();
      }
    }
    catch (IndexNotReadyException ignored) {
    }
    return Collections.emptyList();
  }

  private void reportBrokenClasses(@NotNull RenderLogger logger, @NotNull HtmlBuilder builder) {
    Map<String,Throwable> brokenClasses = logger.getBrokenClasses();
    if (brokenClasses != null && !brokenClasses.isEmpty()) {
      final Module module = logger.getModule();
      if (module != null) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null && facet.requiresAndroidModel() && facet.getAndroidModel() != null) {
          // TODO: b/23032391
          AndroidProject androidProject = AndroidGradleModel.get(facet).getAndroidProject();
          String modelVersion = androidProject.getModelVersion();
          if (hasLayoutRenderingIssue(androidProject)) {
            builder.addBold("Using an obsolete version of the Gradle plugin (" + modelVersion +
                            "); this can lead to layouts not rendering correctly.").newline();
            builder.addIcon(HtmlBuilderHelper.getTipIconPath());

            Runnable runnable = new Runnable() {
              @Override
              public void run() {
                FixAndroidGradlePluginVersionHyperlink
                  quickFix = new FixAndroidGradlePluginVersionHyperlink(GRADLE_PLUGIN_RECOMMENDED_VERSION,
                                                                        null, false);
                quickFix.executeIfClicked(module.getProject(),
                                          new HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, null, quickFix.getUrl()));
              }
            };
            builder.add("Tip: Either ")
              .addLink("update the Gradle plugin build version to 1.2.3", myLinkManager.createRunnableLink(runnable))
              .add(" or later, or downgrade to version 1.1.3, or as a workaround, ");
            builder.beginList();
            builder.listItem().addLink("", "Build the project", ", then", myLinkManager.createCompileModuleUrl());
            builder.listItem().addLink("", "Gradle Sync the project", ", then", myLinkManager.createSyncProjectUrl());
            builder.listItem().addLink("Manually ", "refresh the layout", " (or restart the IDE)", myLinkManager.createRefreshRenderUrl());
            builder.endList();
            builder.newline();
          }
        }
      }

      for (Throwable throwable : brokenClasses.values()) {
        if (RenderLogger.isIssue164378(throwable)) {
          RenderLogger.addHtmlForIssue164378(throwable, module, myLinkManager, builder, false);
          break;
        }
      }
      builder.add("The following classes could not be instantiated:");

      Throwable firstThrowable = null;
      builder.beginList();
      for (Map.Entry<String,Throwable> entry : brokenClasses.entrySet()) {
        String className = entry.getKey();
        Throwable throwable = entry.getValue();

        builder.listItem();
        builder.add(className);
        builder.add(" (");
        builder.addLink("Open Class", myLinkManager.createOpenClassUrl(className));
        if (throwable != null && module != null) {
          builder.add(", ");
          ShowExceptionFix detailsFix = new ShowExceptionFix(module.getProject(), throwable);
          builder.addLink("Show Exception", myLinkManager.createRunnableLink(detailsFix));
        }
        builder.add(", ");
        builder.addLink("Clear Cache", myLinkManager.createRefreshRenderUrl());
        builder.add(")");

        if (firstThrowable == null && throwable != null) {
          firstThrowable = throwable;
        }
      }
      builder.endList();

      builder.addIcon(HtmlBuilderHelper.getTipIconPath());
      builder.addLink("Tip: Use ", "View.isInEditMode()", " in your custom views to skip code or show sample data when shown in the IDE",
                      "http://developer.android.com/reference/android/view/View.html#isInEditMode()");

      if (firstThrowable != null) {
        builder.newline().newline();
        builder.addHeading("Exception Details", HtmlBuilderHelper.getHeaderFontColor()).newline();
        reportThrowable(builder, firstThrowable, false);
        reportSandboxError(builder, firstThrowable, true, false);
      }
      builder.newline().newline();
    }
  }

  private void reportSandboxError(@NotNull HtmlBuilder builder, Throwable throwable, boolean newlineBefore, boolean newlineAfter) {
    if (throwable instanceof SecurityException) {
      if (newlineBefore) {
        builder.newline();
      }
      builder.addLink("Turn off custom view rendering sandbox", myLinkManager.createDisableSandboxUrl());

      String lastFailedPath = RenderSecurityManager.getLastFailedPath();
      if (lastFailedPath != null) {
        builder.newline().newline();
        builder.add("Diagnostic info for Studio bug report:").newline();
        builder.add("Failed path: ").add(lastFailedPath).newline();
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
        builder.add("os.name: ").add(SystemInfo.OS_NAME).newline();
        builder.add("os.version: ").add(SystemInfo.OS_VERSION).newline();
        builder.add("java.runtime.version: ").add(SystemInfo.JAVA_RUNTIME_VERSION);
      }

      if (throwable.getMessage().equals("Unable to create temporary file")) {
          if (JAVA_VERSION.startsWith("1.7.0_")) {
            int version = Integer.parseInt(JAVA_VERSION.substring(JAVA_VERSION.indexOf('_') + 1));
            if (version > 0 && version < 45) {
              builder.newline();
              builder.addIcon(HtmlBuilderHelper.getTipIconPath());
              builder.add("Tip: This may be caused by using an older version of JDK 1.7.0; try using at least 1.7.0_45 " +
                          "(you are using " + JAVA_VERSION + ")");
            }
          }
      }

      if (newlineAfter) {
        builder.newline().newline();
      }
    }
  }

  private void reportRenderingFidelityProblems(@NotNull RenderLogger logger, @NotNull HtmlBuilder builder,
                                               @NotNull final RenderTask renderTask) {
    List<RenderProblem> fidelityWarnings = logger.getFidelityWarnings();
    if (fidelityWarnings != null && !fidelityWarnings.isEmpty()) {
      builder.add("The graphics preview in the layout editor may not be accurate:").newline();
      builder.beginList();
      int count = 0;
      for (final RenderProblem warning : fidelityWarnings) {
        builder.listItem();
        warning.appendHtml(builder.getStringBuilder());
        final Object clientData = warning.getClientData();
        if (clientData != null) {
          builder.addLink(" (Ignore for this session)", myLinkManager.createRunnableLink(new Runnable() {
            @Override
            public void run() {
              RenderLogger.ignoreFidelityWarning(clientData);
              DesignSurface surface = renderTask.getDesignSurface();
              if (surface != null) {
                surface.requestRender();
              }
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
      builder.addLink("Ignore all fidelity warnings for this session", myLinkManager.createRunnableLink(new Runnable() {
        @Override
        public void run() {
          RenderLogger.ignoreAllFidelityWarnings();
          DesignSurface surface = renderTask.getDesignSurface();
          if (surface != null) {
            surface.requestRender();
          }
        }
      }));
      builder.newline();
    }
  }

  private static void reportMissingStyles(RenderLogger logger, HtmlBuilder builder) {
    if (logger.seenTagPrefix(TAG_STILL_BUILDING)) {
      builder.addBold("Project Still Building: May cause rendering errors until the build is done.").newline();
      builder.newline().newline();
    } else if (logger.seenTagPrefix(TAG_RESOURCES_RESOLVE_THEME_ATTR)) {
      builder.addBold("Missing styles. Is the correct theme chosen for this layout?").newline();
      builder.addIcon(HtmlBuilderHelper.getTipIconPath());
      builder.add("Use the Theme combo box above the layout to choose a different layout, or fix the theme style references.");
      builder.newline().newline();
    }
  }

  private static void reportOldNinePathRenderLib(RenderLogger logger, HtmlBuilder builder, @NotNull RenderTask renderTask) {
    for (Throwable trace : logger.getTraces()) {
      if (trace.toString().contains("java.lang.IndexOutOfBoundsException: Index: 2, Size: 2") //$NON-NLS-1$
          && renderTask.getConfiguration().getDensity() == Density.TV) {
        builder.addBold("It looks like you are using a render target where the layout library does not support the tvdpi density.");
        builder.newline().newline();
        builder.add("Please try either updating to the latest available version (using the SDK manager), or if no updated " +
                    "version is available for this specific version of Android, try using a more recent render target version.");
        builder.newline().newline();
        break;
      }
    }
  }

  private static void reportRelevantCompilationErrors(RenderLogger logger, HtmlBuilder builder, RenderTask renderTask) {
    Module module = logger.getModule();
    if (module == null) {
      return;
    }
    Project project = module.getProject();
    WolfTheProblemSolver wolfgang = WolfTheProblemSolver.getInstance(project);
    if (wolfgang.hasProblemFilesBeneath(module)) {
      if (logger.seenTagPrefix(TAG_RESOURCES_PREFIX)) {
        // Do we have errors in the res/ files?
        // See if it looks like we have aapt problems
        boolean haveResourceErrors = wolfgang.hasProblemFilesBeneath(new Condition<VirtualFile>() {
          @Override
          public boolean value(VirtualFile virtualFile) {
            return virtualFile.getFileType() == StdFileTypes.XML;
          }
        });
        if (haveResourceErrors) {
          builder.addBold("NOTE: This project contains resource errors, so aapt did not succeed, " +
                          "which can cause rendering failures. Fix resource problems first.");
          builder.newline().newline();
        }
      } else if (renderTask.getLayoutlibCallback().isUsed()) {
        boolean hasJavaErrors = wolfgang.hasProblemFilesBeneath(new Condition<VirtualFile>() {
          @Override
          public boolean value(VirtualFile virtualFile) {
            return virtualFile.getFileType() == StdFileTypes.JAVA;
          }
        });
        if (hasJavaErrors) {
          builder.addBold("NOTE: This project contains Java compilation errors, " +
                          "which can cause rendering failures for custom views. " +
                          "Fix compilation problems first.");
          builder.newline().newline();
        }
      }
    }
  }

  private void reportMissingSizeAttributes(@NotNull final RenderLogger logger, final HtmlBuilder builder, RenderTask renderTask) {
    Module module = logger.getModule();
    if (module == null) {
      return;
    }
    Project project = module.getProject();
    if (logger.isMissingSize()) {
      // Emit hyperlink about missing attributes; the action will operate on all of them
      builder.addBold("NOTE: One or more layouts are missing the layout_width or layout_height attributes. " +
                      "These are required in most layouts.").newline();
      final ResourceResolver resourceResolver = renderTask.getResourceResolver();
      XmlFile psiFile = renderTask.getPsiFile();
      if (psiFile == null) {
        LOG.error("PsiFile is missing in RenderTask used in RenderErrorPanel!");
        return;
      }
      AddMissingAttributesFix fix = new AddMissingAttributesFix(project, psiFile, resourceResolver);

      List<XmlTag> missing = fix.findViewsMissingSizes();

      // See whether we should offer match_parent instead of fill_parent
      AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(module);
      final String fill = moduleInfo == null
                          ||  moduleInfo.getBuildSdkVersion() == null
                          || moduleInfo.getBuildSdkVersion().getApiLevel() >= 8
                          ? VALUE_MATCH_PARENT : VALUE_FILL_PARENT;

      for (final XmlTag tag : missing) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            boolean missingWidth = !AddMissingAttributesFix.definesWidth(tag, resourceResolver);
            boolean missingHeight = !AddMissingAttributesFix.definesHeight(tag, resourceResolver);
            assert missingWidth || missingHeight;

            String id = tag.getAttributeValue(ATTR_ID);
            if (id == null || id.length() == 0) {
              id = '<' + tag.getName() + '>';
            }
            else {
              id = '"' + stripIdPrefix(id) + '"';
            }

            if (missingWidth) {
              reportMissingSize(builder, logger, fill, tag, id, ATTR_LAYOUT_WIDTH);
            }
            if (missingHeight) {
              reportMissingSize(builder, logger, fill, tag, id, ATTR_LAYOUT_HEIGHT);
            }
          }
        });
      }

      builder.newline();
      builder.add("Or: ");
      builder.addLink("Automatically add all missing attributes", myLinkManager.createCommandLink(fix)).newline();
      builder.newline().newline();
    }
  }

  private void reportOtherProblems(RenderLogger logger, HtmlBuilder builder) {
    List<RenderProblem> messages = logger.getMessages();
    if (messages != null && !messages.isEmpty()) {
      Set<String> seenTags = Sets.newHashSet();
      for (RenderProblem message : messages) {
        String tag = message.getTag();
        if (tag != null && seenTags.contains(tag)) {
          continue;
        }
        seenTags.add(tag);

        HighlightSeverity severity = message.getSeverity();
        if (severity == HighlightSeverity.ERROR) {
          builder.addIcon(HtmlBuilderHelper.getErrorIconPath());
        } else if (severity == HighlightSeverity.WARNING) {
          builder.addIcon(HtmlBuilderHelper.getWarningIconPath());
        }

        String html = message.getHtml();
        Throwable throwable = message.getThrowable();

        if (throwable != null) {
          reportSandboxError(builder, throwable, false, true);
          if (reportThrowable(builder, throwable, !html.isEmpty() || !message.isDefaultHtml())) {
            // The error was hidden.
            if (!html.isEmpty()) {
              builder.getStringBuilder().append(html);
              builder.newlineIfNecessary();
            }
          }
        } else {
          builder.getStringBuilder().append(html);
          builder.newlineIfNecessary();
        }

        if (tag != null) {
          if (LayoutLog.TAG_RESOURCES_FORMAT.equals(tag)) {
            appendFlagValueSuggestions(builder, message);
          }

          int count = logger.getTagCount(tag);
          if (count > 1) {
            builder.add(" (").addHtml(Integer.toString(count)).add(" similar errors not shown)");
          }
        }

        builder.newline();
      }
    }
  }

  private void appendFlagValueSuggestions(HtmlBuilder builder, RenderProblem message) {
    Object clientData = message.getClientData();
    if (!(clientData instanceof String[])) {
      return;
    }
    String[] strings = (String[])clientData;
    if (strings.length != 2) {
      return;
    }

    RenderTask renderTask = myResult.getRenderTask();
    if (renderTask == null) {
      return;
    }
    IAndroidTarget target = renderTask.getConfiguration().getRealTarget();
    if (target == null) {
      return;
    }
    AndroidPlatform platform = renderTask.getPlatform();
    if (platform == null) {
      return;
    }
    AndroidTargetData targetData = platform.getSdkData().getTargetData(target);
    AttributeDefinitions definitionLookup = targetData.getPublicAttrDefs(myResult.getFile().getProject());
    final String attributeName = strings[0];
    final String currentValue = strings[1];
    if (definitionLookup == null) {
      return;
    }
    AttributeDefinition definition = definitionLookup.getAttrDefByName(attributeName);
    if (definition == null) {
      return;
    }
    Set<AttributeFormat> formats = definition.getFormats();
    if (formats.contains(AttributeFormat.Flag) || formats.contains(AttributeFormat.Enum)) {
      String[] values = definition.getValues();
      if (values.length > 0) {
        builder.newline();
        builder.addNbsps(4);
        builder.add("Change ").add(currentValue).add(" to: ");
        boolean first = true;
        for (String value : values) {
          if (first) {
            first = false;
          } else {
            builder.add(", ");
          }
          builder.addLink(value, myLinkManager.createReplaceAttributeValueUrl(attributeName, currentValue, value));
        }
      }
    }
  }

  /**
   * Display the problem list encountered during a render.
   *
   * @return if the throwable was hidden.
   */
  private boolean reportThrowable(@NotNull HtmlBuilder builder, @NotNull final Throwable throwable, boolean hideIfIrrelevant) {
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
        if (RenderLogger.isLoggingAllErrors()) {
          ShowExceptionFix detailsFix = new ShowExceptionFix(myResult.getModule().getProject(), throwable);
          builder.addLink("Show Exception", myLinkManager.createRunnableLink(detailsFix));
        }
        return true;
      } else {
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

    builder.add(throwable.toString()).newline();

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
            builder.addNbsps(indent);
            builder.add("    ...").newline();
            wasHidden = false;
          }
          String url = myLinkManager.createOpenStackUrl(className, methodName, fileName, lineNumber);
          builder.add("(").addLink(location, url).add(")");
        } else {
          // Try to link to local documentation
          String url = null;
          if (isFramework(frame) && platformSourceExists) { // try to link to documentation, if available
            if (platformSource == null) {
              IAndroidTarget target = myResult.getRenderTask().getConfiguration().getRealTarget();
              platformSource = AndroidSdkUtils.findPlatformSources(target);
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
          } else {
            builder.add("(").add(location).add(")");
          }
        }
        builder.newline();
      }
    }

    builder.addLink("Copy stack to clipboard", myLinkManager.createRunnableLink(new Runnable() {
      @Override
      public void run() {
        String text = Throwables.getStackTraceAsString(throwable);
        try {
          CopyPasteManager.getInstance().setContents(new StringSelection(text));
        }
        catch (Exception ignore) {
        }
      }
    }));
    return false;
  }

  private static boolean isHiddenFrame(StackTraceElement frame) {
    String className = frame.getClassName();
    return
      className.startsWith("sun.reflect.") ||
      className.equals("android.view.BridgeInflater") ||
      className.startsWith("com.android.tools.") ||
      className.startsWith("org.jetbrains.");
  }

  private static boolean isInterestingFrame(StackTraceElement frame) {
    String className = frame.getClassName();
    return !(className.startsWith("android.")          //$NON-NLS-1$
             || className.startsWith("org.jetbrains.") //$NON-NLS-1$
             || className.startsWith("com.android.")   //$NON-NLS-1$
             || className.startsWith("java.")          //$NON-NLS-1$
             || className.startsWith("javax.")         //$NON-NLS-1$
             || className.startsWith("sun."));         //$NON-NLS-1$
  }

  private static boolean isFramework(StackTraceElement frame) {
    String className = frame.getClassName();
    return (className.startsWith("android.")          //$NON-NLS-1$
             || className.startsWith("java.")          //$NON-NLS-1$
             || className.startsWith("javax.")         //$NON-NLS-1$
             || className.startsWith("sun."));         //$NON-NLS-1$
  }

  private static boolean isVisible(StackTraceElement frame) {
    String className = frame.getClassName();
    return !(isFramework(frame) || className.startsWith("sun.")); //$NON-NLS-1$
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
    Project project = module.getProject();
    String wrapUrl = myLinkManager.createCommandLink(new SetAttributeFix(project, tag, attribute, ANDROID_URI, VALUE_WRAP_CONTENT));
    String fillUrl = myLinkManager.createCommandLink(new SetAttributeFix(project, tag, attribute, ANDROID_URI, fill));

    builder.add(String.format("%1$s does not set the required %2$s attribute: ", id, attribute));
    builder.newline();
    builder.addNbsps(4);
    builder.addLink("Set to wrap_content", wrapUrl);
    builder.add(", ");
    builder.addLink("Set to " + fill, fillUrl);
    builder.newline();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void showEmpty() {
    UIUtil.invokeLaterIfNeeded(() -> {
      try {
        myHTMLViewer.read(new StringReader("<html><body></body></html>"), null);
      }
      catch (IOException e) {
        // can't be
      }
    });
  }

  private void reportInstantiationProblems(@NotNull final RenderLogger logger, @NotNull HtmlBuilder builder) {
    Map<String, Throwable> classesWithIncorrectFormat = logger.getClassesWithIncorrectFormat();
    if (classesWithIncorrectFormat != null && !classesWithIncorrectFormat.isEmpty()) {
      builder.add("Preview might be incorrect: unsupported class version.").newline();
      builder.addIcon(HtmlBuilderHelper.getTipIconPath());
      builder.add("Tip: ");

      builder.add("You need to run the IDE with the highest JDK version that you are compiling custom views with. ");

      int highest = ClassConverter.findHighestMajorVersion(classesWithIncorrectFormat.values());
      if (highest > 0 && highest > ClassConverter.getCurrentClassVersion()) {
        builder.add("One or more views have been compiled with JDK ");
        String required = ClassConverter.classVersionToJdk(highest);
        builder.add(required);
        builder.add(", but you are running the IDE on JDK ");
        builder.add(ClassConverter.getCurrentJdkVersion());
        builder.add(". ");
      } else {
        builder.add("For example, if you are compiling with sourceCompatibility 1.7, you must run the IDE with JDK 1.7. ");
      }
      builder.add("Running on a higher JDK is necessary such that these classes can be run in the layout renderer. " +
                  "(Or, extract your custom views into a library which you compile with a lower JDK version.)");
      builder.newline().newline();
      builder.addLink("If you have just accidentally built your code with a later JDK, try to ", "build", " the project.",
                      myLinkManager.createCompileModuleUrl());
      builder.newline().newline();
      builder.add("Classes with incompatible format:");

      builder.beginList();
      List<String> names = Lists.newArrayList(classesWithIncorrectFormat.keySet());
      Collections.sort(names);
      for (String className : names) {
        builder.listItem();
        builder.add(className);
        //noinspection ThrowableResultOfMethodCallIgnored
        Throwable throwable = classesWithIncorrectFormat.get(className);
        if (throwable instanceof InconvertibleClassError) {
          InconvertibleClassError error = (InconvertibleClassError)throwable;
          builder.add(" (Compiled with ");
          builder.add(ClassConverter.classVersionToJdk(error.getMajor()));
          builder.add(")");
        }
      }
      builder.endList();

      Module module = logger.getModule();
      if (module == null) {
        return;
      }
      final List<Module> problemModules = getProblemModules(module);
      if (!problemModules.isEmpty()) {
        builder.add("The following modules are built with incompatible JDK:").newline();
        for (Iterator<Module> it = problemModules.iterator(); it.hasNext(); ) {
          Module problemModule = it.next();
          builder.add(problemModule.getName());
          if (it.hasNext()) {
            builder.add(", ");
          }
        }
        builder.newline();
      }

      AndroidFacet facet = AndroidFacet.getInstance(logger.getModule());
      if (facet != null && !facet.requiresAndroidModel()) {
        Project project = logger.getModule().getProject();
        builder.addLink("Rebuild project with '-target 1.6'", myLinkManager.createRunnableLink(new RebuildWith16Fix(project)));
        builder.newline();

        if (!problemModules.isEmpty()) {
          builder.addLink("Change Java SDK to 1.6", myLinkManager.createRunnableLink(new SwitchTo16Fix(project, problemModules)));
          builder.newline();
        }
      }
    }
  }

  // Code copied from the old RenderUtil

  private static void askAndRebuild(Project project) {
    final int r = Messages.showYesNoDialog(project, "You have to rebuild project to see the fixed preview. Would you like to do it?",
                                           "Rebuild Project", Messages.getQuestionIcon());
    if (r == Messages.YES) {
      CompilerManager.getInstance(project).rebuild(null);
    }
  }

  @NotNull
  private static Set<String> getSdkNamesFromModules(@NotNull Collection<Module> modules) {
    final Set<String> result = new HashSet<String>();
    for (Module module : modules) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();

      if (sdk != null) {
        result.add(sdk.getName());
      }
    }
    return result;
  }

  @NotNull
  private static List<Module> getProblemModules(@NotNull Module root) {
    final List<Module> result = new ArrayList<Module>();
    collectProblemModules(root, new HashSet<Module>(), result);
    return result;
  }

  private static void collectProblemModules(@NotNull Module module, @NotNull Set<Module> visited, @NotNull Collection<Module> result) {
    if (!visited.add(module)) {
      return;
    }

    if (isBuiltByJdk7OrHigher(module)) {
      result.add(module);
    }

    for (Module depModule : ModuleRootManager.getInstance(module).getDependencies(false)) {
      collectProblemModules(depModule, visited, result);
    }
  }

  static boolean isBuiltByJdk7OrHigher(@NotNull Module module) {
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null) {
      return false;
    }

    if (isAndroidSdk(sdk)) {
      AndroidSdkAdditionalData data = getAndroidSdkAdditionalData(sdk);
      if (data != null) {
        Sdk jdk = data.getJavaSdk();
        if (jdk != null) {
          sdk = jdk;
        }
      }
    }
    return sdk.getSdkType() instanceof JavaSdk &&
           JavaSdk.getInstance().isOfVersionOrHigher(sdk, JavaSdkVersion.JDK_1_7);
  }

  private static class RebuildWith16Fix implements Runnable {
    private final Project myProject;

    private RebuildWith16Fix(Project project) {
      myProject = project;
    }

    @Override
    public void run() {
      final JpsJavaCompilerOptions settings = JavacConfiguration.getOptions(myProject, JavacConfiguration.class);
      if (settings.ADDITIONAL_OPTIONS_STRING.length() > 0) {
        settings.ADDITIONAL_OPTIONS_STRING += ' ';
      }
      settings.ADDITIONAL_OPTIONS_STRING += "-target 1.6";
      CompilerManager.getInstance(myProject).rebuild(null);
    }
  }

  private static class SwitchTo16Fix implements Runnable {
    private final Project myProject;
    final List<Module> myProblemModules;

    private SwitchTo16Fix(Project project, List<Module> problemModules) {
      myProject = project;
      myProblemModules = problemModules;
    }

    @Override
    public void run() {
      final Set<String> sdkNames = getSdkNamesFromModules(myProblemModules);
      if (sdkNames.size() == 1) {
        final Sdk sdk = ProjectJdkTable.getInstance().findJdk(sdkNames.iterator().next());
        if (sdk != null && sdk.getSdkType() instanceof AndroidSdkType) {
          final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(myProject);
          if (ShowSettingsUtil.getInstance().editConfigurable(myProject, config, new Runnable() {
            @Override
            public void run() {
              config.select(sdk, true);
            }
          })) {
            askAndRebuild(myProject);
          }
          return;
        }
      }

      final String moduleToSelect = myProblemModules.size() > 0
                                    ? myProblemModules.iterator().next().getName()
                                    : null;
      if (ModulesConfigurator.showDialog(myProject, moduleToSelect, ClasspathEditor.NAME)) {
        askAndRebuild(myProject);
      }
    }
  }

  public static class HtmlBuilderHelper {
    @Nullable
    private static String getIconPath(String relative) {
      // TODO: Find a way to do this more efficiently; not referencing assets but the corresponding
      // AllIcons constants, and loading them into HTML class loader contexts?
      URL resource = AllIcons.class.getClassLoader().getResource(relative);
      try {
        return (resource != null) ? resource.toURI().toURL().toExternalForm() : null;
      }
      catch (MalformedURLException e) {
        return null;
      }
      catch (URISyntaxException e) {
        return null;
      }
    }

    @Nullable
    public static String getCloseIconPath() {
      return getIconPath("/actions/closeNew.png");
    }

    @Nullable
    public static String getTipIconPath() {
      return getIconPath("/actions/createFromUsage.png");
    }

    @Nullable
    public static String getWarningIconPath() {
      return getIconPath("/general/warningDialog.png");
    }

    @Nullable
    public static String getErrorIconPath() {
      return getIconPath("/general/error.png");
    }

    public static String getHeaderFontColor() {
      // See om.intellij.codeInspection.HtmlComposer.appendHeading
      // (which operates on StringBuffers)
      return UIUtil.isUnderDarcula() ? "#A5C25C" : "#005555";
    }
  }
}

