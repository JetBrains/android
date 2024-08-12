/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync.source;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.google.idea.blaze.java.run.BlazeJavaDebuggerRunner;
import com.google.idea.blaze.java.run.BlazeJavaTestEventsHandler;
import com.google.idea.blaze.java.sync.importer.JavaSourceFilter;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * For languages similar to Java to reuse certain parts of the Java plugin. E.g., package prefix
 * calculation.
 */
public interface JavaLikeLanguage {
  ExtensionPointName<JavaLikeLanguage> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.JavaLikeLanguage");

  static Predicate<ArtifactLocation> getSourceFileMatcher() {
    final Set<String> fileExtensions = getAllFileExtension();
    return artifactLocation ->
        fileExtensions.stream()
            .anyMatch(extension -> artifactLocation.getRelativePath().endsWith(extension));
  }

  static Set<String> getAllFileExtension() {
    return Arrays.stream(EP_NAME.getExtensions())
        .map(JavaLikeLanguage::getFileExtensions)
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());
  }

  static ImmutableSet<Kind> getAllDebuggableKinds() {
    return Arrays.stream(EP_NAME.getExtensions())
        .map(JavaLikeLanguage::getDebuggableKinds)
        .flatMap(Collection::stream)
        .collect(toImmutableSet());
  }

  static ImmutableSet<Kind> getAllHandledTestKinds() {
    return Arrays.stream(EP_NAME.getExtensions())
        .map(JavaLikeLanguage::getHandledTestKinds)
        .flatMap(Collection::stream)
        .collect(toImmutableSet());
  }

  static boolean canImportAsSource(TargetIdeInfo target) {
    return Arrays.stream(EP_NAME.getExtensions())
        .map(JavaLikeLanguage::getNonSourceKinds)
        .noneMatch(target::kindIsOneOf);
  }

  /** @return file extensions associated with this particular java-like language. */
  ImmutableSet<String> getFileExtensions();

  /** @return target {@link Kind}s to be handled by {@link BlazeJavaDebuggerRunner}. */
  ImmutableSet<Kind> getDebuggableKinds();

  /** @return test {@link Kind}s to be handled by {@link BlazeJavaTestEventsHandler}. */
  ImmutableSet<Kind> getHandledTestKinds();

  /** @return non-source {@link Kind}s to be filtered out by {@link JavaSourceFilter}. */
  ImmutableSet<Kind> getNonSourceKinds();

  /** Java is itself a Java-like language. */
  class Java implements JavaLikeLanguage {
    @Override
    public ImmutableSet<String> getFileExtensions() {
      return ImmutableSet.of(".java");
    }

    @Override
    public ImmutableSet<Kind> getDebuggableKinds() {
      return ImmutableSet.of(
          JavaBlazeRules.RuleTypes.JAVA_BINARY.getKind(),
          JavaBlazeRules.RuleTypes.JAVA_TEST.getKind());
    }

    @Override
    public ImmutableSet<Kind> getHandledTestKinds() {
      return ImmutableSet.of(
          JavaBlazeRules.RuleTypes.JAVA_TEST.getKind(),
          JavaBlazeRules.RuleTypes.GWT_TEST.getKind(),
          JavaBlazeRules.RuleTypes.JAVA_WEB_TEST.getKind());
    }

    @Override
    public ImmutableSet<Kind> getNonSourceKinds() {
      return ImmutableSet.of(
          JavaBlazeRules.RuleTypes.JAVA_WRAP_CC.getKind(),
          JavaBlazeRules.RuleTypes.JAVA_IMPORT.getKind());
    }
  }
}
