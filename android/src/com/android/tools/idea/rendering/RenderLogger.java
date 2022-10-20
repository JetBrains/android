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

import static com.android.SdkConstants.CONSTRUCTOR_NAME;
import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.SdkConstants.WIDGET_PKG_PREFIX;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;
import static com.intellij.lang.annotation.HighlightSeverity.WARNING;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.tools.idea.flags.StudioFlags;
import com.android.utils.HtmlBuilder;
import com.android.utils.XmlUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParserException;

/**
 * A {@link ILayoutLog} which records the problems it encounters and offers them as a
 * single summary at the end
 */
public class RenderLogger implements IRenderLogger {
  public static final RenderLogger NOP_RENDER_LOGGER = new RenderLogger(null, null, null) {
    @Override
    public void addMessage(@NotNull RenderProblem message) {}

    @Override
    public void error(@Nullable String tag,
                      @Nullable String message,
                      @Nullable Object viewCookie,
                      @Nullable Object data) {}

    @Override
    public void error(@Nullable String tag,
                      @Nullable String message,
                      @Nullable Throwable throwable,
                      @Nullable Object viewCookie,
                      @Nullable Object data) {}

    @Override
    public void warning(@Nullable String tag,
                        @NotNull String message,
                        @Nullable Object viewCookie,
                        @Nullable Object data) {}

    @Override
    public void fidelityWarning(@Nullable String tag,
                                @Nullable String message,
                                @Nullable Throwable throwable,
                                @Nullable Object viewCookie,
                                @Nullable Object data) {}

    @Override
    public void setHasLoadedClasses() {}

    @Override
    public void setMissingResourceClass() {}

    @Override
    public void setResourceClass(@NotNull String resourceClass) {}

    @Override
    public void addMissingClass(@NotNull String className) {}

    @Override
    public void addBrokenClass(@NotNull String className, @NotNull Throwable exception) {}

    @Override
    public void logAndroidFramework(int priority, String tag, @NotNull String message) {}
  };

  public static final String TAG_MISSING_DIMENSION = "missing.dimension";
  public static final String TAG_MISSING_FRAGMENT = "missing.fragment";
  public static final String TAG_STILL_BUILDING = "project.building";
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
  private static final boolean LOG_ALL = Boolean.getBoolean("adt.renderLog") ||
                                         (ApplicationManager.getApplication() != null &&
                                          ApplicationManager.getApplication().isUnitTestMode());

  /**
   * Maximum number of RenderProblems that the logger will save. After hitting this limit, no more errors will be added and an additional
   * "too many problems" RenderProblem will be added.
   */
  @VisibleForTesting
  static final int RENDER_PROBLEMS_LIMIT =
    Integer.getInteger("com.android.tools.idea.rendering.RENDER_PROBLEMS_LIMIT", 100);

  /**
   * Maximum size allowed for StackOverflowError exceptions reported by Layoutlib. The exception stacktrace will be rewritten to
   * have STACK_OVERFLOW_TRACE_LIMIT/2 elements from the top and STACK_OVERFLOW_TRACE_LIMIT/2 from the bottom.
   */
  @VisibleForTesting
  static final int STACK_OVERFLOW_TRACE_LIMIT =
    Integer.getInteger("com.android.tools.idea.rendering.STACK_OVERFLOW_TRACE_LIMIT", 100);

  private static Set<String> ourIgnoredFidelityWarnings;
  private static boolean ourIgnoreAllFidelityWarnings;
  private static boolean ourIgnoreFragments;

  private final Module myModule;
  private final String myName;
  private Set<String> myFidelityWarningStrings;
  private boolean myHaveExceptions;
  private Multiset<String> myTags;
  @GuardedBy("myMessages")
  private final List<RenderProblem> myMessages = new ArrayList<>();
  @GuardedBy("myMessages")
  private int myMessagesOverflowCounter;
  private List<RenderProblem> myFidelityWarnings;
  private Set<String> myMissingClasses;
  private Map<String, Throwable> myBrokenClasses;
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

  /**
   * Clears all the fidelity warning ignores.
   * @see #ignoreAllFidelityWarnings()
   * @see #ignoreFidelityWarning(Object)
   */
  @VisibleForTesting
  public static void resetFidelityErrorsFilters() {
    ourIgnoreAllFidelityWarnings = false;
    if (ourIgnoredFidelityWarnings != null) {
      ourIgnoredFidelityWarnings.clear();
    }
  }

