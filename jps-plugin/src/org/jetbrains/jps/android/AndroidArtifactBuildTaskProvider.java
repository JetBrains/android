// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.android;

import org.jetbrains.android.compiler.artifact.AndroidArtifactSigningMode;
import org.jetbrains.android.util.AndroidBuildCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.AndroidApplicationArtifactType;
import org.jetbrains.jps.android.model.JpsAndroidApplicationArtifactProperties;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.artifacts.ArtifactBuildTaskProvider;
import org.jetbrains.jps.incremental.BuildTask;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AndroidArtifactBuildTaskProvider extends ArtifactBuildTaskProvider {
  @NonNls private static final String BUILDER_NAME = "Android Artifact Processor";

  @NotNull
  @Override
  public List<? extends BuildTask> createArtifactBuildTasks(@NotNull JpsArtifact artifact,
                                                            @NotNull ArtifactBuildPhase buildPhase) {
    if (buildPhase != ArtifactBuildPhase.FINISHING_BUILD) {
      return Collections.emptyList();
    }

    if (!(artifact.getArtifactType() instanceof AndroidApplicationArtifactType)) {
      return Collections.emptyList();
    }
    final JpsElement props = artifact.getProperties();

    if (!(props instanceof JpsAndroidApplicationArtifactProperties)) {
      return Collections.emptyList();
    }

    final JpsAndroidApplicationArtifactProperties androidProps = (JpsAndroidApplicationArtifactProperties)props;

    if (!(artifact.getArtifactType() instanceof AndroidApplicationArtifactType)) {
      return Collections.emptyList();
    }
    final AndroidArtifactSigningMode signingMode = androidProps.getSigningMode();

    if (signingMode != AndroidArtifactSigningMode.RELEASE_SIGNED &&
        signingMode != AndroidArtifactSigningMode.DEBUG_WITH_CUSTOM_CERTIFICATE) {
      return Collections.emptyList();
    }
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getPackagedFacet(artifact);

    return extension != null
           ? Collections.singletonList(new MyTask(artifact, extension, androidProps))
           : Collections.emptyList();
  }

  private static class MyTask extends BuildTask {
    private final JpsArtifact myArtifact;
    private final JpsAndroidModuleExtension myExtension;
    private final JpsAndroidApplicationArtifactProperties myProps;

    private MyTask(JpsArtifact artifact,
                   JpsAndroidModuleExtension extension,
                   JpsAndroidApplicationArtifactProperties props) {
      myArtifact = artifact;
      myExtension = extension;
      myProps = props;
    }

    @Override
    public void build(CompileContext context) {
      final String artifactName = myArtifact.getName();
      final String entryName = "Artifact '" + artifactName + "'";
      final String messagePrefix = "[" + entryName + "] ";

      final JpsModule module = myExtension.getModule();
      final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);

      if (platform == null) {
        return;
      }
      final String sdkLocation = platform.getSdk().getHomePath();
      final String artifactFilePath = myArtifact.getOutputFilePath();

      final String keyStorePath = myProps.getKeyStoreUrl() != null
                                  ? JpsPathUtil.urlToPath(myProps.getKeyStoreUrl())
                                  : "";
      final String keyStorePassword = myProps.getKeyStorePassword();
      final String plainKeyStorePassword = keyStorePassword != null && !keyStorePassword.isEmpty()
                                           ? new String(Base64.getDecoder().decode(keyStorePassword), StandardCharsets.UTF_8) : null;

      final String keyPassword = myProps.getKeyPassword();
      final String plainKeyPassword = keyPassword != null && !keyPassword.isEmpty()
                                      ? new String(Base64.getDecoder().decode(keyPassword), StandardCharsets.UTF_8) : null;
      try {
        final Map<AndroidCompilerMessageKind,List<String>> messages =
          AndroidBuildCommonUtils.buildArtifact(artifactName, messagePrefix, sdkLocation, platform.getTarget(), artifactFilePath,
                                                keyStorePath, myProps.getKeyAlias(), plainKeyStorePassword, plainKeyPassword);
        AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME, entryName);
      }
      catch (GeneralSecurityException e) {
        AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
      }
      catch (IOException e) {
        AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
      }
    }
  }
}
