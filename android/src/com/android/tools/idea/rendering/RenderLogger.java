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

import com.android.ide.common.rendering.RenderSecurityManager;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.repository.Revision;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.utils.HtmlBuilder;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;
import static com.intellij.lang.annotation.HighlightSeverity.WARNING;

/**
 * A {@link LayoutLog} which records the problems it encounters and offers them as a
 * single summary at the end
 */
public class RenderLogger extends LayoutLog {
  static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.RenderLogger");
  /**
   * Whether render errors should be sent to the IDE log. We generally don't want this, since if for
   * example a custom view generates an error, it will go to the IDE log, which will interpret it as an
   * IntelliJ error, and will blink the bottom right exception icon and offer to submit an exception
   * etc. All these errors should be routed through the render error panel instead. However, since the
   * render error panel does massage and collate the exceptions etc quite a bit, this flag is here
   * in case we need to ask bug submitters to generate full, raw exceptions.
   */
  @SuppressWarnings("UseOfArchaicSystemPropertyAccessors")
  private static final boolean LOG_ALL = Boolean.getBoolean("adt.renderLog");

  public static final String TAG_MISSING_DIMENSION = "missing.dimension";
  public static final String TAG_MISSING_FRAGMENT = "missing.fragment";
  public static final String TAG_STILL_BUILDING = "project.building";
  private static Set<String> ourIgnoredFidelityWarnings;
  private static boolean ourIgnoreAllFidelityWarnings;
  private static boolean ourIgnoreFragments;

  private final Module myModule;
  private final String myName;
  private Set<String> myFidelityWarningStrings;
  private boolean myHaveExceptions;
  private Map<String,Integer> myTags;
  private List<Throwable> myTraces;
  private List<RenderProblem> myMessages;
  private List<RenderProblem> myFidelityWarnings;
  private Set<String> myMissingClasses;
  private Map<String, Throwable> myBrokenClasses;
  private Map<String, Throwable> myClassesWithIncorrectFormat;
  private String myResourceClass;
  private boolean myMissingResourceClass;
  private boolean myHasLoadedClasses;
  private HtmlLinkManager myLinkManager;
  private boolean myMissingSize;
  private List<String> myMissingFragments;
  private Object myCredential;

  /**
   * Construct a logger for the given named layout. Don't call this method directly; obtain via {@link RenderService}.
   */
  public RenderLogger(@Nullable String name, @Nullable Module module, @Nullable Object credential) {
    myName = name;
    myModule = module;
    myCredential = credential;
  }

  /**
   * Construct a logger for the given named layout. Don't call this method directly; obtain via {@link RenderService}.
   */
  public RenderLogger(@Nullable String name, @Nullable Module module) {
    this(name, module, null);
  }

  @Nullable
  public Module getModule() {
    return myModule;
  }

  @Nullable
  public Project getProject() {
    if (myModule != null) {
      return myModule.getProject();
    }
    return null;
  }

  public void addMessage(@NotNull RenderProblem message) {
    if (myMessages == null) {
      myMessages = Lists.newArrayList();
    }
    myMessages.add(message);
  }

  @Nullable
  public List<RenderProblem> getMessages() {
    return myMessages;
  }

  /**
   * Are there any logged errors or warnings during the render?
   *
   * @return true if there were problems during the render
   */
  public boolean hasProblems() {
    return hasErrors() || myFidelityWarnings != null;
  }

  /**
   * Are there any logged errors during the render? (warnings are ignored)
   *
   * @return true if there were errors during the render
   */
  public boolean hasErrors() {
    return myHaveExceptions || myMessages != null ||
           myClassesWithIncorrectFormat != null || myBrokenClasses != null || myMissingClasses != null ||
           myMissingSize || myMissingFragments != null;
  }

  /**
   * Returns a list of traces encountered during rendering, or null if none
   *
   * @return a list of traces encountered during rendering, or null if none
   */
  @NotNull
  public List<Throwable> getTraces() {
    return myTraces != null ? myTraces : Collections.<Throwable>emptyList();
  }

