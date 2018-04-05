/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Provide Gradle specific abstraction over a {@link PsiElement}s.
 */
public interface GradleDslElement extends AnchorProvider {
  void setParsedClosureElement(@NotNull GradleDslClosure closure);

  GradleDslClosure getClosureElement();

  /**
   * Returns the name of this element at the lowest scope. I.e the text after the last dot ('.').
   */
  String getName();

  /**
   * Returns the full and qualified name of this {@link GradleDslElement}, this will be the name of this element appended to the
   * qualified name of this elements parent.
   */
  String getQualifiedName();

  /**
   * Returns the full name of the element. For elements where it makes sense, this will be the text of the
   * PsiElement in the build file.
   */
  String getFullName();

  GradleNameElement getNameElement();

  void rename(@NotNull String newName);

  GradleDslElement getParent();

  List<GradlePropertiesDslElement> getHolders();

  void addHolder(@NotNull GradlePropertiesDslElement holder);

  void setParent(@NotNull GradleDslElement parent);

  PsiElement getPsiElement();

  void setPsiElement(@Nullable PsiElement psiElement);

  boolean shouldUseAssignment();

  void setUseAssignment(boolean useAssignment);

  PropertyType getElementType();

  void setElementType(@NotNull PropertyType propertyType);

  GradleDslFile getDslFile();

  List<GradleReferenceInjection> getResolvedVariables();

  GradleDslElement getAnchor();

  /**
   * Creates the {@link PsiElement} by adding this element to the .gradle file.
   *
   * <p>It creates a new {@link PsiElement} only when {@link #getPsiElement()} return {@code null}.
   *
   * <p>Returns the final {@link PsiElement} corresponds to this element or {@code null} when failed to create the
   * {@link PsiElement}.
   */
  PsiElement create();

  PsiElement move();

  /**
   * Deletes this element and all it's children from the .gradle file.
   */
  void delete();

  void setModified(boolean modified);

  boolean isModified();

  /**
   * Returns {@code true} if this element represents a Block element (Ex. android, productFlavors, dependencies etc.),
   * {@code false} otherwise.
   */
  boolean isBlockElement();

  /**
   * Returns {@code true} if this element represents an element which is insignificant if empty.
   */
  boolean isInsignificantIfEmpty();

  Collection<GradleDslElement> getChildren();

  void resetState();

  void applyChanges();

  /**
   * Computes a list of properties and variables that are declared or assigned to in this scope.
   * Override in subclasses to return meaningful values.
   */
  List<GradleDslElement> getContainedElements(boolean includeProperties);

  /**
   * Computes a list of properties and variables that are visible from this GradleDslElement.
   */
  Map<String, GradleDslElement> getInScopeElements();

  /**
   * Helpers to quick obtain a notification instance for this elements build context.
   *
   * @param type type reference of the given notification, see {@link NotificationTypeReference} for possible values.
   * @param <T>  type of the notification
   * @return the instance of the notification in the build model.
   */
  <T extends BuildModelNotification> T notification(@NotNull NotificationTypeReference<T> type);

  void registerDependent(@NotNull GradleReferenceInjection injection);

  void unregisterDependent(@NotNull GradleReferenceInjection injection);

  void unregisterAllDependants();

  /**
   * @return all things that depend on this element.
   */
  List<GradleReferenceInjection> getDependents();

  /**
   * @return all resolved and unresolved dependencies.
   */
  List<GradleReferenceInjection> getDependencies();

  void updateDependenciesOnAddElement(@NotNull GradleDslElement newElement);

  void updateDependenciesOnReplaceElement(@NotNull GradleDslElement oldElement, @NotNull GradleDslElement newElement);

  void updateDependenciesOnRemoveElement(@NotNull GradleDslElement oldElement);

  void resolve();
}
