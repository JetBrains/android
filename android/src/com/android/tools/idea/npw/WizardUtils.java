/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.wizard.WizardConstants;
import com.google.common.base.CharMatcher;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Locale;

/**
 * Static utility methods used by the New Project/New Module wizards
 *
 * TODO: Post wizard migration: Rename this to NpwUtils(?)
 */
public class WizardUtils {
  private static final CharMatcher ILLEGAL_CHARACTER_MATCHER = CharMatcher.anyOf(WizardConstants.INVALID_FILENAME_CHARS);
  private static final int WINDOWS_PATH_LENGTH_LIMIT = 100;

  /**
   * Remove spaces, switch to lower case, and remove any invalid characters. If the resulting name
   * conflicts with an existing module, append a number to the end to make a unique name.
   */
  @NotNull
  public static String computeModuleName(@NotNull String appName, @Nullable Project project) {
    String moduleName = appName.toLowerCase(Locale.US).replaceAll(WizardConstants.INVALID_FILENAME_CHARS, "");
    moduleName = moduleName.replaceAll("\\s", "");

    if (!isUniqueModuleName(moduleName, project)) {
      int i = 2;
      while (!isUniqueModuleName(moduleName + Integer.toString(i), project)) {
        i++;
      }
      moduleName += Integer.toString(i);
    }
    return moduleName;
  }

  /**
   * @deprecated Use {@link com.android.tools.idea.ui.wizard.WizardUtils#getProjectLocationParent()} instead.
   * TODO: Post wizard migration: delete
   */
  @NotNull
  public static File getProjectLocationParent() {
    String parent = RecentProjectsManager.getInstance().getLastProjectCreationLocation();

    if (parent == null) {
      String child = ApplicationNamesInfo.getInstance().getFullProductName().replace(" ", "") + "Projects";
      return new File(SystemProperties.getUserHome(), child);
    }
    else {
      return new File(parent.replace('/', File.separatorChar));
    }
  }

