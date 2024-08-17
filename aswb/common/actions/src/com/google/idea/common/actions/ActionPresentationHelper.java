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
package com.google.idea.common.actions;

import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import javax.annotation.Nullable;

/** Helps setting the presentation enabled/visible/text states. */
public class ActionPresentationHelper {

  private final AnActionEvent event;

  private boolean enabled = true;
  private boolean visible = true;
  private boolean disableWithoutSubject;
  private boolean hideInContextMenuIfDisabled;

  private boolean hasSubject;
  private String text;
  private String subjectText;
  private boolean useTextMnemonic;

  private String description;

  /** Converts a subject to a string */
  @FunctionalInterface
  public interface SubjectToString<T> {
    String subjectToString(T subject);
  }

  private ActionPresentationHelper(AnActionEvent event) {
    this.event = event;
  }

  public static ActionPresentationHelper of(AnActionEvent event) {
    return new ActionPresentationHelper(event);
  }

  /** Disables the action if the condition is true. */
  @CanIgnoreReturnValue
  public ActionPresentationHelper disableIf(boolean disableCondition) {
    this.enabled = this.enabled && !disableCondition;
    return this;
  }

  /** Hides the action if the condition is true. If the action is hidden, it is also disabled. */
  @CanIgnoreReturnValue
  public ActionPresentationHelper hideIf(boolean hideCondition) {
    this.visible = this.visible && !hideCondition;
    return this;
  }

  @CanIgnoreReturnValue
  public ActionPresentationHelper hideInContextMenuIfDisabled() {
    this.hideInContextMenuIfDisabled = true;
    return this;
  }

  /** Disables the action if no subject has been provided. */
  @CanIgnoreReturnValue
  public ActionPresentationHelper disableWithoutSubject() {
    this.disableWithoutSubject = true;
    return this;
  }

  /** Sets the text of the presentation. */
  @CanIgnoreReturnValue
  public ActionPresentationHelper setText(String text) {
    this.text = text;
    return this;
  }

  /** Use & or _ in the presentation text as a mnemonic shortcut. */
  @CanIgnoreReturnValue
  public ActionPresentationHelper useTextMnemonic() {
    this.useTextMnemonic = true;
    return this;
  }

  /** Sets the description of the presentation. */
  @CanIgnoreReturnValue
  public ActionPresentationHelper setDescription(String description) {
    this.description = description;
    return this;
  }

  /**
   * Sets the text depending on the subject.
   *
   * @param noSubjectText Text to set if there is no subject, or if the action is disabled.
   * @param subjectText Text to set if there is a subject. If %s exists in the subject text,
   *     String.format is used with the quoted file name.
   * @param file The subject. May be null.
   */
  public ActionPresentationHelper setTextWithSubject(
      String noSubjectText, String subjectText, @Nullable VirtualFile file) {
    return setTextWithSubject(
        noSubjectText, subjectText, file, ActionPresentationHelper::quoteFileName);
  }

  /**
   * Sets the text depending on the subject.
   *
   * @param noSubjectText Text to set if there is no subject, or if the action is disabled.
   * @param subjectText Text to set if there is a subject. If %s exists in the subject text,
   *     String.format is used with the subject passed through subjectToString
   * @param subject The subject. May be null.
   * @param subjectToString Method used to convert the subject to a string.
   */
  @CanIgnoreReturnValue
  public <T> ActionPresentationHelper setTextWithSubject(
      String noSubjectText,
      String subjectText,
      @Nullable T subject,
      SubjectToString<T> subjectToString) {
    this.text = noSubjectText;
    if (subject != null) {
      this.subjectText =
          subjectText.contains("%s")
              ? String.format(subjectText, subjectToString.subjectToString(subject))
              : subjectText;
      this.hasSubject = true;
    }
    return this;
  }

  /**
   * Sets the text depending on the subjects.
   *
   * @param noSubjectText Text to set if there is no subject, or if the action is disabled.
   * @param singleSubjectText Text to set if there is a single subject. If %s exists in the subject
   *     text, String.format is used with the quoted single file name.
   * @param multipleSubjectText Text to use if there are multiple subjects.
   */
  public ActionPresentationHelper setTextWithSubjects(
      String noSubjectText,
      String singleSubjectText,
      String multipleSubjectText,
      Collection<VirtualFile> files) {
    return setTextWithSubjects(
        noSubjectText,
        singleSubjectText,
        multipleSubjectText,
        files,
        ActionPresentationHelper::quoteFileName);
  }

  /**
   * Sets the text depending on the subjects.
   *
   * @param noSubjectText Text to set if there is no subject, or if the action is disabled.
   * @param singleSubjectText Text to set if there is a single subject. If %s exists in the subject
   *     text, String.format is used with the subject passed through subjectToString.
   * @param multipleSubjectText Text to use if there are multiple subjects.
   */
  public <T> ActionPresentationHelper setTextWithSubjects(
      String noSubjectText,
      String singleSubjectText,
      String multipleSubjectText,
      Collection<T> subjects,
      SubjectToString<T> subjectToString) {
    if (subjects.size() > 1) {
      this.text = noSubjectText;
      this.subjectText = multipleSubjectText;
      this.hasSubject = true;
      return this;
    } else {
      T subject = !subjects.isEmpty() ? Iterables.getOnlyElement(subjects) : null;
      return setTextWithSubject(noSubjectText, singleSubjectText, subject, subjectToString);
    }
  }

  private static String quoteFileName(VirtualFile file) {
    return "\"" + file.getName() + "\"";
  }

  public void commit() {
    boolean enabled = this.enabled && this.visible;
    if (disableWithoutSubject) {
      enabled = enabled && hasSubject;
    }
    boolean visible = this.visible;
    if (hideInContextMenuIfDisabled && !enabled && ActionPlaces.isPopupPlace(event.getPlace())) {
      visible = false;
    }

    Presentation presentation = event.getPresentation();
    presentation.setEnabled(enabled);
    presentation.setVisible(visible);

    String text = enabled && hasSubject ? subjectText : this.text;
    if (text != null) {
      presentation.setText(text, useTextMnemonic);
    }

    if (description != null) {
      presentation.setDescription(description);
    }
  }
}
