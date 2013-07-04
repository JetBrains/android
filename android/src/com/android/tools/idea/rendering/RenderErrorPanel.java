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

import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.Density;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.RenderContext;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.ide.DataManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidTargetData;
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
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;
import static com.android.ide.common.rendering.api.LayoutLog.TAG_RESOURCES_PREFIX;
import static com.android.ide.common.rendering.api.LayoutLog.TAG_RESOURCES_RESOLVE_THEME_ATTR;
import static com.android.tools.idea.configurations.RenderContext.UsageType.LAYOUT_EDITOR;
import static com.android.tools.idea.rendering.HtmlLinkManager.URL_ACTION_CLOSE;
import static com.android.tools.idea.rendering.ResourceHelper.viewNeedsPackage;
import static com.android.tools.lint.detector.api.LintUtils.editDistance;
import static com.android.tools.lint.detector.api.LintUtils.stripIdPrefix;

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

  private JEditorPane myHTMLViewer;
  private final HyperlinkListener myHyperLinkListener;
  private RenderResult myResult;
  private HtmlLinkManager myLinkManager;
  private final JScrollPane myScrollPane;

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
      showEmpty();
      myResult = null;
      myLinkManager = null;
      return null;
    }
    myResult = result;
    myLinkManager = result.getLogger().getLinkManager();

    try {
      // Generate HTML under a read lock, since many errors require peeking into the PSI
      // to for example find class names to suggest as typo replacements
      String html = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Override
        public String compute() {
          return generateHtml(result);
        }
      });
      myHTMLViewer.read(new StringReader(html), null);
      setupStyle();
      myHTMLViewer.setCaretPosition(0);
      return html;
    }
    catch (Exception e) {
      showEmpty();
      return null;
    }
  }

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
          Module module = myResult.getModule();
          PsiFile file = myResult.getFile();
          DataContext dataContext = DataManager.getInstance().getDataContext(RenderErrorPanel.this);
          assert dataContext != null;

          myLinkManager.handleUrl(url, module, file, dataContext, myResult);
        }
      }
    };
    myHTMLViewer.addHyperlinkListener(myHyperLinkListener);
    myHTMLViewer.setMargin(new Insets(3, 3, 3, 3));

    myScrollPane = ScrollPaneFactory.createScrollPane(myHTMLViewer);
    setupStyle();

    add(myScrollPane, BorderLayout.CENTER);
  }

  private void close() {
    this.setVisible(false);
  }

  private void setupStyle() {
    // Make the scrollPane transparent
    if (myScrollPane != null) {
      JViewport viewPort = myScrollPane.getViewport();
      viewPort.setOpaque(false);
      viewPort.setBackground(null);
      myScrollPane.setOpaque(false);
      myScrollPane.setBackground(null);
    }

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
      background = new Color(background.getRed(), background.getGreen(), background.getBlue(), ERROR_PANEL_OPACITY);
      myHTMLViewer.setBackground(background);
    }
  }

  public int getPreferredHeight(@SuppressWarnings("UnusedParameters") int width) {
    return myHTMLViewer.getPreferredSize().height;
  }

  private String generateHtml(@NotNull RenderResult result) {
    RenderLogger logger = result.getLogger();
    RenderService renderService = result.getRenderService();
    assert logger.hasProblems();

    HtmlBuilder builder = new HtmlBuilder(new StringBuilder(300));
    builder.openHtmlBody();

    // Construct close button. Sadly <img align="right"> doesn't work in JEditorPanes; would
    // have looked a lot nicer with the image flushed to the right!
    builder.addHtml("<A HREF=\"");
    builder.addHtml(URL_ACTION_CLOSE);
    builder.addHtml("\">");
    builder.addCloseIcon();
    builder.addHtml("</A>");
    builder.addHeading("Rendering Problems").newline();

    reportMissingStyles(logger, builder);
    if (renderService != null) {
      reportOldNinePathRenderLib(logger, builder, renderService);
      reportRelevantCompilationErrors(logger, builder, renderService);
      reportMissingSizeAttributes(logger, builder, renderService);
      reportMissingClasses(logger, builder, renderService);
    }
    reportBrokenClasses(logger, builder);
    reportInstantiationProblems(logger, builder);
    reportOtherProblems(logger, builder);
    reportUnknownFragments(logger, builder);
    if (renderService != null) {
      reportRenderingFidelityProblems(logger, builder, renderService);
    }

    builder.closeHtmlBody();

    return builder.getHtml();
  }

  private void reportMissingClasses(@NotNull RenderLogger logger, @NotNull HtmlBuilder builder, @NotNull RenderService renderService) {
    Set<String> missingClasses = logger.getMissingClasses();
    if (missingClasses != null && !missingClasses.isEmpty()) {
      if (missingClasses.contains("CalendarView")) {
        builder.add("The ").addBold("CalendarView").add(" widget does not work correctly with this render target. " +
            "As a workaround, try using the API 5 (Android 4.0.3) render target library by selecting it from the " +
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
      Collection<String> views = getAllViews(logger.getModule());
      if (!views.isEmpty()) {
        customViews = Lists.newArrayListWithExpectedSize(Math.max(10, views.size() - 80)); // most will be framework views
        androidViewClassNames = Lists.newArrayListWithExpectedSize(views.size());
        for (String fqcn : views) {
          if (fqcn.startsWith("android.") && !viewNeedsPackage(fqcn)) {
            androidViewClassNames.add(fqcn);
          } else {
            customViews.add(fqcn);
          }
        }
      }

      if (missingResourceClass) {
        // TODO: Use projectCallBack isUsed rather than hasLoadedClasses; an *attempt* is enough!
        builder.listItem();
        builder.add(logger.getResourceClass());
      }

      for (String className : missingClasses) {
        builder.listItem();
        builder.add(className);
        builder.add(" (");

        addTypoSuggestions(builder, className, customViews, false);
        addTypoSuggestions(builder, className, customViews, true);
        addTypoSuggestions(builder, className, androidViewClassNames, false);

        builder.addLink("Fix Build Path", myLinkManager.createEditClassPathUrl());

        RenderContext renderContext = renderService.getRenderContext();
        if (renderContext != null && renderContext.getType() == LAYOUT_EDITOR) {
          builder.add(", ");
          builder.addLink("Edit XML", myLinkManager.createShowTagUrl(className));
        }

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

      builder.addTipIcon();
      builder.addLink("Tip: Try to ", "build", " the project", myLinkManager.createCompileModuleUrl());
      builder.newline().newline();
    }
  }

  private void addTypoSuggestions(@NotNull HtmlBuilder builder,
                                  String actual,
                                  @Nullable Collection<String> views,
                                  boolean compareWithPackage) {
    if (views == null || views.isEmpty()) {
      return;
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
          } else if (actualBase.equals(actual) && !actualBase.equals(suggested) && viewNeedsPackage(suggested)) {
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

        if (editDistance(match, matchWith) <= maxDistance) {
          // Suggest this class as a typo for the given class
          String labelClass = (suggestedBase.equals(actual) || actual.indexOf('.') != -1) ? suggested : suggestedBase;
          builder.addLink(String.format("Change to %1$s", labelClass),
                          myLinkManager.createReplaceTagsUrl(actual,
                          // Only show full package name if class name
                          // is the same
                          (viewNeedsPackage(suggested) ? suggested : suggestedBase)));
          builder.add(", ");
        }
      }
    }
  }

  private void reportUnknownFragments(@NotNull RenderLogger logger, @NotNull HtmlBuilder builder) {
    List<String> fragmentNames = logger.getMissingFragments();
    if (fragmentNames != null && !fragmentNames.isEmpty()) {

      builder.add("A ").addHtml("<code>").add("<fragment>").addHtml("</code>").add(" tag allows a layout file to dynamically include " +
                                                                                   "different layouts at runtime. ");
      builder.add("At layout editing time the specific layout to be used is not known. You can choose which layout you would " +
                  "like previewed while editing the layout.");
      builder.beginList();

      // TODO: Add link to not warn any more for this session

      for (String className : fragmentNames) {
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
          // TODO: Look up layout references in the given layout, if possible
          // Find activity class
          // Look for R references in the layout
          Module module = logger.getModule();
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
        } else {
          builder.addLink("Choose Fragment Class...", myLinkManager.createAssignFragmentUrl(className));
        }
        builder.add(")");
      }
      builder.endList();
      builder.newline();
// TODO: URLs
      builder.addLink("Do not warn about <fragment> tags in this session", myLinkManager.createIgnoreFragmentsUrl());
    }
  }

  @NotNull
  private static Collection<String> getAllViews(@NotNull Module module) {
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
  private static Collection<PsiClass> findInheritors(@NotNull Module module, @NotNull String name) {
    Project project = module.getProject();
    PsiClass base = JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project));
    if (base != null) {
      GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
      return ClassInheritorsSearch.search(base, scope, true).findAll();
    }
    return Collections.emptyList();
  }

  private void reportBrokenClasses(@NotNull RenderLogger logger, @NotNull HtmlBuilder builder) {
    Map<String,Throwable> brokenClasses = logger.getBrokenClasses();
    if (brokenClasses != null && !brokenClasses.isEmpty()) {
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
        if (throwable != null) {
          builder.add(", ");
          ShowExceptionFix detailsFix = new ShowExceptionFix(logger.getModule().getProject(), throwable);
          builder.addLink("Show Exception", myLinkManager.createRunnableLink(detailsFix));
        }
        builder.add(")");

        if (firstThrowable == null && throwable != null) {
          firstThrowable = throwable;
        }
      }
      builder.endList();

      builder.addTipIcon();
      builder.addLink("Tip: Use ", "View.isInEditMode()", " in your custom views to skip code or show sample data when shown in the IDE",
                      "http://developer.android.com/reference/android/view/View.html#isInEditMode()");

      if (firstThrowable != null) {
        builder.newline().newline();
        builder.addHeading("Exception Details").newline();
        reportThrowable(builder, firstThrowable);
      }
      builder.newline().newline();
    }
  }

  private void reportRenderingFidelityProblems(@NotNull RenderLogger logger, @NotNull HtmlBuilder builder,
                                               @NotNull final RenderService renderService) {
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
          builder.addLink(" (Ignore for this session", myLinkManager.createRunnableLink(new Runnable() {
            @Override
            public void run() {
              RenderLogger.ignoreFidelityWarning(clientData);
              RenderContext renderContext = renderService.getRenderContext();
              if (renderContext != null) {
                renderContext.requestRender();
              }
            }
          }));
        }
        builder.newline();
        count++;
        // Only display the first 3 render fidelity issues
        if (count == 3) {
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
          RenderContext renderContext = renderService.getRenderContext();
          if (renderContext != null) {
            renderContext.requestRender();
          }
        }
      }));
      builder.newline();
    }
  }

  private static void reportMissingStyles(RenderLogger logger, HtmlBuilder builder) {
    if (logger.seenTagPrefix(TAG_RESOURCES_RESOLVE_THEME_ATTR)) {
      builder.addBold("Missing styles. Is the correct theme chosen for this layout?").newline();
      builder.addTipIcon();
      builder.add("Use the Theme combo box above the layout to choose a different layout, or fix the theme style references.");
      builder.newline().newline();
    }
  }

  private static void reportOldNinePathRenderLib(RenderLogger logger, HtmlBuilder builder, @NotNull RenderService renderService) {
    for (Throwable trace : logger.getTraces()) {
      if (trace.toString().contains("java.lang.IndexOutOfBoundsException: Index: 2, Size: 2") //$NON-NLS-1$
          && renderService.getConfiguration().getDensity() == Density.TV) {
        builder.addBold("It looks like you are using a render target where the layout library does not support the tvdpi density.");
        builder.newline().newline();
        builder.add("Please try either updating to the latest available version (using the SDK manager), or if no updated " +
                    "version is available for this specific version of Android, try using a more recent render target version.");
        builder.newline().newline();
        break;
      }
    }
  }

  private static void reportRelevantCompilationErrors(RenderLogger logger, HtmlBuilder builder, RenderService renderService) {
    Module module = logger.getModule();
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
      } else if (renderService.getProjectCallback() != null && renderService.getProjectCallback().isUsed()) {
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

  private void reportMissingSizeAttributes(@NotNull RenderLogger logger, HtmlBuilder builder, RenderService renderService) {
    Module module = logger.getModule();
    Project project = module.getProject();
    if (logger.isMissingSize()) {
      // Emit hyperlink about missing attributes; the action will operate on all of them
      builder.addBold("NOTE: One or more layouts are missing the layout_width or layout_height attributes. " +
                      "These are required in most layouts.").newline();
      ResourceResolver resourceResolver = renderService.getResourceResolver();
      AddMissingAttributesFix fix = new AddMissingAttributesFix(project, renderService.getPsiFile(), resourceResolver);

      List<XmlTag> missing = fix.findViewsMissingSizes();

      String fill = VALUE_FILL_PARENT;
      // See whether we should offer match_parent instead of fill_parent
      AndroidPlatform platform = renderService.getPlatform();
      if (platform != null && platform.getTarget().getVersion().getApiLevel() >= 8) {
        fill = VALUE_MATCH_PARENT;
      }

      for (XmlTag tag : missing) {
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
          builder.addErrorIcon();
        } else if (severity == HighlightSeverity.WARNING) {
          builder.addWarningIcon();
        }

        message.appendHtml(builder.getStringBuilder());

        Throwable throwable = message.getThrowable();
        if (throwable != null) {
          reportThrowable(builder, throwable);
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

    RenderService renderService = myResult.getRenderService();
    if (renderService == null) {
      return;
    }
    IAndroidTarget target = renderService.getConfiguration().getTarget();
    if (target == null) {
      return;
    }
    AndroidPlatform platform = renderService.getPlatform();
    if (platform == null) {
      return;
    }
    AndroidTargetData targetData = platform.getSdkData().getTargetData(target);
    AttributeDefinitions definitionLookup = targetData.getAttrDefs(myResult.getFile().getProject());
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

  /** Display the problem list encountered during a render */
  private void reportThrowable(@NotNull HtmlBuilder builder, @NotNull Throwable throwable) {
    StackTraceElement[] frames = throwable.getStackTrace();
    int end = -1;
    boolean haveInterestingFrame = false;
    for (int i = 0; i < frames.length; i++) {
      StackTraceElement frame = frames[i];
      if (isInterestingFrame(frame)) {
        haveInterestingFrame = true;
      }
      String className = frame.getClassName();
      if (className.equals("com.android.layoutlib.bridge.impl.RenderSessionImpl")) { //$NON-NLS-1$
        end = i;
        break;
      }
    }

    if (end == -1 || !haveInterestingFrame) {
      // Not a recognized stack trace range: just skip it
      return;
    }

    builder.add(throwable.toString()).newline();

    boolean wasHidden = false;
    int indent = 2;
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
        }
        builder.newline();
      }
    }
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

  private void reportMissingSize(@NotNull HtmlBuilder builder,
                                 @NotNull RenderLogger logger,
                                 @NotNull String fill,
                                 @NotNull XmlTag tag,
                                 @NotNull String id,
                                 @NotNull String attribute) {
    Project project = logger.getModule().getProject();
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
    try {
      myHTMLViewer.read(new StringReader("<html><body></body></html>"), null);
    }
    catch (IOException e) {
      // can't be
    }
  }

  private void reportInstantiationProblems(@NotNull final RenderLogger logger, @NotNull HtmlBuilder builder) {
    Set<String> classesWithIncorrectFormat = logger.getClassesWithIncorrectFormat();
    if (classesWithIncorrectFormat != null && !classesWithIncorrectFormat.isEmpty()) {
      builder.add("Preview might be incorrect: unsupported class version.").newline();
      builder.add("Classes with incompatible format:");

      builder.beginList();
      for (String className : classesWithIncorrectFormat) {
        builder.listItem();
        builder.add(className);
      }
      builder.endList();


      Module module = logger.getModule();
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

      Project project = logger.getModule().getProject();
      builder.addLink("Rebuild project with '-target 1.6'", myLinkManager.createRunnableLink(new RebuildWith16Fix(project)));
      builder.newline();

      if (!problemModules.isEmpty()) {
        builder.addLink("Change Java SDK to 1.5/1.6", myLinkManager.createRunnableLink(new SwitchTo16Fix(project, problemModules)));
        builder.newline();
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

  private static boolean isBuiltByJdk7OrHigher(@NotNull Module module) {
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null) {
      return false;
    }

    if (sdk.getSdkType() instanceof AndroidSdkType) {
      final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (data != null) {
        final Sdk jdk = data.getJavaSdk();
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
}