  /**
   * Ignore the given render fidelity warning for the current session
   *
   * @param clientData the client data stashed on the render problem
   */
  public static void ignoreFidelityWarning(@NotNull Object clientData) {
    if (ourIgnoredFidelityWarnings == null) {
      ourIgnoredFidelityWarnings = new HashSet<>();
    }
    ourIgnoredFidelityWarnings.add((String)clientData);
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

    return message;
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

  @Nullable
  public Module getModule() {
    return myModule;
  }

  // ---- extends ILayoutLog ----

  @Nullable
  public Project getProject() {
    if (myModule != null) {
      return myModule.getProject();
    }
    return null;
  }

  private void logMessageToIdeaLog(@NotNull String message, @Nullable Throwable t) {
    String logMessage;

    if (t == null) {
      logMessage = message;
    }
    else {
      StringWriter stringWriter = new StringWriter();
      PrintWriter writer = new PrintWriter(stringWriter);
      writer.println(t.getMessage());
      t.printStackTrace(writer);
      logMessage = message + "\n" + stringWriter.toString();
    }
    boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
    try {
      LOG.debug(logMessage);
    }
    finally {
      RenderSecurityManager.exitSafeRegion(token);
    }
  }

  private void logMessageToIdeaLog(@NotNull String message) {
    logMessageToIdeaLog(message, null);
  }

  @Override
  public void addMessage(@NotNull RenderProblem message) {
    synchronized (myMessages) {
      if (myMessages.size() < RENDER_PROBLEMS_LIMIT) {
        myMessages.add(message);
      }
      else {
        myMessagesOverflowCounter++;
      }
    }

    logMessageToIdeaLog(XmlUtils.fromXmlAttributeValue(message.getHtml()));
  }

  @NotNull
  public List<RenderProblem> getMessages() {
    ImmutableList.Builder<RenderProblem> builder = ImmutableList.builder();
    int overflowCounter;
    synchronized (myMessages) {
      builder.addAll(myMessages);
      overflowCounter = myMessagesOverflowCounter;
    }

    if (overflowCounter > 0) {
      builder.add(RenderProblem.createPlain(WARNING, String.format(Locale.US,
                                                                   "Too many errors (%d more errors not displayed)", overflowCounter)));
    }

    return builder.build();
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
    boolean hasErrorMessage;
    synchronized (myMessages) {
      hasErrorMessage = myMessages.stream().anyMatch(message -> message.getSeverity().compareTo(ERROR) >= 0);
    }
    return myHaveExceptions || hasErrorMessage ||
           myBrokenClasses != null || myMissingClasses != null ||
           myMissingSize || myMissingFragments != null;
  }

  /**
   * Returns the fidelity warnings
   */
  @NotNull
  public List<RenderProblem> getFidelityWarnings() {
    return myFidelityWarnings != null ? myFidelityWarnings : Collections.emptyList();
  }

  @Override
  public void error(@Nullable String tag, @Nullable String message, @Nullable Object viewCookie, @Nullable Object data) {
    String description = describe(message, null);

    // Workaround: older layout libraries don't provide a tag for this error
    if (tag == null && message != null &&
        (message.startsWith("Failed to find style ") || message.startsWith("Unable to resolve parent style name: "))) {
      tag = ILayoutLog.TAG_RESOURCES_RESOLVE_THEME_ATTR;
    }
    addTag(tag);
    addMessage(RenderProblem.createPlain(ERROR, description).tag(tag));
  }

  /**
   * If the given {@link StackOverflowError} has a stacktrace longer then {@link RenderLogger#STACK_OVERFLOW_TRACE_LIMIT}, the stacktrace
   * will be summarized by keeping the beginning and end of it and adding an "omitted:omitted" line in the middle.
   * The resulting stacktrace will be at most {@link RenderLogger#STACK_OVERFLOW_TRACE_LIMIT} + 1 elements long.
   */
  @NotNull
  private static StackOverflowError summarizeStackOverFlowException(@NotNull StackOverflowError stackOverflowError) {
    StackTraceElement[] stackTraceElements = stackOverflowError.getStackTrace();
    int traceSize = stackTraceElements.length;
    if (traceSize < STACK_OVERFLOW_TRACE_LIMIT) {
      return stackOverflowError;
    }

    // The stack trace is too large, summarize
    int elementsToCopy = STACK_OVERFLOW_TRACE_LIMIT / 2;
    // We save STACK_OVERFLOW_TRACE_LIMIT/2 elements from the beginning and the same from the end of the old stacktrace. In the middle
    // we add a fake element saying "omitted:omitted"
    StackTraceElement[] newStackTraceElements = new StackTraceElement[elementsToCopy * 2 + 1];
    newStackTraceElements[elementsToCopy] = new StackTraceElement("omitted", "omitted", "omitted", -1);
    System.arraycopy(stackTraceElements, 0, newStackTraceElements, 0, elementsToCopy);
    System
      .arraycopy(stackTraceElements, stackTraceElements.length - elementsToCopy, newStackTraceElements, elementsToCopy + 1, elementsToCopy);
    stackOverflowError.setStackTrace(newStackTraceElements);

    return stackOverflowError;
  }

  @Override
  public void error(@Nullable String tag,
                    @Nullable String message,
                    @Nullable Throwable throwable,
                    @Nullable Object viewCookie,
                    @Nullable Object data) {
    String description = describe(message, throwable);
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

      if (isIssue164378(throwable)) {
        return;
      }

      if ("Unable to find the layout for Action Bar.".equals(description)) {
        description += "\nConsider updating to a more recent version of appcompat, or switch the rendering library in the IDE " +
                       "down to API 21";
      }

      if (description.isEmpty()) {
        description = "Exception raised during rendering";
      }
      else if (description.equals(throwable.getLocalizedMessage()) || description.equals(throwable.getMessage())) {
        description = "Exception raised during rendering: " + description;
      }
      else if (message != null && message.startsWith("Failed to configure parser for ") && message.endsWith(DOT_PNG)) {
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
            String basePath = getProject() != null && getProject().getBasePath() != null ?
                              FileUtil.toSystemIndependentName(getProject().getBasePath()) : null;
            if (basePath != null && path.startsWith(basePath)) {
              path = path.substring(basePath.length());
              path = StringUtil.trimStart(path, File.separator);
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
          }
          else if (frame.getClassName().startsWith("com.android.tools.")) {
            break;
          }
        }
      }
      else if (message != null && message.startsWith("Failed to parse file ")
               && throwable instanceof XmlPullParserException) {
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
      else if (throwable instanceof StackOverflowError) {
        // StackOverflowError exceptions can have very large stack traces, limit the size when needed
        throwable = summarizeStackOverFlowException((StackOverflowError)throwable);
      }

      myHaveExceptions = true;
    }

    addTag(tag);
    if (getProject() == null) {
      addMessage(RenderProblem.createPlain(ERROR, description).tag(tag).throwable(throwable));
    }
    else {
      addMessage(RenderProblem.createPlain(ERROR, description, getProject(), getLinkManager(), throwable).tag(tag));
    }
  }

