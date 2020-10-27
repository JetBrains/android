// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import com.android.tools.idea.AndroidStartupActivity;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * A Project component that initializes other Android services when a project becomes an Android project.
 *
 * See {@see AndroidStartupActivity} extension point to register initialization procedures.
 */
// TODO(b/150674051): Re-implemented not as ProjectComponent which is deprecated.
public class AndroidProjectComponent implements ProjectComponent, Disposable {
  private final Project myProject;

  protected AndroidProjectComponent(Project project) {
    myProject = project;
  }

  @Override
  public void projectOpened() {

    if (!ApplicationManager.getApplication().isUnitTestMode() &&
        !ApplicationManager.getApplication().isHeadlessEnvironment()) {

      if (ProjectFacetManager.getInstance(myProject).hasFacets(AndroidFacet.ID)) {
        runAndroidStartupActivities();
      }
      else {
        final MessageBusConnection connection = myProject.getMessageBus().connect();
        // TODO(b/150626704): Review thread safety and add appropriate annotations to `runActivity` methods
        connection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerAdapter() {
          @Override
          public void facetAdded(@NotNull Facet facet) {
            if (facet instanceof AndroidFacet) {
              runAndroidStartupActivities();
              connection.disconnect();
            }
          }
        });
      }
    }
  }

  private void runAndroidStartupActivities() {
    for (AndroidStartupActivity activity : AndroidStartupActivity.STARTUP_ACTIVITY.getExtensionList()) {
      activity.runActivity(myProject, this);
    }
  }

  @Override
  public void dispose() {

  }
}
