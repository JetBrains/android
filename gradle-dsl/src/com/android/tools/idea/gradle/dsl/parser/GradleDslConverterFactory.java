// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.dsl.parser;


import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;

/**
 * Extension point to provide parser/writer pair for Gradle build files (e.g. for different languages).
 *
 * This parser/writer factory will be used to manipulate build file to provide content-related features,
 * e.g. {@link com.intellij.externalSystem.ExternalDependencyModificator}. It does not affect code navigation/highlighting
 */
@ApiStatus.Internal
public interface GradleDslConverterFactory {
  ExtensionPointName<GradleDslConverterFactory> EXTENSION_POINT_NAME = ExtensionPointName.create("org.jetbrains.idea.gradle.dsl.parserFactory");

  boolean canConvert(PsiFile psiFile);

  GradleDslWriter createWriter();

  GradleDslParser createParser(PsiFile psiFile, GradleDslFile gradleDslFile);
}