  /**
   * Returns the fidelity warnings
   *
   * @return the fidelity warnings
   */
  @Nullable
  public List<RenderProblem> getFidelityWarnings() {
    return myFidelityWarnings;
  }

  // ---- extends LayoutLog ----

  @Override
  public void error(@Nullable String tag, @Nullable String message, @Nullable Object data) {
    String description = describe(message, null);

    if (LOG_ALL) {
      boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
      try {
        LOG.warn(String.format("%1$s: %2$s", myName, description));
      }
      finally {
        RenderSecurityManager.exitSafeRegion(token);
      }
    }

    // Workaround: older layout libraries don't provide a tag for this error
    if (tag == null && message != null &&
        (message.startsWith("Failed to find style ") || message.startsWith("Unable to resolve parent style name: "))) {
      tag = LayoutLog.TAG_RESOURCES_RESOLVE_THEME_ATTR;
    }
    addTag(tag);

    if (LayoutLog.TAG_RESOURCES_RESOLVE_THEME_ATTR.equals(tag) && myModule != null
        && BuildSettings.getInstance(myModule.getProject()).getBuildMode() == BuildMode.SOURCE_GEN) {
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      if (facet != null && facet.requiresAndroidModel()) {
        description = "Still building project; theme resources from libraries may be missing. Layout should refresh when the " +
                      "build is complete.\n\n" + description;
        tag = TAG_STILL_BUILDING;
        addTag(tag);
      }
    }

    addMessage(RenderProblem.createPlain(ERROR, description).tag(tag));
  }

