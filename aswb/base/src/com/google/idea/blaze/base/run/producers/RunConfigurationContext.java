/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiQualifiedNamedElement;
import java.util.Objects;
import java.util.Optional;

/** A context used to configure a blaze run configuration, possibly asynchronously. */
public interface RunConfigurationContext {

  /** The {@link PsiElement} most relevant to this context (e.g. a method, class, file, etc.). */
  PsiElement getSourceElement();

  /** Convert a {@link #getSourceElement()} into an uniquely identifiable string. */
  default String getSourceElementString() {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ReadAction.compute(this::getSourceElementString);
    }
    PsiElement element = getSourceElement();
    if (element instanceof PsiFile) {
      return Optional.of((PsiFile) element)
          .map(PsiFile::getVirtualFile)
          .map(VirtualFile::getPath)
          .orElse(element.toString());
    }
    String path =
        Optional.of(element)
                .map(PsiElement::getContainingFile)
                .map(PsiFile::getVirtualFile)
                .map(VirtualFile::getPath)
                .orElse("")
            + '#';
    if (element instanceof PsiQualifiedNamedElement) {
      return path + ((PsiQualifiedNamedElement) element).getQualifiedName();
    } else if (element instanceof PsiNamedElement) {
      return path + ((PsiNamedElement) element).getName();
    } else {
      return path + element.toString();
    }
  }

  /** Returns true if the run configuration was successfully configured. */
  boolean setupRunConfiguration(BlazeCommandRunConfiguration config);

  /** Returns true if the run configuration matches this {@link RunConfigurationContext}. */
  boolean matchesRunConfiguration(BlazeCommandRunConfiguration config);

  static RunConfigurationContext fromKnownTarget(
      TargetExpression target, BlazeCommandName command, PsiElement sourceElement) {
    return new RunConfigurationContext() {
      @Override
      public PsiElement getSourceElement() {
        return sourceElement;
      }

      @Override
      public boolean setupRunConfiguration(BlazeCommandRunConfiguration config) {
        config.setTarget(target);
        BlazeCommandRunConfigurationCommonState handlerState =
            config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
        if (handlerState == null) {
          return false;
        }
        handlerState.getCommandState().setCommand(command);
        config.setGeneratedName();
        return true;
      }

      @Override
      public boolean matchesRunConfiguration(BlazeCommandRunConfiguration config) {
        BlazeCommandRunConfigurationCommonState handlerState =
            config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
        if (handlerState == null) {
          return false;
        }
        return Objects.equals(handlerState.getCommandState().getCommand(), command)
            && Objects.equals(config.getTargets(), ImmutableList.of(target))
            && handlerState.getTestFilterFlag() == null;
      }
    };
  }
}
