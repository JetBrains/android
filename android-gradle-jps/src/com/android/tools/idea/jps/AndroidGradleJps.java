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
package com.android.tools.idea.jps;

import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.tools.idea.jps.model.JpsAndroidGradleModuleExtension;
import com.android.tools.idea.jps.model.JpsGradleModuleExtension;
import com.android.tools.idea.jps.model.impl.JpsAndroidGradleModuleExtensionImpl;
import com.android.tools.idea.jps.model.impl.JpsGradleModuleExtensionImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.android.model.JpsAndroidSdkType;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility methods.
 */
public final class AndroidGradleJps {
  @NonNls private static final String COMPILER_NAME = "Gradle";

  private AndroidGradleJps() {
  }

  /**
   * Returns the first Android-Gradle facet in the given chunk of IDEA modules.
   *
   * @param chunk the given chunk of IDEA modules.
   * @return the first Android-Gradle facet in the given chunk of IDEA modules, or {@code null} if none of the module contain the
   *         Android-Gradle facet.
   */
  @Nullable
  public static JpsAndroidGradleModuleExtension getFirstExtension(@NotNull ModuleChunk chunk) {
    for (JpsModule module : chunk.getModules()) {
      JpsAndroidGradleModuleExtension extension = getExtension(module);
      if (extension != null) {
        return extension;
      }
    }
    return null;
  }

  /**
   * Indicates whether any of the modules in the given IDEA project has the Android-Gradle facet.
   *
   * @param project the given IDEA project.
   * @return {@code true} if the project contains the Android-Gradle facet; {@code false} otherwise.
   */
  public static boolean hasAndroidGradleFacet(@NotNull JpsProject project) {
    for (JpsModule module : project.getModules()) {
      if (getExtension(module) != null) {
        return true;
      }
    }
    return false;
  }

  /**
   * Obtains the Android-Gradle facet from the given IDEA module.
   *
   * @param module the given IDEA module.
   * @return the Android-Gradle facet from the given IDEA module, or {@code null} if the given module does not have the facet.
   */
  @Nullable
  public static JpsAndroidGradleModuleExtension getExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(JpsAndroidGradleModuleExtensionImpl.KIND);
  }

  @Nullable
  public static JpsGradleModuleExtension getOrCreateGradleSystemExtension(@NotNull JpsModule module) {
    JpsGradleModuleExtension extension = getGradleSystemExtension(module);

    if (extension == null) {
      extension = new JpsGradleModuleExtensionImpl();
      module.getContainer().setChild(JpsGradleModuleExtensionImpl.ROLE, extension);
    }
    return extension;
  }

  @Nullable
  public static JpsGradleModuleExtension getGradleSystemExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(JpsGradleModuleExtensionImpl.ROLE);
  }

  /**
   * Returns the first 'Android SDK' in the given chunk of IDEA modules.
   *
   * @param chunk the given chunk of modules.
   * @return the first 'Android SDK' in the given chunk of IDEA modules, or {@code null} if none of the modules contain an 'Android SDK.'
   */
  @Nullable
  public static JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> getFirstAndroidSdk(@NotNull ModuleChunk chunk) {
    for (JpsModule module : chunk.getModules()) {
      JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> sdk = module.getSdk(JpsAndroidSdkType.INSTANCE);
      if (sdk != null) {
        return sdk;
      }
    }
    return null;
  }

  @NotNull
  public static CompilerMessage createCompilerMessage(@NotNull BuildMessage.Kind kind, @NotNull String text) {
    return new CompilerMessage(COMPILER_NAME, kind, text);
  }

  /**
   * Adapter method to create IDEA CompilerMessages from an Android build Message.
   *
   * If the Android build message has multiple source locations, this will create multiple CompilerMesssages with the same kind and
   * messageText, but different source positions.
   */
  @NotNull
  public static List<CompilerMessage> createCompilerMessages(@NotNull Message message) {
    final BuildMessage.Kind kind;
    switch (message.getKind()) {
      case INFO:
        kind = BuildMessage.Kind.INFO;
        break;
      case WARNING:
        kind = BuildMessage.Kind.WARNING;
        break;
      case ERROR:
        kind = BuildMessage.Kind.ERROR;
        break;
      default:
        kind = BuildMessage.Kind.PROGRESS;
    }
    List<CompilerMessage> compilerMessages = new ArrayList<CompilerMessage>();
    for (SourceFilePosition filePosition : message.getSourceFilePositions()) {
      File sourceFile = filePosition.getFile().getSourceFile();
      String sourceFilePath = sourceFile != null ? sourceFile.getAbsolutePath() : null;
      SourcePosition pos = filePosition.getPosition();
      compilerMessages.add(
        new CompilerMessage(COMPILER_NAME, kind, message.getText().trim(), sourceFilePath, pos.getStartOffset(), pos.getEndOffset(),
                            pos.getEndOffset(), pos.getEndLine(), pos.getEndColumn()));
    }
    return Collections.unmodifiableList(compilerMessages);
  }
}