  @Override
  public void error(@Nullable String tag, @Nullable String message, @Nullable Throwable throwable, @Nullable Object data) {
    String description = describe(message, throwable);
    if (LOG_ALL) {
      boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
      try {
        LOG.warn(String.format("%1$s: %2$s", myName, description), throwable);
      }
      finally {
        RenderSecurityManager.exitSafeRegion(token);
      }
    }
    if (throwable != null) {
      if (throwable instanceof ClassNotFoundException) {
        // The LayoutlibCallback is given a chance to resolve classes,
        // and when it fails, it will record it in its own list which
        // is displayed in a special way (with action hyperlinks etc).
        // Therefore, include these messages in the visible render log,
        // especially since the user message from a ClassNotFoundException
        // is really not helpful (it just lists the class name without
        // even mentioning that it is a class-not-found exception.)
        return;
      }

      if (checkForIssue164378(throwable)) {
        return;
      }

      if ("Unable to find the layout for Action Bar.".equals(description)) {
        description += "\nConsider updating to a more recent version of appcompat, or switch the rendering library in the IDE " +
                       "down to API 21";
      }

      if (description.equals(throwable.getLocalizedMessage()) || description.equals(throwable.getMessage())) {
        description = "Exception raised during rendering: " + description;
      } else if (message == null) {
        // See if it looks like the known issue with CalendarView; if so, add a more intuitive message
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        if (stackTrace.length >= 2 &&
          stackTrace[0].getClassName().equals("android.text.format.DateUtils") &&
          stackTrace[1].getClassName().equals("android.widget.CalendarView")) {
          RenderProblem.Html problem = RenderProblem.create(WARNING);
          problem.tag("59732");
          problem.throwable(throwable);
          HtmlBuilder builder = problem.getHtmlBuilder();
          builder.add("<CalendarView> and <DatePicker> are broken in this version of the rendering library. " +
                          "Try updating your SDK in the SDK Manager when issue 59732 is fixed.");
          builder.add(" (");
          builder.addLink("Open Issue 59732", "http://b.android.com/59732");
          builder.add(", ");
          ShowExceptionFix detailsFix = new ShowExceptionFix(getModule().getProject(), throwable);
          builder.addLink("Show Exception", getLinkManager().createRunnableLink(detailsFix));
          builder.add(")");
          addMessage(problem);
          return;
        } else if (stackTrace.length >= 2 &&
                   stackTrace[0].getClassName().equals("android.support.v7.widget.RecyclerView") &&
                   stackTrace[0].getMethodName().equals("onMeasure") &&
                   stackTrace[1].getClassName().equals("android.view.View") &&
                   throwable.toString().equals("java.lang.NullPointerException")) {
          RenderProblem.Html problem = RenderProblem.create(WARNING);
          String issue = "72117";
          problem.tag(issue);
          problem.throwable(throwable);
          HtmlBuilder builder = problem.getHtmlBuilder();
          builder.add("The new RecyclerView does not yet work in Studio. We are working on a fix. ");
          // TODO: Add more specific error message here when we know where we are fixing it, e.g. either
          // to update their layoutlib (if we work around it there), or a new version of the recyclerview AAR.
          builder.add(" (");
          builder.addLink("Open Issue " + issue, "http://b.android.com/" + issue);
          builder.add(", ");
          ShowExceptionFix detailsFix = new ShowExceptionFix(myModule.getProject(), throwable);
          builder.addLink("Show Exception", getLinkManager().createRunnableLink(detailsFix));
          builder.add(")");
          addMessage(problem);
          return;
        }
      } else if (message.startsWith("Failed to configure parser for ") && message.endsWith(DOT_PNG)) {
        // See if it looks like a mismatched bitmap/color; if so, make a more intuitive error message
        StackTraceElement[] frames = throwable.getStackTrace();
        for (StackTraceElement frame : frames) {
          if (frame.getMethodName().equals("createFromXml") && frame.getClassName().equals("android.content.res.ColorStateList")) {
            String path = message.substring("Failed to configure parser for ".length());
            RenderProblem.Html problem = RenderProblem.create(WARNING);
            problem.tag("bitmapAsColor");
            // deliberately not setting the throwable on the problem: exception is misleading
            HtmlBuilder builder = problem.getHtmlBuilder();
            builder.add("Resource error: Attempted to load a bitmap as a color state list.").newline();
            builder.add("Verify that your style/theme attributes are correct, and make sure layouts are using the right attributes.");
            builder.newline().newline();
            path = FileUtil.toSystemIndependentName(path);
            String basePath = FileUtil.toSystemIndependentName(myModule.getProject().getBasePath());
            if (path.startsWith(basePath)) {
              path = path.substring(basePath.length());
              if (path.startsWith(File.separator)) {
                path = path.substring(File.separator.length());
              }
            }
            path = FileUtil.toSystemDependentName(path);
            builder.add("The relevant image is ").add(path);
            Set<String> widgets = Sets.newHashSet();
            for (StackTraceElement f : frames) {
              if (f.getMethodName().equals(CONSTRUCTOR_NAME)) {
                String className = f.getClassName();
                if (className.startsWith(WIDGET_PKG_PREFIX)) {
                  widgets.add(className.substring(className.lastIndexOf('.') + 1));
                }
              }
            }
            if (!widgets.isEmpty()) {
              List<String> sorted = Lists.newArrayList(widgets);
              Collections.sort(sorted);
              builder.newline().newline().add("Widgets possibly involved: ").add(Joiner.on(", ").join(sorted));
            }

            addMessage(problem);
            return;
          } else if (frame.getClassName().startsWith("com.android.tools.")) {
            break;
          }
        }
      } else if (message.startsWith("Failed to parse file ") && throwable instanceof XmlPullParserException) {
        XmlPullParserException e = (XmlPullParserException)throwable;
        String msg = e.getMessage();
        if (msg.startsWith("Binary XML file ")) {
          int index = msg.indexOf(':');
          if (index != -1 && index < msg.length() - 1) {
            msg = msg.substring(index + 1).trim();
          }
        }
        int lineNumber = e.getLineNumber();
        int column = e.getColumnNumber();

        // Strip out useless input sources pointing back to the internal reader
        // e.g. "in java.io.InputStreamReader@4d957e26"
        String reader = " in java.io.InputStreamReader@";
        int index = msg.indexOf(reader);
        if (index != -1) {
          int end = msg.indexOf(')', index + 1);
          if (end != -1) {
            msg = msg.substring(0, index) + msg.substring(end);
          }
        }

        String path = message.substring("Failed to parse file ".length());

        RenderProblem.Html problem = RenderProblem.create(WARNING);
        problem.tag("xmlParse");

        // Don't include exceptions for XML parser errors: that's just displaying irrelevant
        // information about how we ended up parsing the file
        //problem.throwable(throwable);

        HtmlBuilder builder = problem.getHtmlBuilder();
        if (lineNumber != -1) {
          builder.add("Line ").add(Integer.toString(lineNumber)).add(": ");
        }
        builder.add(msg);
        if (lineNumber != -1) {
          builder.add(" (");
          File file = new File(path);
          String url = HtmlLinkManager.createFilePositionUrl(file, lineNumber, column);
          if (url != null) {
            builder.addLink("Show", url);
            builder.add(")");
          }
        }
        addMessage(problem);
        return;
      }

      recordThrowable(throwable);
      myHaveExceptions = true;
    }

    addTag(tag);
    if (getProject() == null) {
      addMessage(RenderProblem.createPlain(ERROR, description).tag(tag).throwable(throwable));
    } else {
      addMessage(RenderProblem.createPlain(ERROR, description, getProject(), getLinkManager(), throwable).tag(tag));
    }

  }

