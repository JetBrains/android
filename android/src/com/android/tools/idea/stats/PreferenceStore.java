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

package com.android.tools.idea.stats;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Basic re-implementation of the jface PreferenceStore interface.
 * This implements the bare minimum needed by DdmsPreferenceStore which
 * is trivial since the store is merely a wrapper on top of the standard
 * java Properties.
 */
public class PreferenceStore {

  private String myFilename;
  private boolean myChanged = false;
  private final Properties myProperties = new Properties();

  /** Creates an empty store not associated with any file.
   * Trying to invoke save() on this store will throw an exception. */
  public PreferenceStore() {
    myFilename = null;
  }

  /** Creates an empty store. To load from the file, caller should invoke load() */
  public PreferenceStore(String filename) {
    myFilename = filename;
  }

  /** Load from the consturctor's registered filename, erasing the current store. */
  public void load() throws IOException {
    if (myFilename == null) {
      throw new IOException("No filename specified for PreferenceStore.");
    }
    FileInputStream in = new FileInputStream(myFilename);
    try {
      myProperties.load(in);
      myChanged = false;
    } finally {
      in.close();
    }
  }

  /** Save the current store if any value has changed since the last load. */
  public void save() throws IOException {
    if (myFilename == null) {
      throw new IOException("No filename specified for PreferenceStore.");
    }
    if (myChanged) {
      FileOutputStream out = new FileOutputStream(myFilename);
      try {
        save(out, null);
        myChanged = false;
      } finally {
        out.close();
      }
    }
  }

  /** Unconditionally save the current store to the given stream. */
  public void save(FileOutputStream outputStream, @Nullable String header) throws IOException {
    myProperties.store(outputStream, header);
  }

  /** Returns true if the store contains a value for the given key. */
  public boolean contains(String key) {
    return myProperties.containsKey(key);
  }

  /** Returns the long value for the given key or 0 if the value is missing. */
  public long getLong(String key) {
    try {
      return Long.parseLong(myProperties.getProperty(key));
    } catch (Exception ignored) {
      return 0;  // IPreferenceStore.LONG_DEFAULT_DEFAULT is 0
    }
  }

  /** Returns the boolean value for the given key or false if the value is missing. */
  public boolean getBoolean(String key) {
    try {
      return Boolean.parseBoolean(myProperties.getProperty(key));
    } catch (Exception ignored) {
      return false;  // IPreferenceStore.BOOLEAN_DEFAULT_DEFAULT is false
    }
  }

  /** Returns the string value for the given key or the empty string if the value is missing.
   * Note that the store doesn't store nulls and this never returns null. */
  @NotNull
  public String getString(String key) {
    String s = myProperties.getProperty(key);
    return s == null ? "" : s;  // IPreferenceStore.STRING_DEFAULT_DEFAULT is ""
  }

  /** Sets the corresponding long value for the given key and marks the store as changed. */
  public void setValue(String key, long value) {
    myProperties.setProperty(key, Long.toString(value));
    myChanged = true;
  }

  /** Sets the corresponding boolean value for the given key and marks the store as changed. */
  public void setValue(String key, boolean value) {
    myProperties.setProperty(key, Boolean.toString(value));
    myChanged = true;
  }

  /** Sets the corresponding string value for the given key and marks the store as changed. */
  public void setValue(String key, String value) {
    myProperties.setProperty(key, value);
    myChanged = true;
  }
}
