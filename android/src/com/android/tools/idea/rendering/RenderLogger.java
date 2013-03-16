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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;
import static com.intellij.lang.annotation.HighlightSeverity.WARNING;

/**
 * A {@link com.android.ide.common.rendering.api.LayoutLog} which records the problems it encounters and offers them as a
 * single summary at the end
 */
public class RenderLogger extends LayoutLog {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.RenderLogger");

  private static final String TAG_MISSING_DIMENSION = "missing.dimension";     //$NON-NLS-1$
  private static Set<String> ourIgnoredFidelityWarnings;
  private static boolean ourIgnoreAllFidelityWarnings;

  private final Module myModule;
  private final String myName;
  private Set<String> myFidelityWarningStrings;
  private boolean myHaveExceptions;
  private List<String> myTags;
  private List<Throwable> myTraces;
  private List<RenderProblem> myMessages;
  private List<RenderProblem> myFidelityWarnings;
  private Set<String> myMissingClasses;
  private Map<String, Throwable> myBrokenClasses;
  private Set<String> myClassesWithIncorrectFormat;
  private String myResourceClass;
  private boolean myMissingResourceClass;
  private boolean myHasLoadedClasses;
  private HtmlLinkManager myLinkManager;
  private boolean myMissingSize;

  /**
   * Construct a logger for the given named layout
   */
  public RenderLogger(String name, Module module) {
    myName = name;
    myModule = module;
  }

  public Module getModule() {
    return myModule;
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
    return myHaveExceptions || myFidelityWarnings != null || myMessages != null ||
           myClassesWithIncorrectFormat != null || myBrokenClasses != null || myMissingClasses != null ||
           myMissingSize;
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
  public void error(@Nullable String tag, @NotNull String message, @Nullable Object data) {
    String description = describe(message);

    LOG.error("%1$s: %2$s", myName, description);

    // Workaround: older layout libraries don't provide a tag for this error
    if (tag == null && message != null && message.startsWith("Failed to find style ")) { //$NON-NLS-1$
      tag = LayoutLog.TAG_RESOURCES_RESOLVE_THEME_ATTR;
    }
    addTag(tag);
    addMessage(RenderProblem.createPlain(ERROR, description));
  }

  @Override
  public void error(@Nullable String tag, @Nullable String message, @Nullable Throwable throwable, @Nullable Object data) {
    String description = describe(message);
    LOG.error("%1$s: %2$s", throwable, myName, description);
    if (throwable != null) {
      if (throwable instanceof ClassNotFoundException) {
        // The project callback is given a chance to resolve classes,
        // and when it fails, it will record it in its own list which
        // is displayed in a special way (with action hyperlinks etc).
        // Therefore, include these messages in the visible render log,
        // especially since the user message from a ClassNotFoundException
        // is really not helpful (it just lists the class name without
        // even mentioning that it is a class-not-found exception.)
        return;
      }

      if (description.equals(throwable.getLocalizedMessage()) || description.equals(throwable.getMessage())) {
        description = "Exception raised during rendering: " + description;
      }

      recordThrowable(throwable);
      myHaveExceptions = true;
    }

    addTag(tag);
    addMessage(RenderProblem.createPlain(ERROR, description).throwable(throwable));
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
    String description = describe(message);

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
    }

    addTag(tag);
    addMessage(RenderProblem.createPlain(WARNING, description));
  }

  @Override
  public void fidelityWarning(@Nullable String tag, @Nullable String message, @Nullable Throwable throwable, @Nullable Object data) {
    if (ourIgnoreAllFidelityWarnings || ourIgnoredFidelityWarnings != null && ourIgnoredFidelityWarnings.contains(message)) {
      return;
    }

    String description = describe(message);
    if (myFidelityWarningStrings != null && myFidelityWarningStrings.contains(description)) {
      // Exclude duplicates
      return;
    }

    LOG.warn(String.format("%1$s: %2$s", myName, description), throwable);
    if (throwable != null) {
      myHaveExceptions = true;
    }

    RenderProblem error = new RenderProblem.Deferred(ERROR, tag, description, throwable);
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

  @NotNull
  private static String describe(@Nullable String message) {
    if (message == null) {
      return "";
    }
    else {
      return message;
    }
  }

  // ---- Tags ----

  private void addTag(@Nullable String tag) {
    if (tag != null) {
      if (myTags == null) {
        myTags = new ArrayList<String>();
      }
      myTags.add(tag);
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
      for (String tag : myTags) {
        if (tag.startsWith(prefix)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Returns true if the given tag has been seen
   *
   * @param tag the tag to look for
   * @return true iff the tag was seen during the render
   */
  public boolean seenTag(@NotNull String tag) {
    if (myTags != null) {
      return myTags.contains(tag);
    }
    else {
      return false;
    }
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

  public void setResourceClass(String resourceClass) {
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

  public String getResourceClass() {
    return myResourceClass;
  }

  public Set<String> getClassesWithIncorrectFormat() {
    return myClassesWithIncorrectFormat;
  }

  public Map<String, Throwable> getBrokenClasses() {
    return myBrokenClasses;
  }

  public Set<String> getMissingClasses() {
    return myMissingClasses;
  }

  public void addMissingClass(String className) {
    if (!className.equals(VIEW_FRAGMENT)) {
      if (myMissingClasses == null) {
        myMissingClasses = new TreeSet<String>();
      }
      myMissingClasses.add(className);
    }
  }

  public void addIncorrectFormatClass(String className) {
    if (myClassesWithIncorrectFormat == null) {
      myClassesWithIncorrectFormat = new com.intellij.util.containers.HashSet<String>();
    }
    myClassesWithIncorrectFormat.add(className);
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  public void addBrokenClass(String className, Throwable exception) {
    while (exception.getCause() != null && exception.getCause() != exception) {
      exception = exception.getCause();
    }

    if (myBrokenClasses == null) {
      myBrokenClasses = new HashMap<String, Throwable>();
    }
    myBrokenClasses.put(className, exception);
  }
}
