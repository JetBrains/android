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

import com.android.ddmlib.ClientData;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent storage of logcat filters.
 */
@State(
  name = "AndroidConfiguredLogFilters",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
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

  /**
   * Returns a copy of the list of filters. If you need to modify a filter, use
   * {@link #setFilterEntries(List)} to do so.
   */
  @Tag("filters")
  @AbstractCollection(surroundWithTag = false)
  public List<FilterEntry> getFilterEntries() {
    return Lists.newArrayList(Lists.transform(myFilterEntries, new Function<FilterEntry, FilterEntry>() {
      @NotNull
      @Override
      public FilterEntry apply(FilterEntry srcEntry) {
        return new FilterEntry(srcEntry);
      }
    }));
  }

  @NotNull
  public FilterEntry createFilterForClient(@NotNull ClientData clientData) {
    FilterEntry entry = new FilterEntry();
    entry.setPackageNameIsRegex(false);
    String processName = clientData.getClientDescription();
    entry.setPackageNamePattern(processName);
    entry.setName("Process: " + processName);
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
    @Nullable private String myName;
    @Nullable private String myLogMessagePattern;
    private boolean myLogMessageIsRegex = true;
    @Nullable private String myLogLevel;
    @Nullable private String myLogTagPattern;
    private boolean myLogTagIsRegex = true;
    @Nullable private String myPid;
    @Nullable private String myPackageNamePattern;
    private boolean myPackageNameIsRegex = true;

    public FilterEntry() {
    }

    public FilterEntry(@NotNull FilterEntry otherEntry) {
      myName = otherEntry.myName;
      myLogMessagePattern = otherEntry.myLogMessagePattern;
      myLogMessageIsRegex = otherEntry.myLogMessageIsRegex;
      myLogLevel = otherEntry.myLogLevel;
      myLogTagPattern = otherEntry.myLogTagPattern;
      myLogTagIsRegex = otherEntry.myLogTagIsRegex;
      myPid = otherEntry.myPid;
      myPackageNamePattern = otherEntry.myPackageNamePattern;
      myPackageNameIsRegex = otherEntry.myPackageNameIsRegex;
    }

    @Nullable
    public String getName() {
      return myName;
    }

    @Nullable
    public String getLogMessagePattern() {
      return myLogMessagePattern;
    }

    public boolean getLogMessageIsRegex() {
      return myLogMessageIsRegex;
    }

    @Nullable
    public String getLogLevel() {
      return myLogLevel;
    }

    @Nullable
    public String getLogTagPattern() {
      return myLogTagPattern;
    }

    public boolean getLogTagIsRegex() {
      return myLogTagIsRegex;
    }

    @Nullable
    public String getPid() {
      return myPid;
    }

    public void setName(@Nullable String name) {
      myName = name;
    }

    public void setLogMessagePattern(@Nullable String logMessagePattern) {
      myLogMessagePattern = logMessagePattern;
    }

    public void setLogMessageIsRegex(boolean logMessageIsRegex) {
      myLogMessageIsRegex = logMessageIsRegex;
    }

    public void setLogLevel(@Nullable String logLevel) {
      myLogLevel = logLevel;
    }

    public void setLogTagPattern(@Nullable String logTagPattern) {
      myLogTagPattern = logTagPattern;
    }

    public void setLogTagIsRegex(boolean logTagIsRegex) {
      myLogTagIsRegex = logTagIsRegex;
    }

    public void setPid(@Nullable String pid) {
      myPid = pid;
    }

    @Nullable
    public String getPackageNamePattern() {
      return myPackageNamePattern;
    }

    public boolean getPackageNameIsRegex() {
      return myPackageNameIsRegex;
    }

    public void setPackageNamePattern(@Nullable String packageNamePattern) {
      myPackageNamePattern = packageNamePattern;
    }

    public void setPackageNameIsRegex(boolean logPackageNameIsRegex) {
      myPackageNameIsRegex = logPackageNameIsRegex;
    }
  }
}
