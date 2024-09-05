/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.idea.blaze.android.projectsystem;

import static com.android.tools.idea.util.DependencyManagementUtil.addDependenciesWithUiConfirmation;
import static com.android.tools.idea.util.DependencyManagementUtil.dependsOn;

import com.android.ide.common.repository.GoogleMavenArtifactId;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.naveditor.editor.AddDestinationMenuToken;
import com.intellij.openapi.module.Module;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Blaze implementation of project system tokens for the navigation editor: Add Destination Menu */
public class BazelAddDestinationMenuToken
    implements AddDestinationMenuToken<BazelProjectSystem>, BazelToken {
  @Override
  public void modifyProject(
      @NotNull BazelProjectSystem projectSystem, @NotNull AddDestinationMenuToken.Data data) {
    NlModel model = data.getSurface().getModel();
    if (model == null) {
      return;
    }
    Module module = model.getModule();
    if (dependsOn(module, GoogleMavenArtifactId.ANDROIDX_NAVIGATION_DYNAMIC_FEATURES_FRAGMENT)) {
      return;
    }
    final var unused =
        addDependenciesWithUiConfirmation(
            module,
            List.of(
                GoogleMavenArtifactId.ANDROIDX_NAVIGATION_DYNAMIC_FEATURES_FRAGMENT.getCoordinate(
                    "+")),
            true,
            false);
  }
}