  // ---- Tags ----

  @Override
  public void warning(@Nullable String tag, @NotNull String message, @Nullable Object viewCookie, @Nullable Object data) {
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
    }
    else if (TAG_MISSING_FRAGMENT.equals(tag)) {
      if (!ourIgnoreFragments) {
        if (myMissingFragments == null) {
          myMissingFragments = new ArrayList<>();
        }
        String name = data instanceof String ? (String)data : null;
        myMissingFragments.add(name);
      }
      return;
    }
    else if (TAG_THREAD_CREATION.equals(tag)) {
      addTag(tag);
      RenderProblem problem = RenderProblem.createPlain(WARNING, description).tag(tag);
      if (data instanceof Throwable) {
        problem.throwable((Throwable)data);
      }
      addMessage(problem);
      return;
    }

    addTag(tag);
    addMessage(RenderProblem.createPlain(WARNING, description).tag(tag));
  }

  @Override
  public void fidelityWarning(@Nullable String tag,
                              @Nullable String message,
                              @Nullable Throwable throwable,
                              @Nullable Object viewCookie,
                              @Nullable Object data) {
    if (ourIgnoreAllFidelityWarnings || ourIgnoredFidelityWarnings != null && ourIgnoredFidelityWarnings.contains(message)) {
      return;
    }

    String description = describe(message, throwable);
    if (myFidelityWarningStrings != null && myFidelityWarningStrings.contains(description)) {
      // Exclude duplicates
      return;
    }

    if (throwable != null) {
      myHaveExceptions = true;
    }

    RenderProblem error = RenderProblem.createDeferred(ERROR, tag, description, throwable);
    error.setClientData(description);
    if (myFidelityWarnings == null) {
      myFidelityWarnings = new ArrayList<>();
      myFidelityWarningStrings = Sets.newHashSet();
    }

    myFidelityWarnings.add(error);
    assert myFidelityWarningStrings != null;
    myFidelityWarningStrings.add(description);
    addTag(tag);
  }

  private void addTag(@Nullable String tag) {
    if (tag == null) {
      return;
    }

    if (myTags == null) {
      myTags = HashMultiset.create();
    }

    myTags.add(tag);
  }

  // ---- Class loading and instantiation problems ----
  //
  // These are recorded in the logger such that they can later be
  // aggregated by the error panel. It is also written into the logger
  // rather than stashed on the ViewLoader, since the ViewLoader is reused
  // across multiple rendering operations.

  /**
   * Returns true if the given tag prefix has been seen
   *
   * @param prefix the tag prefix to look for
   * @return true iff any tags with the given prefix was seen during the render
   */
  public boolean seenTagPrefix(@NotNull String prefix) {
    if (myTags == null) {
      return false;
    }

    return myTags.stream().anyMatch(s -> s.startsWith(prefix));
  }

  @Override
  @NotNull
  public HtmlLinkManager getLinkManager() {
    if (myLinkManager == null) {
      myLinkManager = new HtmlLinkManager();
    }
    return myLinkManager;
  }

  @Override
  public void setHasLoadedClasses() {
    myHasLoadedClasses = true;
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

  @Override
  public void setMissingResourceClass() {
    myMissingResourceClass = true;
  }

  @Nullable
  public String getResourceClass() {
    return myResourceClass;
  }

  @Override
  public void setResourceClass(@NotNull String resourceClass) {
    myResourceClass = resourceClass;
  }

  /**
   * Returns all the logged broken classes during rendering. If any of the {@link Throwable}s stack traces is
   * longer than 100 elements, it will be summarized by only keeping the first 50 and last 50 elements and one element in the
   * middle to indicate the exception stack trace has been summarized.
   */
  @NotNull
  public Map<String, Throwable> getBrokenClasses() {
    return myBrokenClasses != null ? myBrokenClasses : Collections.emptyMap();
  }

  @NotNull
  public Set<String> getMissingClasses() {
    return myMissingClasses != null ? myMissingClasses : Collections.emptySet();
  }

  @Override
  public void addMissingClass(@NotNull String className) {
    if (!className.equals(VIEW_FRAGMENT)) {
      if (myMissingClasses == null) {
        myMissingClasses = new TreeSet<>();
      }
      myMissingClasses.add(className);

      logMessageToIdeaLog("Class not found " + className);
    }
  }

  @Override
  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  public void addBrokenClass(@NotNull String className, @NotNull Throwable exception) {
    while (exception.getCause() != null && exception.getCause() != exception) {
      exception = exception.getCause();
    }

    if (exception instanceof StackOverflowError) {
      // StackOverflowError exceptions can have very large stack traces, limit the size when needed
      exception = summarizeStackOverFlowException((StackOverflowError)exception);
    }

    if (myBrokenClasses == null) {
      myBrokenClasses = new HashMap<>();
    }

    myBrokenClasses.put(className, exception);
    logMessageToIdeaLog("Broken class " + className, exception);
  }

  @Nullable
  public List<String> getMissingFragments() {
    return myMissingFragments;
  }

  /**
   * Android framework log priority levels.
   * They are defined in system/core/base/include/android-base/logging.h in the Android Framework code.
   */
  private static final int ANDROID_LOG_VERBOSE = 0;
  private static final int ANDROID_LOG_DEBUG = 1;
  private static final int ANDROID_LOG_INFO = 2;
  private static final int ANDROID_LOG_WARN = 3;
  private static final int ANDROID_LOG_ERROR = 4;
  private static final int ANDROID_LOG_FATAL_WITHOUT_ABORT = 5;
  private static final int ANDROID_LOG_FATAL = 6;

  @Override
  public void logAndroidFramework(int priority, String tag, @NotNull String message) {
    if (StudioFlags.NELE_LOG_ANDROID_FRAMEWORK.get()) {
      boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
      try {
        String fullMessage = tag + ": " + message;
        switch (priority) {
          case ANDROID_LOG_VERBOSE:
          case ANDROID_LOG_DEBUG:
            LOG.debug(fullMessage);
            break;
          case ANDROID_LOG_INFO:
            LOG.info(fullMessage);
            break;
          case ANDROID_LOG_WARN:
          case ANDROID_LOG_ERROR:
            LOG.warn(fullMessage);
            break;
          case ANDROID_LOG_FATAL_WITHOUT_ABORT:
          case ANDROID_LOG_FATAL:
            LOG.error(fullMessage);
            break;
        }
      }
      finally {
        RenderSecurityManager.exitSafeRegion(token);
      }
    }
  }
}
