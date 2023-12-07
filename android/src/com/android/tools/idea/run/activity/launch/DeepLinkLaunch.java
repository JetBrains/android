/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.activity.launch;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;
import static com.android.tools.idea.instantapp.InstantApps.findFeatureModules;
import static com.android.tools.idea.run.configuration.execution.ExecutionUtils.printShellCommand;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tools.deployer.model.App;
import com.android.tools.idea.instantapp.InstantAppUrlFinder;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.configuration.AndroidBackgroundTaskReceiver;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class DeepLinkLaunch extends LaunchOption<DeepLinkLaunch.State> {
  public static final DeepLinkLaunch INSTANCE = new DeepLinkLaunch();

  @NotNull
  @Override
  public String getId() {
    return AndroidRunConfiguration.LAUNCH_DEEP_LINK;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "URL";
  }

  @NotNull
  @Override
  public State createState() {
    return new State();
  }

  @NotNull
  @Override
  public LaunchOptionConfigurable<State> createConfigurable(@NotNull Project project, @NotNull LaunchOptionConfigurableContext context) {
    return new DeepLinkConfigurable(project, context);
  }

  public static final class State extends LaunchOptionState {
    public String DEEP_LINK = "";

    @Override
    protected boolean doLaunch(@NotNull IDevice device,
                            @NotNull App app,
                            @NotNull ApkProvider apkProvider, boolean isDebug, @NotNull String extraFlags,
                            @NotNull ConsoleView console) throws ExecutionException {
      IShellOutputReceiver receiver = new AndroidBackgroundTaskReceiver(console);
      String quotedLink = "'" + DEEP_LINK.replace("'", "'\\''") + "'";
      String command = "am start" +
                       " -a android.intent.action.VIEW" +
                       " -c android.intent.category.BROWSABLE" +
                       " -d " + quotedLink + (extraFlags.isEmpty() ? "" : " " + extraFlags);
      printShellCommand(console, command);
      try {
        device.executeShellCommand(command, receiver, 15, TimeUnit.SECONDS);
      }
      catch (Exception e) {
        Logger logger = Logger.getInstance(DeepLinkLaunch.class);
        logger.warn("Unexpected exception while executing shell command: " + command);
        logger.warn(e);
        throw new ExecutionException("Unexpected error while executing: " + command);
      }
      return true;
    }

    @NotNull
    @Override
    public List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
      boolean isInstantApp = facet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP;

      if ((DEEP_LINK == null || DEEP_LINK.isEmpty())) {
        if (isInstantApp) {
          // The new AIA SDK library supports launching instant apps without a URL
          return ImmutableList.of();
        }
        else {
          return ImmutableList.of(ValidationError.warning("URL not specified"));
        }
      }

      if (isInstantApp) {
        boolean matched = false;
        List<Module> featureModules = findFeatureModules(facet);
        for (Module featureModule : featureModules) {
          if (new InstantAppUrlFinder(featureModule).matchesUrl(DEEP_LINK)) {
            matched = true;
            break;
          }
        }
        if (!matched) {
          return ImmutableList.of(ValidationError.warning("URL \"" + DEEP_LINK + "\" not defined in the manifest."));
        }
      }
      return ImmutableList.of();
    }

    @NotNull
    @Override
    public String getId() {
      return "LAUNCH_DEEP_LINK";
    }
  }
}

