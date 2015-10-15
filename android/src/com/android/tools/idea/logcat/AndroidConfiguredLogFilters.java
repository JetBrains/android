/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.logcat;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Persistent storage of logcat filters.
 */
@State(
  name = "AndroidConfiguredLogFilters",
  storages = {
    @Storage(file = StoragePathMacros.WORKSPACE_FILE)
  }
)
public final class AndroidConfiguredLogFilters implements PersistentStateComponent<AndroidConfiguredLogFilters> {
  private List<FilterEntry> myFilterEntries = new ArrayList<FilterEntry>();

  @Override
  public AndroidConfiguredLogFilters getState() {
    return this;
  }

  @Override
  public void loadState(AndroidConfiguredLogFilters state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static AndroidConfiguredLogFilters getInstance(final Project project) {
    return ServiceManager.getService(project, AndroidConfiguredLogFilters.class);
  }

  @Tag("filters")
  @AbstractCollection(surroundWithTag = false)
  public List<FilterEntry> getFilterEntries() {
    return new ArrayList<FilterEntry>(myFilterEntries);
  }
  
  @Nullable
  public FilterEntry findFilterEntryByName(@NotNull String name) {
    for (FilterEntry entry : myFilterEntries) {
      if (name.equals(entry.getName())) {
        return entry;
      }
    }
    return null;
  }

  @NotNull
  public FilterEntry createFilterForProcess(int pid) {
    FilterEntry entry = new FilterEntry();
    final String pidString = Integer.toString(pid);
    entry.setName("Process id: " + pidString);
    entry.setPid(pidString);
    return entry;
  }

  public void setFilterEntries(List<FilterEntry> filterEntries) {
    myFilterEntries = filterEntries;
  }

  /**
   * A version of {@link ConfiguredFilter} for serialization / deserialization.
   */
  @Tag("filter")
  static final class FilterEntry {
    private String myName;
    private String myLogMessagePattern;
    private boolean myLogMessageIsRegex = true;
    private String myLogLevel;
    private String myLogTagPattern;
    private boolean myLogTagIsRegex = true;
    private String myPid;
    private String myPackageNamePattern;
    private boolean myPackageNameIsRegex = true;

    public String getName() {
      return myName;
    }

    public String getLogMessagePattern() {
      return myLogMessagePattern;
    }

    public boolean getLogMessageIsRegex() {
      return myLogMessageIsRegex;
    }

    public String getLogLevel() {
      return myLogLevel;
    }

    public String getLogTagPattern() {
      return myLogTagPattern;
    }

    public boolean getLogTagIsRegex() {
      return myLogTagIsRegex;
    }

    public String getPid() {
      return myPid;
    }

    public void setName(String name) {
      myName = name;
    }

    public void setLogMessagePattern(String logMessagePattern) {
      myLogMessagePattern = logMessagePattern;
    }

    public void setLogMessageIsRegex(boolean logMessageIsRegex) {
      myLogMessageIsRegex = logMessageIsRegex;
    }

    public void setLogLevel(String logLevel) {
      myLogLevel = logLevel;
    }

    public void setLogTagPattern(String logTagPattern) {
      myLogTagPattern = logTagPattern;
    }

    public void setLogTagIsRegex(boolean logTagIsRegex) {
      myLogTagIsRegex = logTagIsRegex;
    }

    public void setPid(String pid) {
      myPid = pid;
    }

    public String getPackageNamePattern() {
      return myPackageNamePattern;
    }

    public boolean getPackageNameIsRegex() {
      return myPackageNameIsRegex;
    }

    public void setPackageNamePattern(String packageNamePattern) {
      myPackageNamePattern = packageNamePattern;
    }

    public void setPackageNameIsRegex(boolean logPackageNameIsRegex) {
      myPackageNameIsRegex = logPackageNameIsRegex;
    }
  }
}