  /**
   * Record that the given exception was encountered during rendering
   *
   * @param throwable the exception that was raised
   */
  public void recordThrowable(@NotNull Throwable throwable) {
    if (myTraces == null) {
      myTraces = new ArrayList<Throwable>();
    }
    myTraces.add(throwable);
  }

  @Override
  public void warning(@Nullable String tag, @NotNull String message, @Nullable Object data) {
    String description = describe(message, null);

    if (TAG_INFO.equals(tag)) {
      Logger.getInstance(getClass()).info(description);
      return;
    }
    if (TAG_RESOURCES_FORMAT.equals(tag)) {
      // TODO: Accumulate multiple hits of this form and synthesize into one
      if (description.equals("You must supply a layout_width attribute.")       //$NON-NLS-1$
          || description.equals("You must supply a layout_height attribute.")) {//$NON-NLS-1$
        // Don't log these messages individually; you get one for each missing width and each missing height,
        // but there is no correlation to the specific view which is using the given TypedArray,
        // so instead just record that fact that *some* views were missing a dimension, and the
        // error summary will mention this, and add an action which lists the eligible views
        myMissingSize = true;
        addTag(TAG_MISSING_DIMENSION);
        return;
      }
      if (description.endsWith(" is not a valid value")) {
        // TODO: Consider performing the attribute search up front, rather than on link-click,
        // such that we don't add a link where we can't find the attribute in the current layout
        // (e.g. it is coming somewhere from an <include> context, etc
        Pattern pattern = Pattern.compile("\"(.*)\" in attribute \"(.*)\" is not a valid value");
        Matcher matcher = pattern.matcher(description);
        if (matcher.matches()) {
          addTag(tag);
          RenderProblem.Html problem = RenderProblem.create(WARNING);
          problem.tag(tag);
          String attribute = matcher.group(2);
          String value = matcher.group(1);
          problem.setClientData(new String[]{attribute, value});
          String url = getLinkManager().createEditAttributeUrl(attribute, value);
          problem.getHtmlBuilder().add(description).add(" (").addLink("Edit", url).add(")");
          addMessage(problem);
          return;
        }
      }
      if (description.endsWith(" is not a valid format.")) {
        Pattern pattern = Pattern.compile("\"(.*)\" in attribute \"(.*)\" is not a valid format.");
        Matcher matcher = pattern.matcher(description);
        if (matcher.matches()) {
          addTag(tag);
          RenderProblem.Html problem = RenderProblem.create(WARNING);
          problem.tag(tag);
          String attribute = matcher.group(2);
          String value = matcher.group(1);
          problem.setClientData(new String[]{attribute, value});
          String url = getLinkManager().createEditAttributeUrl(attribute, value);
          problem.getHtmlBuilder().add(description).add(" (").addLink("Edit", url).add(")");
          problem.setClientData(url);
          addMessage(problem);
          return;
        }
      }
    } else if (TAG_MISSING_FRAGMENT.equals(tag)) {
      if (!ourIgnoreFragments) {
        if (myMissingFragments == null) {
          myMissingFragments = Lists.newArrayList();
        }
        String name = data instanceof String ? (String) data : null;
        myMissingFragments.add(name);
      }
      return;
    }

    addTag(tag);
    addMessage(RenderProblem.createPlain(WARNING, description).tag(tag));
  }