  /**
   * @return true if the given module name is unique inside the given project. Returns true if the given
   * project is null.
   */
  private static boolean isUniqueModuleName(@NotNull String moduleName, @Nullable Project project) {
    if (project == null) {
      return true;
    }
    // Check our modules
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module m : moduleManager.getModules()) {
      if (m.getName().equalsIgnoreCase(moduleName)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Lists the files of the given directory and returns them as an array which
   * is never null. This simplifies processing file listings from for each
   * loops since {@link File#listFiles} can return null. This method simply
   * wraps it and makes sure it returns an empty array instead if necessary.
   *
   * @param dir the directory to list
   * @return the children, or empty if it has no children, is not a directory,
   * etc.
   *
   * @deprecated
   * TODO: Post wizard migration: delete
   */
  @NotNull
  public static File[] listFiles(@Nullable File dir) {
    if (dir != null) {
      File[] files = dir.listFiles();
      if (files != null) {
        return files;
      }
    }
    return ArrayUtil.EMPTY_FILE_ARRAY;
  }

  /**
   * A Validation Result for Wizard Validations, contains a status and a message
   *
   * @deprecated Use {@link PathValidator} instead.
   * TODO: Post wizard migration: delete
   */
  public static class ValidationResult {
    public enum Status {
      OK, WARN, ERROR
    }

    public enum Message {
      NO_LOCATION_SPECIFIED("Please specify a %1$s"),
      BAD_SLASHES("Your %1$s contains incorrect slashes ('\\' vs '/')"),
      ILLEGAL_CHARACTER("Illegal character in %1$s path: '%2$c' in filename %3s"),
      ILLEGAL_FILENAME("Illegal filename in %1$s path: %2$s"),
      WHITESPACE("%1$s should not contain whitespace, as this can cause problems with the NDK tools."),
      NON_ASCII_CHARS_WARNING("Your %1$s contains non-ASCII characters, which can cause problems. Proceed with caution."),
      NON_ASCII_CHARS_ERROR("Your %1$s contains non-ASCII characters."),
      PATH_NOT_WRITABLE("The path '%2$s' is not writable. Please choose a new location."),
      PROJECT_LOC_IS_FILE("There must not already be a file at the %1$s."),
      NON_EMPTY_DIR("A non-empty directory already exists at the specified %1$s. Existing files may be overwritten. Proceed with caution."),
      PROJECT_IS_FILE_SYSTEM_ROOT("The %1$s can not be at the filesystem root"),
      PARENT_NOT_DIR("The %1$s's parent directory must be a directory, not a plain file"),
      INSIDE_ANDROID_STUDIO("The %1$s is inside %2$s install location"),
      PATH_TOO_LONG("The %1$s is too long");

      private final String myText;

      Message(final String text) {
        myText = text;
      }

      @Override
      public String toString() {
        return myText;
      }
    }

    public static final ValidationResult OK = new ValidationResult(Status.OK, null, "any");

    private final Status myStatus;
    private final Message myMessage;
    private final Object[] myMessageParams;

    private ValidationResult(@NotNull Status status, @Nullable Message message, @NotNull String field, Object... messageParams) {
      myStatus = status;
      myMessage = message;
      myMessageParams = ArrayUtil.prepend(field, messageParams);
    }

    public static ValidationResult warn(@NotNull Message message, String field, Object... params) {
      return new ValidationResult(Status.WARN, message, field, params);
    }

    public static ValidationResult error(@NotNull Message message, String field, Object... params) {
      return new ValidationResult(Status.ERROR, message, field, params);
    }

    @NotNull
    private static ValidationResult pathNotWritable(@NotNull WritableCheckMode mode, @NotNull String field, @NotNull File file) {
      switch (mode) {
        case NOT_WRITABLE_IS_ERROR:
          return error(Message.PATH_NOT_WRITABLE, field, file.getPath());
        case NOT_WRITABLE_IS_WARNING:
          return warn(Message.PATH_NOT_WRITABLE, field, file.getPath());
        default:
          throw new IllegalArgumentException(mode.toString());
      }
    }

    public String getFormattedMessage() {
      if (myMessage == null) {
        throw new IllegalStateException("Null message, are you trying to get the message of an OK?");
      }
      return String.format(myMessage.toString(), myMessageParams);
    }

    @NotNull
    public Status getStatus() {
      return myStatus;
    }

    public boolean isError() {
      return myStatus.equals(Status.ERROR);
    }

    public boolean isOk() {
      return myStatus.equals(Status.OK);
    }
  }

  /**
   * Will return {@link WizardUtils.ValidationResult#OK} if projectLocation is valid
   * or {@link WizardUtils.ValidationResult} with error if not.
   */
  @NotNull
  public static ValidationResult validateLocation(@Nullable String projectLocation) {
    return validateLocation(projectLocation, "project location", true);
  }

  @NotNull
  public static ValidationResult validateLocation(@Nullable String projectLocation, @NotNull String fieldName, boolean checkEmpty) {
    return validateLocation(projectLocation, fieldName, checkEmpty, WritableCheckMode.NOT_WRITABLE_IS_ERROR);
  }

  public enum WritableCheckMode {DO_NOT_CHECK, NOT_WRITABLE_IS_ERROR, NOT_WRITABLE_IS_WARNING}

  @NotNull
  public static ValidationResult validateLocation(@Nullable String projectLocation,
                                                  @NotNull String fieldName,
                                                  boolean checkEmpty,
                                                  @NotNull WritableCheckMode writableCheckMode) {
    ValidationResult warningResult = null;
    if (projectLocation == null || projectLocation.isEmpty()) {
      return ValidationResult.error(ValidationResult.Message.NO_LOCATION_SPECIFIED, fieldName);
    }
    // Check the separators
    if ((File.separatorChar == '/' && projectLocation.contains("\\")) || (File.separatorChar == '\\' && projectLocation.contains("/"))) {
      return ValidationResult.error(ValidationResult.Message.BAD_SLASHES, fieldName);
    }
    // Check the individual components for not allowed characters.
    File testFile = new File(projectLocation);
    while (testFile != null) {
      String filename = testFile.getName();
      if (ILLEGAL_CHARACTER_MATCHER.matchesAnyOf(filename)) {
        char illegalChar = filename.charAt(ILLEGAL_CHARACTER_MATCHER.indexIn(filename));
        return ValidationResult.error(ValidationResult.Message.ILLEGAL_CHARACTER, fieldName, illegalChar, filename);
      }
      if (WizardConstants.INVALID_WINDOWS_FILENAMES.contains(filename.toLowerCase(Locale.US))) {
        return ValidationResult.error(ValidationResult.Message.ILLEGAL_FILENAME, fieldName, filename);
      }
      if (CharMatcher.WHITESPACE.matchesAnyOf(filename)) {
        warningResult = ValidationResult.warn(ValidationResult.Message.WHITESPACE, fieldName);
      }
      if (!CharMatcher.ASCII.matchesAllOf(filename)) {
        if (SystemInfo.isWindows) {
          return ValidationResult.error(ValidationResult.Message.NON_ASCII_CHARS_ERROR, fieldName);
        }
        else {
          warningResult = ValidationResult.warn(ValidationResult.Message.NON_ASCII_CHARS_WARNING, fieldName);
        }
      }
      // Check that we can write to that location: make sure we can write into the first extant directory in the path.
      File parent = testFile.getParentFile();

      if (!writableCheckMode.equals(WritableCheckMode.DO_NOT_CHECK) &&
          !testFile.exists() &&
          parent != null &&
          parent.exists() &&
          !parent.canWrite()) {
        // TODO Passing NOT_WRITABLE_IS_ERROR here is a hack. Stop depending on this code and use PathValidator.
        return ValidationResult.pathNotWritable(WritableCheckMode.NOT_WRITABLE_IS_ERROR, fieldName, parent);
      }

      testFile = parent;
    }

    if (SystemInfo.isWindows && projectLocation.length() > WINDOWS_PATH_LENGTH_LIMIT) {
      return ValidationResult.error(ValidationResult.Message.PATH_TOO_LONG, fieldName);
    }

    File file = new File(projectLocation);
    if (file.isFile()) {
      return ValidationResult.error(ValidationResult.Message.PROJECT_LOC_IS_FILE, fieldName);
    }
    if (file.getParent() == null) {
      return ValidationResult.error(ValidationResult.Message.PROJECT_IS_FILE_SYSTEM_ROOT, fieldName);
    }
    if (file.getParentFile().exists() && !file.getParentFile().isDirectory()) {
      return ValidationResult.error(ValidationResult.Message.PARENT_NOT_DIR, fieldName);
    }

    if (!writableCheckMode.equals(WritableCheckMode.DO_NOT_CHECK) && file.exists() && !file.canWrite()) {
      return ValidationResult.pathNotWritable(writableCheckMode, fieldName, file);
    }

    String installLocation = PathManager.getHomePathFor(Application.class);
    if (installLocation != null && FileUtil.isAncestor(new File(installLocation), file, false)) {
      String applicationName = ApplicationNamesInfo.getInstance().getProductName();
      return ValidationResult.error(ValidationResult.Message.INSIDE_ANDROID_STUDIO, fieldName, applicationName);
    }

    if (checkEmpty && file.exists() && listFiles(file).length > 0) {
      return ValidationResult.warn(ValidationResult.Message.NON_EMPTY_DIR, fieldName);
    }

    return (warningResult == null) ? ValidationResult.OK : warningResult;
  }
}
