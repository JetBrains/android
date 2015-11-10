/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.ui.wizard;

import com.android.repository.io.FileOp;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

/**
 * A Validation Result for common wizard error cases. Contains a {@link Status} and a
 * {@link Message}.
 * <p/>
 * Use {@link #isOk()} to see if an issue exists or not and, if so, use {@link #getMessage()} for a
 * readable description.
 * <p/>
 * TODO: This class so far is a minimally modified fork from the tools.idea.npw package, but we should extract path validation into its
 * own class in a followup CL. Notes from a code review:
 * "validateLocation is sort of a disaster: there are just to many different situations we want to check for and different ways we want to
 * process the result. To me it seems like what we need is perhaps a path validator class, where we can set up different parameters and then
 * use it to validate multiple paths. So e.g. the project configuration step would create a path validator on init, specify that it cares
 * about writability and some set of special characters or whatever, and then use it repeatedly to validate the path in the ui."
 */
public final class ValidationResult {

  public static final ValidationResult OK = new ValidationResult(Status.OK, Message.OK);

  // TODO: Move these constants into a separate PathValidation class.
  private static final String INVALID_FILENAME_CHARS = "[/\\\\?%*:|\"<>!;]";
  private static final Set<String> INVALID_WINDOWS_FILENAMES = ImmutableSet
    .of("con", "prn", "aux", "clock$", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2",
        "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9", "$mft", "$mftmirr", "$logfile", "$volume", "$attrdef", "$bitmap", "$boot",
        "$badclus", "$secure", "$upcase", "$extend", "$quota", "$objid", "$reparse");
  private static final int WINDOWS_PATH_LENGTH_LIMIT = 100;
  private static final CharMatcher ILLEGAL_CHARACTER_MATCHER = CharMatcher.anyOf(INVALID_FILENAME_CHARS);
  private final Status myStatus;
  private final Message myMessage;
  private final Object[] myMessageParams;

  private ValidationResult(@NotNull Status status, @NotNull Message message, @NotNull Object... messageParams) {
    myStatus = status;
    myMessage = message;
    myMessageParams = messageParams;
  }

  /**
   * Will return {@link ValidationResult#OK} if projectLocation is valid
   * or {@link ValidationResult} with error if not.
   */
  @NotNull
  public static ValidationResult validateLocation(@Nullable String projectLocation, @NotNull FileOp fileOp) {
    return validateLocation(projectLocation, "project location", fileOp, true);
  }

  @NotNull
  public static ValidationResult validateLocation(@Nullable String projectLocation, @NotNull String fieldName, @NotNull FileOp fileOp, boolean checkEmpty) {
    return validateLocation(projectLocation, fieldName, fileOp, checkEmpty, true);
  }