  @Override
  public void fidelityWarning(@Nullable String tag, @Nullable String message, @Nullable Throwable throwable, @Nullable Object data) {
    if (ourIgnoreAllFidelityWarnings || ourIgnoredFidelityWarnings != null && ourIgnoredFidelityWarnings.contains(message)) {
      return;
    }

    String description = describe(message, throwable);
    if (myFidelityWarningStrings != null && myFidelityWarningStrings.contains(description)) {
      // Exclude duplicates
      return;
    }

    if (LOG_ALL) {
      boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
      try {
        LOG.warn(String.format("%1$s: %2$s", myName, description), throwable);
      }
      finally {
        RenderSecurityManager.exitSafeRegion(token);
      }
    }

    if (throwable != null) {
      myHaveExceptions = true;
    }

    RenderProblem error = RenderProblem.createDeferred(ERROR, tag, description, throwable);
    error.setClientData(description);
    if (myFidelityWarnings == null) {
      myFidelityWarnings = new ArrayList<RenderProblem>();
      myFidelityWarningStrings = Sets.newHashSet();
    }

    myFidelityWarnings.add(error);
    assert myFidelityWarningStrings != null;
    myFidelityWarningStrings.add(description);
    addTag(tag);
  }

  /**
   * Ignore the given render fidelity warning for the current session
   *
   * @param clientData the client data stashed on the render problem
   */
  public static void ignoreFidelityWarning(@NotNull Object clientData) {
    if (ourIgnoredFidelityWarnings == null) {
      ourIgnoredFidelityWarnings = new HashSet<String>();
    }
    ourIgnoredFidelityWarnings.add((String) clientData);
  }

  public static void ignoreAllFidelityWarnings() {
    ourIgnoreAllFidelityWarnings = true;
  }

  public static void ignoreFragments() {
    ourIgnoreFragments = true;
  }

  @NotNull
  private static String describe(@Nullable String message, @Nullable Throwable throwable) {
    if (StringUtil.isEmptyOrSpaces(message)) {
      return throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "";
    }
    else {
      return message;
    }
  }

  // ---- Tags ----

  private void addTag(@Nullable String tag) {
    if (tag != null) {
      if (myTags == null) {
        myTags = Maps.newHashMap();
      }
      Integer count = myTags.get(tag);
      if (count == null) {
        myTags.put(tag, 1);
      } else {
        myTags.put(tag, count + 1);
      }
    }
  }

