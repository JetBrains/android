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
package com.google.idea.blaze.base.ideinfo;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

/**
 * Utility class to intern frequently duplicated objects in the project data.
 *
 * <p>The underlying interners are application-wide, not specific to a project.
 */
public final class ProjectDataInterner {
  private static final BoolExperiment internProjectData =
      new BoolExperiment("intern.project.data", true);

  private static volatile State state = useInterner() ? new Impl() : new NoOp();

  private static boolean useInterner() {
    return ApplicationManager.getApplication() == null
        || ApplicationManager.getApplication().isUnitTestMode()
        || internProjectData.getValue();
  }

  public static Label intern(Label label) {
    return state.doIntern(label);
  }

  static String intern(String string) {
    return state.doIntern(string);
  }

  static TargetKey intern(TargetKey targetKey) {
    return state.doIntern(targetKey);
  }

  static Dependency intern(Dependency dependency) {
    return state.doIntern(dependency);
  }

  static ArtifactLocation intern(ArtifactLocation artifactLocation) {
    return state.doIntern(artifactLocation);
  }

  static AndroidResFolder intern(AndroidResFolder androidResFolder) {
    return state.doIntern(androidResFolder);
  }

  public static ExecutionRootPath intern(ExecutionRootPath executionRootPath) {
    return state.doIntern(executionRootPath);
  }

  static LibraryArtifact intern(LibraryArtifact libraryArtifact) {
    return state.doIntern(libraryArtifact);
  }

  private interface State {
    Label doIntern(Label label);

    String doIntern(String string);

    TargetKey doIntern(TargetKey targetKey);

    Dependency doIntern(Dependency dependency);

    ArtifactLocation doIntern(ArtifactLocation artifactLocation);

    AndroidResFolder doIntern(AndroidResFolder androidResFolder);

    ExecutionRootPath doIntern(ExecutionRootPath executionRootPath);

    LibraryArtifact doIntern(LibraryArtifact libraryArtifact);
  }

  private static class NoOp implements State {
    @Override
    public Label doIntern(Label label) {
      return label;
    }

    @Override
    public String doIntern(String string) {
      return string;
    }

    @Override
    public TargetKey doIntern(TargetKey targetKey) {
      return targetKey;
    }

    @Override
    public Dependency doIntern(Dependency dependency) {
      return dependency;
    }

    @Override
    public ArtifactLocation doIntern(ArtifactLocation artifactLocation) {
      return artifactLocation;
    }

    @Override
    public AndroidResFolder doIntern(AndroidResFolder androidResFolder) {
      return androidResFolder;
    }

    @Override
    public ExecutionRootPath doIntern(ExecutionRootPath executionRootPath) {
      return executionRootPath;
    }

    @Override
    public LibraryArtifact doIntern(LibraryArtifact libraryArtifact) {
      return libraryArtifact;
    }
  }

  private static class Impl implements State {
    private final Interner<Label> labelInterner = Interners.newWeakInterner();
    private final Interner<String> stringInterner = Interners.newWeakInterner();
    private final Interner<TargetKey> targetKeyInterner = Interners.newWeakInterner();
    private final Interner<Dependency> dependencyInterner = Interners.newWeakInterner();
    private final Interner<ArtifactLocation> artifactLocationInterner = Interners.newWeakInterner();
    private final Interner<AndroidResFolder> androidResFolderInterner = Interners.newWeakInterner();
    private final Interner<ExecutionRootPath> executionRootPathInterner =
        Interners.newWeakInterner();
    private final Interner<LibraryArtifact> libraryArtifactInterner = Interners.newWeakInterner();

    @Override
    public Label doIntern(Label label) {
      return labelInterner.intern(label);
    }

    @Override
    public String doIntern(String string) {
      return stringInterner.intern(string);
    }

    @Override
    public TargetKey doIntern(TargetKey targetKey) {
      return targetKeyInterner.intern(targetKey);
    }

    @Override
    public Dependency doIntern(Dependency dependency) {
      return dependencyInterner.intern(dependency);
    }

    @Override
    public ArtifactLocation doIntern(ArtifactLocation artifactLocation) {
      return artifactLocationInterner.intern(artifactLocation);
    }

    @Override
    public AndroidResFolder doIntern(AndroidResFolder androidResFolder) {
      return androidResFolderInterner.intern(androidResFolder);
    }

    @Override
    public ExecutionRootPath doIntern(ExecutionRootPath executionRootPath) {
      return executionRootPathInterner.intern(executionRootPath);
    }

    @Override
    public LibraryArtifact doIntern(LibraryArtifact libraryArtifact) {
      return libraryArtifactInterner.intern(libraryArtifact);
    }
  }

  static class Updater implements SyncListener {
    @Override
    public void onSyncStart(Project project, BlazeContext context, SyncMode syncMode) {
      boolean useInterner = useInterner();
      boolean usingInterner = state instanceof Impl;
      if (useInterner != usingInterner) {
        state = useInterner ? new Impl() : new NoOp();
      }
    }
  }
}
