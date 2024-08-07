/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.model.primitives;

import com.google.idea.blaze.base.ideinfo.ProjectDataInterner;
import com.intellij.openapi.diagnostic.Logger;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Wrapper around a string for a blaze label ([@@?external_workspace]//package:rule).
 *
 * <p>Unlike {@link com.google.idea.blaze.common.Label} this implementation does not normalize from
 * a single @ to @@ for external workspaces. Since the expression could be serialized.
 */
@Immutable
public final class Label extends TargetExpression {
  private static final Logger logger = Logger.getInstance(Label.class);
  // still Serializable as part of ProjectViewSet
  public static final long serialVersionUID = 2L;

  /** Silently returns null if this is not a valid Label */
  @Nullable
  public static Label createIfValid(String label) {
    return validate(label) == null ? ProjectDataInterner.intern(new Label(label)) : null;
  }

  public static Label create(String label) {
    String error = validate(label);
    if (error != null) {
      throw new IllegalArgumentException(error);
    }
    return ProjectDataInterner.intern(new Label(label));
  }

  public static Label create(com.google.idea.blaze.common.Label label) {
    return create(label.toString());
  }

  public static Label create(WorkspacePath packageName, TargetName newTargetName) {
    return create(null, packageName, newTargetName);
  }

  public static Label create(
      @Nullable String externalWorkspaceName, WorkspacePath packagePath, TargetName targetName) {
    String fullLabel =
        String.format(
            "%s//%s:%s",
            externalWorkspaceName != null ? "@" + externalWorkspaceName : "",
            packagePath,
            targetName);
    return ProjectDataInterner.intern(new Label(fullLabel));
  }

  private Label(String label) {
    super(label);
  }

  /** Validate the given target label. Returns null on success or an error message otherwise. */
  @Nullable
  public static String validate(String label) {
    int colonIndex = label.indexOf(':');
    if (label.startsWith("//") && colonIndex >= 0) {
      String packageName = label.substring("//".length(), colonIndex);
      String error = validatePackagePath(packageName);
      if (error != null) {
        return error;
      }
      String ruleName = label.substring(colonIndex + 1);
      error = TargetName.validate(ruleName);
      if (error != null) {
        return error;
      }
      return null;
    }
    if (label.startsWith("@") && colonIndex >= 0) {
      // a bazel-specific label pointing to a different repository
      int slashIndex = label.indexOf("//");
      if (slashIndex >= 0) {
        return validate(label.substring(slashIndex));
      }
    }
    return "Not a valid label, no target name found: " + label;
  }

  public boolean isExternal() {
    return toString().startsWith("@");
  }

  /**
   * Returns the external workspace referenced by this label, or null if it's a main workspace
   * label.
   */
  @Nullable
  public String externalWorkspaceName() {
    String label = toString();
    if (!label.startsWith("@")) {
      return null;
    }
    int slashesIndex = label.indexOf("//");
    logger.assertTrue(slashesIndex >= 0);
    return label.substring(0, slashesIndex).replaceFirst("@@?", "");
  }

  /**
   * Extract the target name from a label. The target name follows a colon at the end of the label.
   *
   * @return the target name
   */
  public TargetName targetName() {
    String labelStr = toString();
    int colonLocation = labelStr.lastIndexOf(':');
    int targetNameStart = colonLocation + 1;
    String targetNameStr = labelStr.substring(targetNameStart);
    return TargetName.create(targetNameStr);
  }

  /**
   * Return the workspace path for the package label for the given label. For example, if the
   * package is //j/c/g/a/apps/docs:release, it returns j/c/g/a/apps/docs.
   */
  public WorkspacePath blazePackage() {
    String labelStr = toString();
    int startIndex = labelStr.indexOf("//") + "//".length();
    int colonIndex = labelStr.lastIndexOf(':');
    logger.assertTrue(colonIndex >= 0);
    return new WorkspacePath(labelStr.substring(startIndex, colonIndex));
  }

  /** A new label with the same workspace and package paths, but a different target name. */
  @Nullable
  public Label withTargetName(@Nullable String targetName) {
    if (targetName == null) {
      return null;
    }
    TargetName target = TargetName.createIfValid(targetName);
    return target != null ? Label.create(externalWorkspaceName(), blazePackage(), target) : null;
  }

  @Nullable
  public static String validatePackagePath(String path) {
    return PackagePathValidator.validatePackageName(path);
  }

  public static Label fromProto(String proto) {
    return ProjectDataInterner.intern(new Label(proto));
  }
}