  @NotNull
  public static ValidationResult validateLocation(@Nullable String projectLocation,
                                                  @NotNull String fieldName,
                                                  @NotNull FileOp fileOp,
                                                  boolean checkEmpty,
                                                  boolean checkWriteable) {
    ValidationResult warningResult = null;
    if (projectLocation == null || projectLocation.isEmpty()) {
      return error(ValidationResult.Message.NO_LOCATION_SPECIFIED, fieldName);
    }
    // Check the separators
    if ((File.separatorChar == '/' && projectLocation.contains("\\")) || (File.separatorChar == '\\' && projectLocation.contains("/"))) {
      return error(ValidationResult.Message.BAD_SLASHES, fieldName);
    }
    // Check the individual components for not allowed characters.
    File testFile = new File(projectLocation);
    while (testFile != null) {
      String filename = testFile.getName();
      if (ILLEGAL_CHARACTER_MATCHER.matchesAnyOf(filename)) {
        char illegalChar = filename.charAt(ILLEGAL_CHARACTER_MATCHER.indexIn(filename));
        return error(ValidationResult.Message.ILLEGAL_CHARACTER, fieldName, illegalChar, filename);
      }
      if (INVALID_WINDOWS_FILENAMES.contains(filename.toLowerCase())) {
        return error(ValidationResult.Message.ILLEGAL_FILENAME, fieldName, filename);
      }
      if (CharMatcher.WHITESPACE.matchesAnyOf(filename)) {
        warningResult = warn(ValidationResult.Message.WHITESPACE, fieldName);
      }
      if (!CharMatcher.ASCII.matchesAllOf(filename)) {
        if (SystemInfo.isWindows) {
          return error(ValidationResult.Message.NON_ASCII_CHARS_ERROR, fieldName);
        }
        else {
          warningResult = warn(ValidationResult.Message.NON_ASCII_CHARS_WARNING, fieldName);
        }
      }
      // Check that we can write to that location: make sure we can write into the first extant directory in the path.
      if (checkWriteable && !testFile.exists() && testFile.getParentFile() != null && testFile.getParentFile().exists()) {
        if (!testFile.getParentFile().canWrite()) {
          return error(ValidationResult.Message.PATH_NOT_WRITEABLE, fieldName, testFile.getParentFile().getPath());
        }
      }
      testFile = testFile.getParentFile();
    }

    if (SystemInfo.isWindows && projectLocation.length() > WINDOWS_PATH_LENGTH_LIMIT) {
      return error(ValidationResult.Message.PATH_TOO_LONG, fieldName);
    }

    File file = new File(projectLocation);
    if (file.isFile()) {
      return error(ValidationResult.Message.PROJECT_LOC_IS_FILE, fieldName);
    }
    if (file.getParent() == null) {
      return error(ValidationResult.Message.PROJECT_IS_FILE_SYSTEM_ROOT, fieldName);
    }
    if (file.getParentFile().exists() && !file.getParentFile().isDirectory()) {
      return error(ValidationResult.Message.PARENT_NOT_DIR, fieldName);
    }
    if (checkWriteable && file.exists() && !file.canWrite()) {
      return error(ValidationResult.Message.PATH_NOT_WRITEABLE, fieldName, file.getPath());
    }

    String installLocation = PathManager.getHomePathFor(Application.class);
    if (installLocation != null && FileUtil.isAncestor(new File(installLocation), file, false)) {
      String applicationName = ApplicationNamesInfo.getInstance().getProductName();
      return error(ValidationResult.Message.INSIDE_ANDROID_STUDIO, fieldName, applicationName);
    }

    if (checkEmpty && file.exists() && fileOp.listFiles(file).length > 0) {
      return warn(ValidationResult.Message.NON_EMPTY_DIR, fieldName);
    }

    return (warningResult == null) ? OK : warningResult;
  }

  public static ValidationResult warn(@NotNull Message message, Object... params) {
    return new ValidationResult(Status.WARN, message, params);
  }

  public static ValidationResult error(@NotNull Message message, Object... params) {
    return new ValidationResult(Status.ERROR, message, params);
  }

  public String getMessage() {
    if (myStatus == Status.OK) {
      throw new IllegalStateException("Requesting the error message of a valid result.");
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

  public enum Status {
    OK, WARN, ERROR
  }

  public enum Message {
    OK(""),
    NO_LOCATION_SPECIFIED("Please specify a %1$s"),
    BAD_SLASHES("Your %1$s contains incorrect slashes ('\\' vs '/')"),
    ILLEGAL_CHARACTER("Illegal character in %1$s path: '%2$c' in filename %3s"),
    ILLEGAL_FILENAME("Illegal filename in %1$s path: %2$s"),
    WHITESPACE("%1$s should not contain whitespace, as this can cause problems with the NDK tools."),
    NON_ASCII_CHARS_WARNING("Your %1$s contains non-ASCII characters, which can cause problems. Proceed with caution."),
    NON_ASCII_CHARS_ERROR("Your %1$s contains non-ASCII characters."),
    PATH_NOT_WRITEABLE("The path '%2$s' is not writeable. Please choose a new location."),
    PROJECT_LOC_IS_FILE("There must not already be a file at the %1$s."),
    NON_EMPTY_DIR("A non-empty directory already exists at the specified %1$s. Existing files may be overwritten. Proceed with caution."),
    PROJECT_IS_FILE_SYSTEM_ROOT("The %1$s can not be at the filesystem root"),
    IS_UNDER_ANDROID_STUDIO_ROOT("Path points to a location within Android Studio installation directory"),
    PARENT_NOT_DIR("The %1$s's parent directory must be a directory, not a plain file"),
    INSIDE_ANDROID_STUDIO("The %1$s is inside %2$s install location"),
    PATH_TOO_LONG("The %1$s is too long");

    private final String myText;

    Message(@NotNull String text) {
      myText = text;
    }

    @Override
    public String toString() {
      return myText;
    }
  }
}