  /**
   * Returns true if the given tag prefix has been seen
   *
   * @param prefix the tag prefix to look for
   * @return true iff any tags with the given prefix was seen during the render
   */
  public boolean seenTagPrefix(@NotNull String prefix) {
    if (myTags != null) {
      for (String tag : myTags.keySet()) {
        if (tag.startsWith(prefix)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Returns the number of occurrences of the given tag
   *
   * @param tag the tag to look up
   * @return the number of occurrences of the given tag
   */
  public int getTagCount(@NotNull String tag) {
    Integer count = myTags != null ? myTags.get(tag) : null;
    return count != null ? count.intValue() : 0;
  }

  public HtmlLinkManager getLinkManager() {
    if (myLinkManager == null) {
      myLinkManager = new HtmlLinkManager();
    }
    return myLinkManager;
  }

// ---- Class loading and instantiation problems ----
  //
  // These are recorded in the logger such that they can later be
  // aggregated by the error panel. It is also written into the logger
  // rather than stashed on the ViewLoader, since the ViewLoader is reused
  // across multiple rendering operations.

  public void setResourceClass(@NotNull String resourceClass) {
    myResourceClass = resourceClass;
  }

  public void setMissingResourceClass(boolean missingResourceClass) {
    myMissingResourceClass = missingResourceClass;
  }

  public void setHasLoadedClasses(boolean hasLoadedClasses) {
    myHasLoadedClasses = hasLoadedClasses;
  }

  public boolean isMissingSize() {
    return myMissingSize;
  }

  public boolean hasLoadedClasses() {
    return myHasLoadedClasses;
  }

  public boolean isMissingResourceClass() {
    return myMissingResourceClass;
  }

  @Nullable
  public String getResourceClass() {
    return myResourceClass;
  }

  @Nullable
  public Map<String, Throwable> getClassesWithIncorrectFormat() {
    return myClassesWithIncorrectFormat;
  }

  @Nullable
  public Map<String, Throwable> getBrokenClasses() {
    return myBrokenClasses;
  }

  @Nullable
  public Set<String> getMissingClasses() {
    return myMissingClasses;
  }

  public void addMissingClass(@NotNull String className) {
    if (!className.equals(VIEW_FRAGMENT)) {
      if (myMissingClasses == null) {
        myMissingClasses = new TreeSet<String>();
      }
      myMissingClasses.add(className);
    }
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  public void addIncorrectFormatClass(@NotNull String className, @NotNull Throwable exception) {
    if (myClassesWithIncorrectFormat == null) {
      myClassesWithIncorrectFormat = new HashMap<String, Throwable>();
    }
    myClassesWithIncorrectFormat.put(className, exception);
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  public void addBrokenClass(@NotNull String className, @NotNull Throwable exception) {
    while (exception.getCause() != null && exception.getCause() != exception) {
      exception = exception.getCause();
    }

    if (myBrokenClasses == null) {
      myBrokenClasses = new HashMap<String, Throwable>();
    }
    myBrokenClasses.put(className, exception);
  }

  @Nullable
  public List<String> getMissingFragments() {
    return myMissingFragments;
  }

  /**
   * Check if this is possibly an instance of http://b.android.com/164378. If likely, this adds a message with the recommended workaround.
   */
  private boolean checkForIssue164378(@Nullable Throwable throwable) {
    if (isIssue164378(throwable)) {
        RenderProblem.Html problem = RenderProblem.create(ERROR);
        HtmlBuilder builder = problem.getHtmlBuilder();
        addHtmlForIssue164378(throwable, myModule, getLinkManager(), builder, true);
        addMessage(problem);
        return true;
    }
    return false;
  }


  static boolean isIssue164378(@Nullable Throwable throwable) {
    if (throwable instanceof NoSuchFieldError) {
      StackTraceElement[] stackTrace = throwable.getStackTrace();
      if (stackTrace.length >= 1 && stackTrace[0].getClassName().startsWith("android.support")) {
        return true;
      }
    }
    return false;
  }

  static void addHtmlForIssue164378(@NotNull Throwable throwable,
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
    builder.addLink("Please try a ", "rebuild", ".", linkManager.createCompileModuleUrl());
    builder.newline().newline();
    if (!addShowExceptionLink) {
      return;
    }
    ShowExceptionFix showExceptionFix = new ShowExceptionFix(module.getProject(), throwable);
    builder.addLink("Show Exception", linkManager.createRunnableLink(showExceptionFix));
  }

  static boolean isLoggingAllErrors() {
    return LOG_ALL;
  }
}
