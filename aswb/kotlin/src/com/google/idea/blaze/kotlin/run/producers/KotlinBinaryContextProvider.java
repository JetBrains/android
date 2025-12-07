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
package com.google.idea.blaze.kotlin.run.producers;

import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.run.producers.BinaryContextProvider;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.idea.run.KotlinRunConfigurationProducer;
import org.jetbrains.kotlin.psi.KtDeclarationContainer;

/** Creates run configurations for Kotlin main methods. */
class KotlinBinaryContextProvider implements BinaryContextProvider {

  @Nullable
  @Override
  public BinaryRunContext getRunContext(ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }
    TargetIdeInfo target = getTargetIdeInfo(context);
    if (target == null) {
      return null;
    }
    return BinaryRunContext.create(location.getPsiElement(), target.toTargetInfo());
  }

  @Nullable
  private static TargetIdeInfo getTargetIdeInfo(ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }
    VirtualFile virtualFile = location.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    // #api213. Switch to EntryPointContainerFinder.find.
    KtDeclarationContainer entryPointContainer =
        KotlinRunConfigurationProducer.Companion.getEntryPointContainer(location.getPsiElement());
    if (entryPointContainer == null) {
      return null;
    }
    String startClassFqName =
        KotlinRunConfigurationProducer.Companion.getStartClassFqName(entryPointContainer);
    if (startClassFqName == null) {
      return null;
    }
    Collection<TargetIdeInfo> kotlinBinaryTargets = Collections.emptyList(); // TODO: b/466357478 - support query sync.

    // first look for a matching main_class
    TargetIdeInfo match =
        kotlinBinaryTargets.stream()
            .filter(
                target ->
                    target.getJavaIdeInfo() != null
                        && startClassFqName.equals(
                            target.getJavaIdeInfo().getJavaBinaryMainClass()))
            .findFirst()
            .orElse(null);
    if (match != null) {
      return match;
    }

    match =
        kotlinBinaryTargets.stream()
            .filter(
                target ->
                    startClassFqName.equals(target.getKey().getLabel().targetName().toString()))
            .findFirst()
            .orElse(null);
    if (match != null) {
      return match;
    }

    return Iterables.getFirst(kotlinBinaryTargets, null);
  }
}
