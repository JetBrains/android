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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.uipreview.FixableIssueMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A {@link com.android.ide.common.rendering.api.LayoutLog} which records the problems it encounters and offers them as a
 * single summary at the end
 */
public class RenderLogger extends LayoutLog {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.RenderLogger");

  private static final String TAG_MISSING_DIMENSION = "missing.dimension";     //$NON-NLS-1$

  private final Module myModule;
  private final String myName;
  private List<String> myFidelityWarnings;
  private List<String> myWarnings;
  private List<String> myErrors;
  private boolean myHaveExceptions;
  private List<String> myTags;
  private List<Throwable> myTraces;
  private List<FixableIssueMessage> myFixableErrors;
  private List<FixableIssueMessage> myFixableWarnings;

  private static Set<String> ourIgnoredFidelityWarnings;

  /**
   * Construct a logger for the given named layout
   */
  public RenderLogger(String name, Module module) {
    myName = name;
    myModule = module;
  }

  @Nullable
  public List<FixableIssueMessage> getErrorMessages() {
    return myFixableErrors;
  }

  public void addErrorMessage(@NotNull FixableIssueMessage message) {
    if (myFixableErrors == null) {
      myFixableErrors = Lists.newArrayList();
    }
    myFixableErrors.add(message);
  }

  @Nullable
  public List<FixableIssueMessage> getWarningMessages() {
    return myFixableWarnings;
  }

  public void addWarningMessage(@NotNull FixableIssueMessage message) {
    if (myFixableWarnings == null) {
      myFixableWarnings = Lists.newArrayList();
    }
    myFixableWarnings.add(message);
  }

  /**
   * Are there any logged errors or warnings during the render?
   *
   * @return true if there were problems during the render
   */
  public boolean hasProblems() {
    return hasErrors() || myFidelityWarnings != null || myWarnings != null || myFixableWarnings != null;
  }

  /**
   * aRe there any logged errors?
   *
   * @return true if there were errors during rendering
   œ*/
  public boolean hasErrors() {
    return myErrors != null || myFixableErrors != null || myHaveExceptions;
  }

  /**
   * Returns the first exception encountered during rendering, or null if none
   *
   * @return the first exception encountered during rendering, or null if none
   */
  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  @Nullable
  public Throwable getFirstTrace() {
    return myTraces != null && !myTraces.isEmpty() ? myTraces.get(0) : null;
  }

  /**
   * Returns a list of traces encountered during rendering, or null if none
   *
   * @return a list of traces encountered during rendering, or null if none
   * @todo Rename to getTraces!
   */
  @NotNull
  public List<Throwable> getTraces() {
    return myTraces != null ? myTraces : Collections.<Throwable>emptyList();
  }

  /**
   * Returns a (possibly multi-line) description of all the problems
   *
   * @param includeFidelityWarnings if true, include fidelity warnings in the problem
   *                                summary
   * @return a string describing the rendering problems
   */
  @NotNull
  public String getProblems(boolean includeFidelityWarnings) {
    StringBuilder sb = new StringBuilder();

    if (myErrors != null) {
      for (String error : myErrors) {
        sb.append(error).append('\n');
      }
    }

    if (myWarnings != null) {
      for (String warning : myWarnings) {
        sb.append(warning).append('\n');
      }
    }

    if (includeFidelityWarnings && myFidelityWarnings != null) {
      sb.append("The graphics preview in the layout editor may not be accurate:\n");
      for (String warning : myFidelityWarnings) {
        sb.append("* ");
        sb.append(warning).append('\n');
      }
    }

    if (myHaveExceptions) {
      sb.append("Exception details are logged in Window > Show View > Error Log");
    }

    return sb.toString();
  }

  /**
   * Returns the fidelity warnings
   *
   * @return the fidelity warnings
   */
  @Nullable
  public List<String> getFidelityWarnings() {
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

    addError(tag, description);
    addErrorMessage(new FixableIssueMessage(message));
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

    addError(tag, description);
    if (throwable != null) {
      addErrorMessage(FixableIssueMessage.createExceptionIssue(myModule.getProject(), description, throwable));
    } else {
      addErrorMessage(new FixableIssueMessage(description));
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
    String description = describe(message);

    boolean log = true;
    if (TAG_RESOURCES_FORMAT.equals(tag)) {
      if (description.equals("You must supply a layout_width attribute.")       //$NON-NLS-1$
          || description.equals("You must supply a layout_height attribute.")) {//$NON-NLS-1$
        tag = TAG_MISSING_DIMENSION;
        log = false;
      }
    }

    if (log) {
      LOG.warn(String.format("%1$s: %2$s", myName, description));
    }

    addWarning(tag, description);
    addErrorMessage(new FixableIssueMessage(message));
  }

  @Override
  public void fidelityWarning(@Nullable String tag, @Nullable String message, @Nullable Throwable throwable, @Nullable Object data) {
    if (ourIgnoredFidelityWarnings != null && ourIgnoredFidelityWarnings.contains(message)) {
      return;
    }

    String description = describe(message);
    LOG.warn(String.format("%1$s: %2$s", myName, description), throwable);
    if (throwable != null) {
      myHaveExceptions = true;
    }

    addFidelityWarning(tag, description);
    // TODO: Add fixable message for clearing the error
    if (throwable != null) {
      addErrorMessage(FixableIssueMessage.createExceptionIssue(myModule.getProject(), description, throwable));
    } else {
      addErrorMessage(new FixableIssueMessage(description));
    }
  }

  /**
   * Ignore the given render fidelity warning for the current session
   *
   * @param message the message to be ignored for this session
   */
  public static void ignoreFidelityWarning(@NotNull String message) {
    if (ourIgnoredFidelityWarnings == null) {
      ourIgnoredFidelityWarnings = new HashSet<String>();
    }
    ourIgnoredFidelityWarnings.add(message);
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

  private void addWarning(@Nullable String tag, @NotNull String description) {
    if (myWarnings == null) {
      myWarnings = new ArrayList<String>();
    }
    else if (myWarnings.contains(description)) {
      // Avoid duplicates
      return;
    }
    myWarnings.add(description);
    addTag(tag);
  }

  private void addError(@Nullable String tag, @NotNull String description) {
    if (myErrors == null) {
      myErrors = new ArrayList<String>();
    }
    else if (myErrors.contains(description)) {
      // Avoid duplicates
      return;
    }
    myErrors.add(description);
    addTag(tag);
  }

  private void addFidelityWarning(@Nullable String tag, @NotNull String description) {
    if (myFidelityWarnings == null) {
      myFidelityWarnings = new ArrayList<String>();
    }
    else if (myFidelityWarnings.contains(description)) {
      // Avoid duplicates
      return;
    }
    myFidelityWarnings.add(description);
    addTag(tag);
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
}
