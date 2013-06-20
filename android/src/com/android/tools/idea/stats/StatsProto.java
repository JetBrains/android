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

public final class StatsProto {
  private StatsProto() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }
  public static final class LogEventKeyValues extends
      com.google.protobuf.GeneratedMessageLite {
    // Use LogEventKeyValues.newBuilder() to construct.
    private LogEventKeyValues() {
      initFields();
    }
    private LogEventKeyValues(boolean noInit) {}
    
    private static final LogEventKeyValues defaultInstance;
    public static LogEventKeyValues getDefaultInstance() {
      return defaultInstance;
    }
    
    @Override
    public LogEventKeyValues getDefaultInstanceForType() {
      return defaultInstance;
    }
    
    // optional string key = 1;
    public static final int KEY_FIELD_NUMBER = 1;
    private boolean hasKey;
    private java.lang.String key_ = "";
    public boolean hasKey() { return hasKey; }
    public java.lang.String getKey() { return key_; }
    
    // optional string value = 2;
    public static final int VALUE_FIELD_NUMBER = 2;
    private boolean hasValue;
    private java.lang.String value_ = "";
    public boolean hasValue() { return hasValue; }
    public java.lang.String getValue() { return value_; }
    
    private void initFields() {
    }
    @Override
    public final boolean isInitialized() {
      return true;
    }
    
    @Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      getSerializedSize();
      if (hasKey()) {
        output.writeString(1, getKey());
      }
      if (hasValue()) {
        output.writeString(2, getValue());
      }
    }
    
    private int memoizedSerializedSize = -1;
    @Override
    public int getSerializedSize() {
      int size = memoizedSerializedSize;
      if (size != -1) return size;
    
      size = 0;
      if (hasKey()) {
        size += com.google.protobuf.CodedOutputStream
          .computeStringSize(1, getKey());
      }
      if (hasValue()) {
        size += com.google.protobuf.CodedOutputStream
          .computeStringSize(2, getValue());
      }
      memoizedSerializedSize = size;
      return size;
    }
    
    public static com.android.tools.idea.stats.StatsProto.LogEventKeyValues parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEventKeyValues parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEventKeyValues parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEventKeyValues parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEventKeyValues parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEventKeyValues parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEventKeyValues parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      Builder builder = newBuilder();
      if (builder.mergeDelimitedFrom(input)) {
        return builder.buildParsed();
      } else {
        return null;
      }
    }
    public static com.android.tools.idea.stats.StatsProto.LogEventKeyValues parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      Builder builder = newBuilder();
      if (builder.mergeDelimitedFrom(input, extensionRegistry)) {
        return builder.buildParsed();
      } else {
        return null;
      }
    }
    public static com.android.tools.idea.stats.StatsProto.LogEventKeyValues parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEventKeyValues parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input, extensionRegistry)
               .buildParsed();
    }
    
    public static Builder newBuilder() { return Builder.create(); }
    @Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder(com.android.tools.idea.stats.StatsProto.LogEventKeyValues prototype) {
      return newBuilder().mergeFrom(prototype);
    }
    @Override
    public Builder toBuilder() { return newBuilder(this); }
    
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageLite.Builder<
          com.android.tools.idea.stats.StatsProto.LogEventKeyValues, Builder> {
      private com.android.tools.idea.stats.StatsProto.LogEventKeyValues result;
      
      // Construct using com.android.tools.idea.stats.StatsProto.LogEventKeyValues.newBuilder()
      private Builder() {}
      
      private static Builder create() {
        Builder builder = new Builder();
        builder.result = new com.android.tools.idea.stats.StatsProto.LogEventKeyValues();
        return builder;
      }
      
      protected com.android.tools.idea.stats.StatsProto.LogEventKeyValues internalGetResult() {
        return result;
      }
      
      @Override
      public Builder clear() {
        if (result == null) {
          throw new IllegalStateException(
            "Cannot call clear() after build().");
        }
        result = new com.android.tools.idea.stats.StatsProto.LogEventKeyValues();
        return this;
      }
      
      @Override
      public Builder clone() {
        return create().mergeFrom(result);
      }
      
      @Override
      public com.android.tools.idea.stats.StatsProto.LogEventKeyValues getDefaultInstanceForType() {
        return com.android.tools.idea.stats.StatsProto.LogEventKeyValues.getDefaultInstance();
      }
      
      @Override
      public boolean isInitialized() {
        return result.isInitialized();
      }
      @Override
      public com.android.tools.idea.stats.StatsProto.LogEventKeyValues build() {
        if (result != null && !isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return buildPartial();
      }
      
      private com.android.tools.idea.stats.StatsProto.LogEventKeyValues buildParsed()
          throws com.google.protobuf.InvalidProtocolBufferException {
        if (!isInitialized()) {
          throw newUninitializedMessageException(
            result).asInvalidProtocolBufferException();
        }
        return buildPartial();
      }
      
      @Override
      public com.android.tools.idea.stats.StatsProto.LogEventKeyValues buildPartial() {
        if (result == null) {
          throw new IllegalStateException(
            "build() has already been called on this Builder.");
        }
        com.android.tools.idea.stats.StatsProto.LogEventKeyValues returnMe = result;
        result = null;
        return returnMe;
      }
      
      @Override
      public Builder mergeFrom(com.android.tools.idea.stats.StatsProto.LogEventKeyValues other) {
        if (other == com.android.tools.idea.stats.StatsProto.LogEventKeyValues.getDefaultInstance()) return this;
        if (other.hasKey()) {
          setKey(other.getKey());
        }
        if (other.hasValue()) {
          setValue(other.getValue());
        }
        return this;
      }
      
      @Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        while (true) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              return this;
            default: {
              if (!parseUnknownField(input, extensionRegistry, tag)) {
                return this;
              }
              break;
            }
            case 10: {
              setKey(input.readString());
              break;
            }
            case 18: {
              setValue(input.readString());
              break;
            }
          }
        }
      }
      
      
      // optional string key = 1;
      public boolean hasKey() {
        return result.hasKey();
      }
      public java.lang.String getKey() {
        return result.getKey();
      }
      public Builder setKey(java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  result.hasKey = true;
        result.key_ = value;
        return this;
      }
      public Builder clearKey() {
        result.hasKey = false;
        result.key_ = getDefaultInstance().getKey();
        return this;
      }
      
      // optional string value = 2;
      public boolean hasValue() {
        return result.hasValue();
      }
      public java.lang.String getValue() {
        return result.getValue();
      }
      public Builder setValue(java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  result.hasValue = true;
        result.value_ = value;
        return this;
      }
      public Builder clearValue() {
        result.hasValue = false;
        result.value_ = getDefaultInstance().getValue();
        return this;
      }
      
      // @@protoc_insertion_point(builder_scope:com.android.tools.idea.stats.LogEventKeyValues)
    }
    
    static {
      defaultInstance = new LogEventKeyValues(true);
      com.android.tools.idea.stats.StatsProto.internalForceInit();
      defaultInstance.initFields();
    }
    
    // @@protoc_insertion_point(class_scope:com.android.tools.idea.stats.LogEventKeyValues)
  }
  
  public static final class LogEvent extends
      com.google.protobuf.GeneratedMessageLite {
    // Use LogEvent.newBuilder() to construct.
    private LogEvent() {
      initFields();
    }
    private LogEvent(boolean noInit) {}
    
    private static final LogEvent defaultInstance;
    public static LogEvent getDefaultInstance() {
      return defaultInstance;
    }
    
    @Override
    public LogEvent getDefaultInstanceForType() {
      return defaultInstance;
    }
    
    // optional int64 event_time_ms = 1;
    public static final int EVENT_TIME_MS_FIELD_NUMBER = 1;
    private boolean hasEventTimeMs;
    private long eventTimeMs_ = 0L;
    public boolean hasEventTimeMs() { return hasEventTimeMs; }
    public long getEventTimeMs() { return eventTimeMs_; }
    
    // optional string tag = 2;
    public static final int TAG_FIELD_NUMBER = 2;
    private boolean hasTag;
    private java.lang.String tag_ = "";
    public boolean hasTag() { return hasTag; }
    public java.lang.String getTag() { return tag_; }
    
    // repeated .com.android.tools.idea.stats.LogEventKeyValues value = 3;
    public static final int VALUE_FIELD_NUMBER = 3;
    private java.util.List<com.android.tools.idea.stats.StatsProto.LogEventKeyValues> value_ =
      java.util.Collections.emptyList();
    public java.util.List<com.android.tools.idea.stats.StatsProto.LogEventKeyValues> getValueList() {
      return value_;
    }
    public int getValueCount() { return value_.size(); }
    public com.android.tools.idea.stats.StatsProto.LogEventKeyValues getValue(int index) {
      return value_.get(index);
    }
    
    private void initFields() {
    }
    @Override
    public final boolean isInitialized() {
      return true;
    }
    
    @Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      getSerializedSize();
      if (hasEventTimeMs()) {
        output.writeInt64(1, getEventTimeMs());
      }
      if (hasTag()) {
        output.writeString(2, getTag());
      }
      for (com.android.tools.idea.stats.StatsProto.LogEventKeyValues element : getValueList()) {
        output.writeMessage(3, element);
      }
    }
    
    private int memoizedSerializedSize = -1;
    @Override
    public int getSerializedSize() {
      int size = memoizedSerializedSize;
      if (size != -1) return size;
    
      size = 0;
      if (hasEventTimeMs()) {
        size += com.google.protobuf.CodedOutputStream
          .computeInt64Size(1, getEventTimeMs());
      }
      if (hasTag()) {
        size += com.google.protobuf.CodedOutputStream
          .computeStringSize(2, getTag());
      }
      for (com.android.tools.idea.stats.StatsProto.LogEventKeyValues element : getValueList()) {
        size += com.google.protobuf.CodedOutputStream
          .computeMessageSize(3, element);
      }
      memoizedSerializedSize = size;
      return size;
    }
    
    public static com.android.tools.idea.stats.StatsProto.LogEvent parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEvent parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEvent parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEvent parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEvent parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEvent parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEvent parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      Builder builder = newBuilder();
      if (builder.mergeDelimitedFrom(input)) {
        return builder.buildParsed();
      } else {
        return null;
      }
    }
    public static com.android.tools.idea.stats.StatsProto.LogEvent parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      Builder builder = newBuilder();
      if (builder.mergeDelimitedFrom(input, extensionRegistry)) {
        return builder.buildParsed();
      } else {
        return null;
      }
    }
    public static com.android.tools.idea.stats.StatsProto.LogEvent parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogEvent parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input, extensionRegistry)
               .buildParsed();
    }
    
    public static Builder newBuilder() { return Builder.create(); }
    @Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder(com.android.tools.idea.stats.StatsProto.LogEvent prototype) {
      return newBuilder().mergeFrom(prototype);
    }
    @Override
    public Builder toBuilder() { return newBuilder(this); }
    
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageLite.Builder<
          com.android.tools.idea.stats.StatsProto.LogEvent, Builder> {
      private com.android.tools.idea.stats.StatsProto.LogEvent result;
      
      // Construct using com.android.tools.idea.stats.StatsProto.LogEvent.newBuilder()
      private Builder() {}
      
      private static Builder create() {
        Builder builder = new Builder();
        builder.result = new com.android.tools.idea.stats.StatsProto.LogEvent();
        return builder;
      }
      
      protected com.android.tools.idea.stats.StatsProto.LogEvent internalGetResult() {
        return result;
      }
      
      @Override
      public Builder clear() {
        if (result == null) {
          throw new IllegalStateException(
            "Cannot call clear() after build().");
        }
        result = new com.android.tools.idea.stats.StatsProto.LogEvent();
        return this;
      }
      
      @Override
      public Builder clone() {
        return create().mergeFrom(result);
      }
      
      @Override
      public com.android.tools.idea.stats.StatsProto.LogEvent getDefaultInstanceForType() {
        return com.android.tools.idea.stats.StatsProto.LogEvent.getDefaultInstance();
      }
      
      @Override
      public boolean isInitialized() {
        return result.isInitialized();
      }
      @Override
      public com.android.tools.idea.stats.StatsProto.LogEvent build() {
        if (result != null && !isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return buildPartial();
      }
      
      private com.android.tools.idea.stats.StatsProto.LogEvent buildParsed()
          throws com.google.protobuf.InvalidProtocolBufferException {
        if (!isInitialized()) {
          throw newUninitializedMessageException(
            result).asInvalidProtocolBufferException();
        }
        return buildPartial();
      }
      
      @Override
      public com.android.tools.idea.stats.StatsProto.LogEvent buildPartial() {
        if (result == null) {
          throw new IllegalStateException(
            "build() has already been called on this Builder.");
        }
        if (result.value_ != java.util.Collections.EMPTY_LIST) {
          result.value_ =
            java.util.Collections.unmodifiableList(result.value_);
        }
        com.android.tools.idea.stats.StatsProto.LogEvent returnMe = result;
        result = null;
        return returnMe;
      }
      
      @Override
      public Builder mergeFrom(com.android.tools.idea.stats.StatsProto.LogEvent other) {
        if (other == com.android.tools.idea.stats.StatsProto.LogEvent.getDefaultInstance()) return this;
        if (other.hasEventTimeMs()) {
          setEventTimeMs(other.getEventTimeMs());
        }
        if (other.hasTag()) {
          setTag(other.getTag());
        }
        if (!other.value_.isEmpty()) {
          if (result.value_.isEmpty()) {
            result.value_ = new java.util.ArrayList<com.android.tools.idea.stats.StatsProto.LogEventKeyValues>();
          }
          result.value_.addAll(other.value_);
        }
        return this;
      }
      
      @Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        while (true) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              return this;
            default: {
              if (!parseUnknownField(input, extensionRegistry, tag)) {
                return this;
              }
              break;
            }
            case 8: {
              setEventTimeMs(input.readInt64());
              break;
            }
            case 18: {
              setTag(input.readString());
              break;
            }
            case 26: {
              com.android.tools.idea.stats.StatsProto.LogEventKeyValues.Builder subBuilder = com.android.tools.idea.stats.StatsProto.LogEventKeyValues.newBuilder();
              input.readMessage(subBuilder, extensionRegistry);
              addValue(subBuilder.buildPartial());
              break;
            }
          }
        }
      }
      
      
      // optional int64 event_time_ms = 1;
      public boolean hasEventTimeMs() {
        return result.hasEventTimeMs();
      }
      public long getEventTimeMs() {
        return result.getEventTimeMs();
      }
      public Builder setEventTimeMs(long value) {
        result.hasEventTimeMs = true;
        result.eventTimeMs_ = value;
        return this;
      }
      public Builder clearEventTimeMs() {
        result.hasEventTimeMs = false;
        result.eventTimeMs_ = 0L;
        return this;
      }
      
      // optional string tag = 2;
      public boolean hasTag() {
        return result.hasTag();
      }
      public java.lang.String getTag() {
        return result.getTag();
      }
      public Builder setTag(java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  result.hasTag = true;
        result.tag_ = value;
        return this;
      }
      public Builder clearTag() {
        result.hasTag = false;
        result.tag_ = getDefaultInstance().getTag();
        return this;
      }
      
      // repeated .com.android.tools.idea.stats.LogEventKeyValues value = 3;
      public java.util.List<com.android.tools.idea.stats.StatsProto.LogEventKeyValues> getValueList() {
        return java.util.Collections.unmodifiableList(result.value_);
      }
      public int getValueCount() {
        return result.getValueCount();
      }
      public com.android.tools.idea.stats.StatsProto.LogEventKeyValues getValue(int index) {
        return result.getValue(index);
      }
      public Builder setValue(int index, com.android.tools.idea.stats.StatsProto.LogEventKeyValues value) {
        if (value == null) {
          throw new NullPointerException();
        }
        result.value_.set(index, value);
        return this;
      }
      public Builder setValue(int index, com.android.tools.idea.stats.StatsProto.LogEventKeyValues.Builder builderForValue) {
        result.value_.set(index, builderForValue.build());
        return this;
      }
      public Builder addValue(com.android.tools.idea.stats.StatsProto.LogEventKeyValues value) {
        if (value == null) {
          throw new NullPointerException();
        }
        if (result.value_.isEmpty()) {
          result.value_ = new java.util.ArrayList<com.android.tools.idea.stats.StatsProto.LogEventKeyValues>();
        }
        result.value_.add(value);
        return this;
      }
      public Builder addValue(com.android.tools.idea.stats.StatsProto.LogEventKeyValues.Builder builderForValue) {
        if (result.value_.isEmpty()) {
          result.value_ = new java.util.ArrayList<com.android.tools.idea.stats.StatsProto.LogEventKeyValues>();
        }
        result.value_.add(builderForValue.build());
        return this;
      }
      public Builder addAllValue(
          java.lang.Iterable<? extends com.android.tools.idea.stats.StatsProto.LogEventKeyValues> values) {
        if (result.value_.isEmpty()) {
          result.value_ = new java.util.ArrayList<com.android.tools.idea.stats.StatsProto.LogEventKeyValues>();
        }
        super.addAll(values, result.value_);
        return this;
      }
      public Builder clearValue() {
        result.value_ = java.util.Collections.emptyList();
        return this;
      }
      
      // @@protoc_insertion_point(builder_scope:com.android.tools.idea.stats.LogEvent)
    }
    
    static {
      defaultInstance = new LogEvent(true);
      com.android.tools.idea.stats.StatsProto.internalForceInit();
      defaultInstance.initFields();
    }
    
    // @@protoc_insertion_point(class_scope:com.android.tools.idea.stats.LogEvent)
  }
  
  public static final class DesktopClientInfo extends
      com.google.protobuf.GeneratedMessageLite {
    // Use DesktopClientInfo.newBuilder() to construct.
    private DesktopClientInfo() {
      initFields();
    }
    private DesktopClientInfo(boolean noInit) {}
    
    private static final DesktopClientInfo defaultInstance;
    public static DesktopClientInfo getDefaultInstance() {
      return defaultInstance;
    }
    
    @Override
    public DesktopClientInfo getDefaultInstanceForType() {
      return defaultInstance;
    }
    
    // optional string client_id = 1;
    public static final int CLIENT_ID_FIELD_NUMBER = 1;
    private boolean hasClientId;
    private java.lang.String clientId_ = "";
    public boolean hasClientId() { return hasClientId; }
    public java.lang.String getClientId() { return clientId_; }
    
    // optional string os = 3;
    public static final int OS_FIELD_NUMBER = 3;
    private boolean hasOs;
    private java.lang.String os_ = "";
    public boolean hasOs() { return hasOs; }
    public java.lang.String getOs() { return os_; }
    
    // optional string os_major_version = 4;
    public static final int OS_MAJOR_VERSION_FIELD_NUMBER = 4;
    private boolean hasOsMajorVersion;
    private java.lang.String osMajorVersion_ = "";
    public boolean hasOsMajorVersion() { return hasOsMajorVersion; }
    public java.lang.String getOsMajorVersion() { return osMajorVersion_; }
    
    // optional string os_full_version = 5;
    public static final int OS_FULL_VERSION_FIELD_NUMBER = 5;
    private boolean hasOsFullVersion;
    private java.lang.String osFullVersion_ = "";
    public boolean hasOsFullVersion() { return hasOsFullVersion; }
    public java.lang.String getOsFullVersion() { return osFullVersion_; }
    
    // optional string application_build = 6;
    public static final int APPLICATION_BUILD_FIELD_NUMBER = 6;
    private boolean hasApplicationBuild;
    private java.lang.String applicationBuild_ = "";
    public boolean hasApplicationBuild() { return hasApplicationBuild; }
    public java.lang.String getApplicationBuild() { return applicationBuild_; }
    
    private void initFields() {
    }
    @Override
    public final boolean isInitialized() {
      return true;
    }
    
    @Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      getSerializedSize();
      if (hasClientId()) {
        output.writeString(1, getClientId());
      }
      if (hasOs()) {
        output.writeString(3, getOs());
      }
      if (hasOsMajorVersion()) {
        output.writeString(4, getOsMajorVersion());
      }
      if (hasOsFullVersion()) {
        output.writeString(5, getOsFullVersion());
      }
      if (hasApplicationBuild()) {
        output.writeString(6, getApplicationBuild());
      }
    }
    
    private int memoizedSerializedSize = -1;
    @Override
    public int getSerializedSize() {
      int size = memoizedSerializedSize;
      if (size != -1) return size;
    
      size = 0;
      if (hasClientId()) {
        size += com.google.protobuf.CodedOutputStream
          .computeStringSize(1, getClientId());
      }
      if (hasOs()) {
        size += com.google.protobuf.CodedOutputStream
          .computeStringSize(3, getOs());
      }
      if (hasOsMajorVersion()) {
        size += com.google.protobuf.CodedOutputStream
          .computeStringSize(4, getOsMajorVersion());
      }
      if (hasOsFullVersion()) {
        size += com.google.protobuf.CodedOutputStream
          .computeStringSize(5, getOsFullVersion());
      }
      if (hasApplicationBuild()) {
        size += com.google.protobuf.CodedOutputStream
          .computeStringSize(6, getApplicationBuild());
      }
      memoizedSerializedSize = size;
      return size;
    }
    
    public static com.android.tools.idea.stats.StatsProto.DesktopClientInfo parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.DesktopClientInfo parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.DesktopClientInfo parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.DesktopClientInfo parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.DesktopClientInfo parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.DesktopClientInfo parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.DesktopClientInfo parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      Builder builder = newBuilder();
      if (builder.mergeDelimitedFrom(input)) {
        return builder.buildParsed();
      } else {
        return null;
      }
    }
    public static com.android.tools.idea.stats.StatsProto.DesktopClientInfo parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      Builder builder = newBuilder();
      if (builder.mergeDelimitedFrom(input, extensionRegistry)) {
        return builder.buildParsed();
      } else {
        return null;
      }
    }
    public static com.android.tools.idea.stats.StatsProto.DesktopClientInfo parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.DesktopClientInfo parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input, extensionRegistry)
               .buildParsed();
    }
    
    public static Builder newBuilder() { return Builder.create(); }
    @Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder(com.android.tools.idea.stats.StatsProto.DesktopClientInfo prototype) {
      return newBuilder().mergeFrom(prototype);
    }
    @Override
    public Builder toBuilder() { return newBuilder(this); }
    
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageLite.Builder<
          com.android.tools.idea.stats.StatsProto.DesktopClientInfo, Builder> {
      private com.android.tools.idea.stats.StatsProto.DesktopClientInfo result;
      
      // Construct using com.android.tools.idea.stats.StatsProto.DesktopClientInfo.newBuilder()
      private Builder() {}
      
      private static Builder create() {
        Builder builder = new Builder();
        builder.result = new com.android.tools.idea.stats.StatsProto.DesktopClientInfo();
        return builder;
      }
      
      protected com.android.tools.idea.stats.StatsProto.DesktopClientInfo internalGetResult() {
        return result;
      }
      
      @Override
      public Builder clear() {
        if (result == null) {
          throw new IllegalStateException(
            "Cannot call clear() after build().");
        }
        result = new com.android.tools.idea.stats.StatsProto.DesktopClientInfo();
        return this;
      }
      
      @Override
      public Builder clone() {
        return create().mergeFrom(result);
      }
      
      @Override
      public com.android.tools.idea.stats.StatsProto.DesktopClientInfo getDefaultInstanceForType() {
        return com.android.tools.idea.stats.StatsProto.DesktopClientInfo.getDefaultInstance();
      }
      
      @Override
      public boolean isInitialized() {
        return result.isInitialized();
      }
      @Override
      public com.android.tools.idea.stats.StatsProto.DesktopClientInfo build() {
        if (result != null && !isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return buildPartial();
      }
      
      private com.android.tools.idea.stats.StatsProto.DesktopClientInfo buildParsed()
          throws com.google.protobuf.InvalidProtocolBufferException {
        if (!isInitialized()) {
          throw newUninitializedMessageException(
            result).asInvalidProtocolBufferException();
        }
        return buildPartial();
      }
      
      @Override
      public com.android.tools.idea.stats.StatsProto.DesktopClientInfo buildPartial() {
        if (result == null) {
          throw new IllegalStateException(
            "build() has already been called on this Builder.");
        }
        com.android.tools.idea.stats.StatsProto.DesktopClientInfo returnMe = result;
        result = null;
        return returnMe;
      }
      
      @Override
      public Builder mergeFrom(com.android.tools.idea.stats.StatsProto.DesktopClientInfo other) {
        if (other == com.android.tools.idea.stats.StatsProto.DesktopClientInfo.getDefaultInstance()) return this;
        if (other.hasClientId()) {
          setClientId(other.getClientId());
        }
        if (other.hasOs()) {
          setOs(other.getOs());
        }
        if (other.hasOsMajorVersion()) {
          setOsMajorVersion(other.getOsMajorVersion());
        }
        if (other.hasOsFullVersion()) {
          setOsFullVersion(other.getOsFullVersion());
        }
        if (other.hasApplicationBuild()) {
          setApplicationBuild(other.getApplicationBuild());
        }
        return this;
      }
      
      @Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        while (true) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              return this;
            default: {
              if (!parseUnknownField(input, extensionRegistry, tag)) {
                return this;
              }
              break;
            }
            case 10: {
              setClientId(input.readString());
              break;
            }
            case 26: {
              setOs(input.readString());
              break;
            }
            case 34: {
              setOsMajorVersion(input.readString());
              break;
            }
            case 42: {
              setOsFullVersion(input.readString());
              break;
            }
            case 50: {
              setApplicationBuild(input.readString());
              break;
            }
          }
        }
      }
      
      
      // optional string client_id = 1;
      public boolean hasClientId() {
        return result.hasClientId();
      }
      public java.lang.String getClientId() {
        return result.getClientId();
      }
      public Builder setClientId(java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  result.hasClientId = true;
        result.clientId_ = value;
        return this;
      }
      public Builder clearClientId() {
        result.hasClientId = false;
        result.clientId_ = getDefaultInstance().getClientId();
        return this;
      }
      
      // optional string os = 3;
      public boolean hasOs() {
        return result.hasOs();
      }
      public java.lang.String getOs() {
        return result.getOs();
      }
      public Builder setOs(java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  result.hasOs = true;
        result.os_ = value;
        return this;
      }
      public Builder clearOs() {
        result.hasOs = false;
        result.os_ = getDefaultInstance().getOs();
        return this;
      }
      
      // optional string os_major_version = 4;
      public boolean hasOsMajorVersion() {
        return result.hasOsMajorVersion();
      }
      public java.lang.String getOsMajorVersion() {
        return result.getOsMajorVersion();
      }
      public Builder setOsMajorVersion(java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  result.hasOsMajorVersion = true;
        result.osMajorVersion_ = value;
        return this;
      }
      public Builder clearOsMajorVersion() {
        result.hasOsMajorVersion = false;
        result.osMajorVersion_ = getDefaultInstance().getOsMajorVersion();
        return this;
      }
      
      // optional string os_full_version = 5;
      public boolean hasOsFullVersion() {
        return result.hasOsFullVersion();
      }
      public java.lang.String getOsFullVersion() {
        return result.getOsFullVersion();
      }
      public Builder setOsFullVersion(java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  result.hasOsFullVersion = true;
        result.osFullVersion_ = value;
        return this;
      }
      public Builder clearOsFullVersion() {
        result.hasOsFullVersion = false;
        result.osFullVersion_ = getDefaultInstance().getOsFullVersion();
        return this;
      }
      
      // optional string application_build = 6;
      public boolean hasApplicationBuild() {
        return result.hasApplicationBuild();
      }
      public java.lang.String getApplicationBuild() {
        return result.getApplicationBuild();
      }
      public Builder setApplicationBuild(java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  result.hasApplicationBuild = true;
        result.applicationBuild_ = value;
        return this;
      }
      public Builder clearApplicationBuild() {
        result.hasApplicationBuild = false;
        result.applicationBuild_ = getDefaultInstance().getApplicationBuild();
        return this;
      }
      
      // @@protoc_insertion_point(builder_scope:com.android.tools.idea.stats.DesktopClientInfo)
    }
    
    static {
      defaultInstance = new DesktopClientInfo(true);
      com.android.tools.idea.stats.StatsProto.internalForceInit();
      defaultInstance.initFields();
    }
    
    // @@protoc_insertion_point(class_scope:com.android.tools.idea.stats.DesktopClientInfo)
  }
  
  public static final class ClientInfo extends
      com.google.protobuf.GeneratedMessageLite {
    // Use ClientInfo.newBuilder() to construct.
    private ClientInfo() {
      initFields();
    }
    private ClientInfo(boolean noInit) {}
    
    private static final ClientInfo defaultInstance;
    public static ClientInfo getDefaultInstance() {
      return defaultInstance;
    }
    
    @Override
    public ClientInfo getDefaultInstanceForType() {
      return defaultInstance;
    }
    
    public enum ClientType
        implements com.google.protobuf.Internal.EnumLite {
      UNKNOWN(0, 0),
      RESERVED_1(1, 1),
      DESKTOP(2, 2),
      RESERVED_3(3, 3),
      RESERVED_4(4, 4),
      ;
      
      
      @Override
      public final int getNumber() { return value; }
      
      public static ClientType valueOf(int value) {
        switch (value) {
          case 0: return UNKNOWN;
          case 1: return RESERVED_1;
          case 2: return DESKTOP;
          case 3: return RESERVED_3;
          case 4: return RESERVED_4;
          default: return null;
        }
      }
      
      public static com.google.protobuf.Internal.EnumLiteMap<ClientType>
          internalGetValueMap() {
        return internalValueMap;
      }
      private static com.google.protobuf.Internal.EnumLiteMap<ClientType>
          internalValueMap =
            new com.google.protobuf.Internal.EnumLiteMap<ClientType>() {
              @Override
              public ClientType findValueByNumber(int number) {
                return ClientType.valueOf(number)
      ;        }
            };
      
      private final int index;
      private final int value;
      private ClientType(int index, int value) {
        this.index = index;
        this.value = value;
      }
      
      // @@protoc_insertion_point(enum_scope:com.android.tools.idea.stats.ClientInfo.ClientType)
    }
    
    // optional .com.android.tools.idea.stats.ClientInfo.ClientType client_type = 1 [default = UNKNOWN];
    public static final int CLIENT_TYPE_FIELD_NUMBER = 1;
    private boolean hasClientType;
    private com.android.tools.idea.stats.StatsProto.ClientInfo.ClientType clientType_;
    public boolean hasClientType() { return hasClientType; }
    public com.android.tools.idea.stats.StatsProto.ClientInfo.ClientType getClientType() { return clientType_; }
    
    // optional .com.android.tools.idea.stats.DesktopClientInfo desktop_client_info = 3;
    public static final int DESKTOP_CLIENT_INFO_FIELD_NUMBER = 3;
    private boolean hasDesktopClientInfo;
    private com.android.tools.idea.stats.StatsProto.DesktopClientInfo desktopClientInfo_;
    public boolean hasDesktopClientInfo() { return hasDesktopClientInfo; }
    public com.android.tools.idea.stats.StatsProto.DesktopClientInfo getDesktopClientInfo() { return desktopClientInfo_; }
    
    private void initFields() {
      clientType_ = com.android.tools.idea.stats.StatsProto.ClientInfo.ClientType.UNKNOWN;
      desktopClientInfo_ = com.android.tools.idea.stats.StatsProto.DesktopClientInfo.getDefaultInstance();
    }
    @Override
    public final boolean isInitialized() {
      return true;
    }
    
    @Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      getSerializedSize();
      if (hasClientType()) {
        output.writeEnum(1, getClientType().getNumber());
      }
      if (hasDesktopClientInfo()) {
        output.writeMessage(3, getDesktopClientInfo());
      }
    }
    
    private int memoizedSerializedSize = -1;
    @Override
    public int getSerializedSize() {
      int size = memoizedSerializedSize;
      if (size != -1) return size;
    
      size = 0;
      if (hasClientType()) {
        size += com.google.protobuf.CodedOutputStream
          .computeEnumSize(1, getClientType().getNumber());
      }
      if (hasDesktopClientInfo()) {
        size += com.google.protobuf.CodedOutputStream
          .computeMessageSize(3, getDesktopClientInfo());
      }
      memoizedSerializedSize = size;
      return size;
    }
    
    public static com.android.tools.idea.stats.StatsProto.ClientInfo parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.ClientInfo parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.ClientInfo parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.ClientInfo parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.ClientInfo parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.ClientInfo parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.ClientInfo parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      Builder builder = newBuilder();
      if (builder.mergeDelimitedFrom(input)) {
        return builder.buildParsed();
      } else {
        return null;
      }
    }
    public static com.android.tools.idea.stats.StatsProto.ClientInfo parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      Builder builder = newBuilder();
      if (builder.mergeDelimitedFrom(input, extensionRegistry)) {
        return builder.buildParsed();
      } else {
        return null;
      }
    }
    public static com.android.tools.idea.stats.StatsProto.ClientInfo parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.ClientInfo parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input, extensionRegistry)
               .buildParsed();
    }
    
    public static Builder newBuilder() { return Builder.create(); }
    @Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder(com.android.tools.idea.stats.StatsProto.ClientInfo prototype) {
      return newBuilder().mergeFrom(prototype);
    }
    @Override
    public Builder toBuilder() { return newBuilder(this); }
    
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageLite.Builder<
          com.android.tools.idea.stats.StatsProto.ClientInfo, Builder> {
      private com.android.tools.idea.stats.StatsProto.ClientInfo result;
      
      // Construct using com.android.tools.idea.stats.StatsProto.ClientInfo.newBuilder()
      private Builder() {}
      
      private static Builder create() {
        Builder builder = new Builder();
        builder.result = new com.android.tools.idea.stats.StatsProto.ClientInfo();
        return builder;
      }
      
      protected com.android.tools.idea.stats.StatsProto.ClientInfo internalGetResult() {
        return result;
      }
      
      @Override
      public Builder clear() {
        if (result == null) {
          throw new IllegalStateException(
            "Cannot call clear() after build().");
        }
        result = new com.android.tools.idea.stats.StatsProto.ClientInfo();
        return this;
      }
      
      @Override
      public Builder clone() {
        return create().mergeFrom(result);
      }
      
      @Override
      public com.android.tools.idea.stats.StatsProto.ClientInfo getDefaultInstanceForType() {
        return com.android.tools.idea.stats.StatsProto.ClientInfo.getDefaultInstance();
      }
      
      @Override
      public boolean isInitialized() {
        return result.isInitialized();
      }
      @Override
      public com.android.tools.idea.stats.StatsProto.ClientInfo build() {
        if (result != null && !isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return buildPartial();
      }
      
      private com.android.tools.idea.stats.StatsProto.ClientInfo buildParsed()
          throws com.google.protobuf.InvalidProtocolBufferException {
        if (!isInitialized()) {
          throw newUninitializedMessageException(
            result).asInvalidProtocolBufferException();
        }
        return buildPartial();
      }
      
      @Override
      public com.android.tools.idea.stats.StatsProto.ClientInfo buildPartial() {
        if (result == null) {
          throw new IllegalStateException(
            "build() has already been called on this Builder.");
        }
        com.android.tools.idea.stats.StatsProto.ClientInfo returnMe = result;
        result = null;
        return returnMe;
      }
      
      @Override
      public Builder mergeFrom(com.android.tools.idea.stats.StatsProto.ClientInfo other) {
        if (other == com.android.tools.idea.stats.StatsProto.ClientInfo.getDefaultInstance()) return this;
        if (other.hasClientType()) {
          setClientType(other.getClientType());
        }
        if (other.hasDesktopClientInfo()) {
          mergeDesktopClientInfo(other.getDesktopClientInfo());
        }
        return this;
      }
      
      @Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        while (true) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              return this;
            default: {
              if (!parseUnknownField(input, extensionRegistry, tag)) {
                return this;
              }
              break;
            }
            case 8: {
              int rawValue = input.readEnum();
              com.android.tools.idea.stats.StatsProto.ClientInfo.ClientType value = com.android.tools.idea.stats.StatsProto.ClientInfo.ClientType.valueOf(rawValue);
              if (value != null) {
                setClientType(value);
              }
              break;
            }
            case 26: {
              com.android.tools.idea.stats.StatsProto.DesktopClientInfo.Builder subBuilder = com.android.tools.idea.stats.StatsProto.DesktopClientInfo.newBuilder();
              if (hasDesktopClientInfo()) {
                subBuilder.mergeFrom(getDesktopClientInfo());
              }
              input.readMessage(subBuilder, extensionRegistry);
              setDesktopClientInfo(subBuilder.buildPartial());
              break;
            }
          }
        }
      }
      
      
      // optional .com.android.tools.idea.stats.ClientInfo.ClientType client_type = 1 [default = UNKNOWN];
      public boolean hasClientType() {
        return result.hasClientType();
      }
      public com.android.tools.idea.stats.StatsProto.ClientInfo.ClientType getClientType() {
        return result.getClientType();
      }
      public Builder setClientType(com.android.tools.idea.stats.StatsProto.ClientInfo.ClientType value) {
        if (value == null) {
          throw new NullPointerException();
        }
        result.hasClientType = true;
        result.clientType_ = value;
        return this;
      }
      public Builder clearClientType() {
        result.hasClientType = false;
        result.clientType_ = com.android.tools.idea.stats.StatsProto.ClientInfo.ClientType.UNKNOWN;
        return this;
      }
      
      // optional .com.android.tools.idea.stats.DesktopClientInfo desktop_client_info = 3;
      public boolean hasDesktopClientInfo() {
        return result.hasDesktopClientInfo();
      }
      public com.android.tools.idea.stats.StatsProto.DesktopClientInfo getDesktopClientInfo() {
        return result.getDesktopClientInfo();
      }
      public Builder setDesktopClientInfo(com.android.tools.idea.stats.StatsProto.DesktopClientInfo value) {
        if (value == null) {
          throw new NullPointerException();
        }
        result.hasDesktopClientInfo = true;
        result.desktopClientInfo_ = value;
        return this;
      }
      public Builder setDesktopClientInfo(com.android.tools.idea.stats.StatsProto.DesktopClientInfo.Builder builderForValue) {
        result.hasDesktopClientInfo = true;
        result.desktopClientInfo_ = builderForValue.build();
        return this;
      }
      public Builder mergeDesktopClientInfo(com.android.tools.idea.stats.StatsProto.DesktopClientInfo value) {
        if (result.hasDesktopClientInfo() &&
            result.desktopClientInfo_ != com.android.tools.idea.stats.StatsProto.DesktopClientInfo.getDefaultInstance()) {
          result.desktopClientInfo_ =
            com.android.tools.idea.stats.StatsProto.DesktopClientInfo.newBuilder(result.desktopClientInfo_).mergeFrom(value).buildPartial();
        } else {
          result.desktopClientInfo_ = value;
        }
        result.hasDesktopClientInfo = true;
        return this;
      }
      public Builder clearDesktopClientInfo() {
        result.hasDesktopClientInfo = false;
        result.desktopClientInfo_ = com.android.tools.idea.stats.StatsProto.DesktopClientInfo.getDefaultInstance();
        return this;
      }
      
      // @@protoc_insertion_point(builder_scope:com.android.tools.idea.stats.ClientInfo)
    }
    
    static {
      defaultInstance = new ClientInfo(true);
      com.android.tools.idea.stats.StatsProto.internalForceInit();
      defaultInstance.initFields();
    }
    
    // @@protoc_insertion_point(class_scope:com.android.tools.idea.stats.ClientInfo)
  }
  
  public static final class LogRequest extends
      com.google.protobuf.GeneratedMessageLite {
    // Use LogRequest.newBuilder() to construct.
    private LogRequest() {
      initFields();
    }
    private LogRequest(boolean noInit) {}
    
    private static final LogRequest defaultInstance;
    public static LogRequest getDefaultInstance() {
      return defaultInstance;
    }
    
    @Override
    public LogRequest getDefaultInstanceForType() {
      return defaultInstance;
    }
    
    public enum LogSource
        implements com.google.protobuf.Internal.EnumLite {
      UNKNOWN(0, -1),
      RESERVED_0(1, 0),
      RESERVED_1(2, 1),
      RESERVED_2(3, 2),
      RESERVED_3(4, 3),
      RESERVED_4(5, 4),
      RESERVED_5(6, 5),
      RESERVED_6(7, 6),
      ANDROID_STUDIO(8, 7),
      RESERVED_8(9, 8),
      RESERVED_9(10, 9),
      ;
      
      
      @Override
      public final int getNumber() { return value; }
      
      public static LogSource valueOf(int value) {
        switch (value) {
          case -1: return UNKNOWN;
          case 0: return RESERVED_0;
          case 1: return RESERVED_1;
          case 2: return RESERVED_2;
          case 3: return RESERVED_3;
          case 4: return RESERVED_4;
          case 5: return RESERVED_5;
          case 6: return RESERVED_6;
          case 7: return ANDROID_STUDIO;
          case 8: return RESERVED_8;
          case 9: return RESERVED_9;
          default: return null;
        }
      }
      
      public static com.google.protobuf.Internal.EnumLiteMap<LogSource>
          internalGetValueMap() {
        return internalValueMap;
      }
      private static com.google.protobuf.Internal.EnumLiteMap<LogSource>
          internalValueMap =
            new com.google.protobuf.Internal.EnumLiteMap<LogSource>() {
              @Override
              public LogSource findValueByNumber(int number) {
                return LogSource.valueOf(number)
      ;        }
            };
      
      private final int index;
      private final int value;
      private LogSource(int index, int value) {
        this.index = index;
        this.value = value;
      }
      
      // @@protoc_insertion_point(enum_scope:com.android.tools.idea.stats.LogRequest.LogSource)
    }
    
    // optional .com.android.tools.idea.stats.ClientInfo client_info = 1;
    public static final int CLIENT_INFO_FIELD_NUMBER = 1;
    private boolean hasClientInfo;
    private com.android.tools.idea.stats.StatsProto.ClientInfo clientInfo_;
    public boolean hasClientInfo() { return hasClientInfo; }
    public com.android.tools.idea.stats.StatsProto.ClientInfo getClientInfo() { return clientInfo_; }
    
    // optional .com.android.tools.idea.stats.LogRequest.LogSource log_source = 2 [default = UNKNOWN];
    public static final int LOG_SOURCE_FIELD_NUMBER = 2;
    private boolean hasLogSource;
    private com.android.tools.idea.stats.StatsProto.LogRequest.LogSource logSource_;
    public boolean hasLogSource() { return hasLogSource; }
    public com.android.tools.idea.stats.StatsProto.LogRequest.LogSource getLogSource() { return logSource_; }
    
    // repeated .com.android.tools.idea.stats.LogEvent log_event = 3;
    public static final int LOG_EVENT_FIELD_NUMBER = 3;
    private java.util.List<com.android.tools.idea.stats.StatsProto.LogEvent> logEvent_ =
      java.util.Collections.emptyList();
    public java.util.List<com.android.tools.idea.stats.StatsProto.LogEvent> getLogEventList() {
      return logEvent_;
    }
    public int getLogEventCount() { return logEvent_.size(); }
    public com.android.tools.idea.stats.StatsProto.LogEvent getLogEvent(int index) {
      return logEvent_.get(index);
    }
    
    // optional int64 request_time_ms = 4;
    public static final int REQUEST_TIME_MS_FIELD_NUMBER = 4;
    private boolean hasRequestTimeMs;
    private long requestTimeMs_ = 0L;
    public boolean hasRequestTimeMs() { return hasRequestTimeMs; }
    public long getRequestTimeMs() { return requestTimeMs_; }
    
    private void initFields() {
      clientInfo_ = com.android.tools.idea.stats.StatsProto.ClientInfo.getDefaultInstance();
      logSource_ = com.android.tools.idea.stats.StatsProto.LogRequest.LogSource.UNKNOWN;
    }
    @Override
    public final boolean isInitialized() {
      return true;
    }
    
    @Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      getSerializedSize();
      if (hasClientInfo()) {
        output.writeMessage(1, getClientInfo());
      }
      if (hasLogSource()) {
        output.writeEnum(2, getLogSource().getNumber());
      }
      for (com.android.tools.idea.stats.StatsProto.LogEvent element : getLogEventList()) {
        output.writeMessage(3, element);
      }
      if (hasRequestTimeMs()) {
        output.writeInt64(4, getRequestTimeMs());
      }
    }
    
    private int memoizedSerializedSize = -1;
    @Override
    public int getSerializedSize() {
      int size = memoizedSerializedSize;
      if (size != -1) return size;
    
      size = 0;
      if (hasClientInfo()) {
        size += com.google.protobuf.CodedOutputStream
          .computeMessageSize(1, getClientInfo());
      }
      if (hasLogSource()) {
        size += com.google.protobuf.CodedOutputStream
          .computeEnumSize(2, getLogSource().getNumber());
      }
      for (com.android.tools.idea.stats.StatsProto.LogEvent element : getLogEventList()) {
        size += com.google.protobuf.CodedOutputStream
          .computeMessageSize(3, element);
      }
      if (hasRequestTimeMs()) {
        size += com.google.protobuf.CodedOutputStream
          .computeInt64Size(4, getRequestTimeMs());
      }
      memoizedSerializedSize = size;
      return size;
    }
    
    public static com.android.tools.idea.stats.StatsProto.LogRequest parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogRequest parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogRequest parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogRequest parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogRequest parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogRequest parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input, extensionRegistry)
               .buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogRequest parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      Builder builder = newBuilder();
      if (builder.mergeDelimitedFrom(input)) {
        return builder.buildParsed();
      } else {
        return null;
      }
    }
    public static com.android.tools.idea.stats.StatsProto.LogRequest parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      Builder builder = newBuilder();
      if (builder.mergeDelimitedFrom(input, extensionRegistry)) {
        return builder.buildParsed();
      } else {
        return null;
      }
    }
    public static com.android.tools.idea.stats.StatsProto.LogRequest parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input).buildParsed();
    }
    public static com.android.tools.idea.stats.StatsProto.LogRequest parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input, extensionRegistry)
               .buildParsed();
    }
    
    public static Builder newBuilder() { return Builder.create(); }
    @Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder(com.android.tools.idea.stats.StatsProto.LogRequest prototype) {
      return newBuilder().mergeFrom(prototype);
    }
    @Override
    public Builder toBuilder() { return newBuilder(this); }
    
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageLite.Builder<
          com.android.tools.idea.stats.StatsProto.LogRequest, Builder> {
      private com.android.tools.idea.stats.StatsProto.LogRequest result;
      
      // Construct using com.android.tools.idea.stats.StatsProto.LogRequest.newBuilder()
      private Builder() {}
      
      private static Builder create() {
        Builder builder = new Builder();
        builder.result = new com.android.tools.idea.stats.StatsProto.LogRequest();
        return builder;
      }
      
      protected com.android.tools.idea.stats.StatsProto.LogRequest internalGetResult() {
        return result;
      }
      
      @Override
      public Builder clear() {
        if (result == null) {
          throw new IllegalStateException(
            "Cannot call clear() after build().");
        }
        result = new com.android.tools.idea.stats.StatsProto.LogRequest();
        return this;
      }
      
      @Override
      public Builder clone() {
        return create().mergeFrom(result);
      }
      
      @Override
      public com.android.tools.idea.stats.StatsProto.LogRequest getDefaultInstanceForType() {
        return com.android.tools.idea.stats.StatsProto.LogRequest.getDefaultInstance();
      }
      
      @Override
      public boolean isInitialized() {
        return result.isInitialized();
      }
      @Override
      public com.android.tools.idea.stats.StatsProto.LogRequest build() {
        if (result != null && !isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return buildPartial();
      }
      
      private com.android.tools.idea.stats.StatsProto.LogRequest buildParsed()
          throws com.google.protobuf.InvalidProtocolBufferException {
        if (!isInitialized()) {
          throw newUninitializedMessageException(
            result).asInvalidProtocolBufferException();
        }
        return buildPartial();
      }
      
      @Override
      public com.android.tools.idea.stats.StatsProto.LogRequest buildPartial() {
        if (result == null) {
          throw new IllegalStateException(
            "build() has already been called on this Builder.");
        }
        if (result.logEvent_ != java.util.Collections.EMPTY_LIST) {
          result.logEvent_ =
            java.util.Collections.unmodifiableList(result.logEvent_);
        }
        com.android.tools.idea.stats.StatsProto.LogRequest returnMe = result;
        result = null;
        return returnMe;
      }
      
      @Override
      public Builder mergeFrom(com.android.tools.idea.stats.StatsProto.LogRequest other) {
        if (other == com.android.tools.idea.stats.StatsProto.LogRequest.getDefaultInstance()) return this;
        if (other.hasClientInfo()) {
          mergeClientInfo(other.getClientInfo());
        }
        if (other.hasLogSource()) {
          setLogSource(other.getLogSource());
        }
        if (!other.logEvent_.isEmpty()) {
          if (result.logEvent_.isEmpty()) {
            result.logEvent_ = new java.util.ArrayList<com.android.tools.idea.stats.StatsProto.LogEvent>();
          }
          result.logEvent_.addAll(other.logEvent_);
        }
        if (other.hasRequestTimeMs()) {
          setRequestTimeMs(other.getRequestTimeMs());
        }
        return this;
      }
      
      @Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        while (true) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              return this;
            default: {
              if (!parseUnknownField(input, extensionRegistry, tag)) {
                return this;
              }
              break;
            }
            case 10: {
              com.android.tools.idea.stats.StatsProto.ClientInfo.Builder subBuilder = com.android.tools.idea.stats.StatsProto.ClientInfo.newBuilder();
              if (hasClientInfo()) {
                subBuilder.mergeFrom(getClientInfo());
              }
              input.readMessage(subBuilder, extensionRegistry);
              setClientInfo(subBuilder.buildPartial());
              break;
            }
            case 16: {
              int rawValue = input.readEnum();
              com.android.tools.idea.stats.StatsProto.LogRequest.LogSource value = com.android.tools.idea.stats.StatsProto.LogRequest.LogSource.valueOf(rawValue);
              if (value != null) {
                setLogSource(value);
              }
              break;
            }
            case 26: {
              com.android.tools.idea.stats.StatsProto.LogEvent.Builder subBuilder = com.android.tools.idea.stats.StatsProto.LogEvent.newBuilder();
              input.readMessage(subBuilder, extensionRegistry);
              addLogEvent(subBuilder.buildPartial());
              break;
            }
            case 32: {
              setRequestTimeMs(input.readInt64());
              break;
            }
          }
        }
      }
      
      
      // optional .com.android.tools.idea.stats.ClientInfo client_info = 1;
      public boolean hasClientInfo() {
        return result.hasClientInfo();
      }
      public com.android.tools.idea.stats.StatsProto.ClientInfo getClientInfo() {
        return result.getClientInfo();
      }
      public Builder setClientInfo(com.android.tools.idea.stats.StatsProto.ClientInfo value) {
        if (value == null) {
          throw new NullPointerException();
        }
        result.hasClientInfo = true;
        result.clientInfo_ = value;
        return this;
      }
      public Builder setClientInfo(com.android.tools.idea.stats.StatsProto.ClientInfo.Builder builderForValue) {
        result.hasClientInfo = true;
        result.clientInfo_ = builderForValue.build();
        return this;
      }
      public Builder mergeClientInfo(com.android.tools.idea.stats.StatsProto.ClientInfo value) {
        if (result.hasClientInfo() &&
            result.clientInfo_ != com.android.tools.idea.stats.StatsProto.ClientInfo.getDefaultInstance()) {
          result.clientInfo_ =
            com.android.tools.idea.stats.StatsProto.ClientInfo.newBuilder(result.clientInfo_).mergeFrom(value).buildPartial();
        } else {
          result.clientInfo_ = value;
        }
        result.hasClientInfo = true;
        return this;
      }
      public Builder clearClientInfo() {
        result.hasClientInfo = false;
        result.clientInfo_ = com.android.tools.idea.stats.StatsProto.ClientInfo.getDefaultInstance();
        return this;
      }
      
      // optional .com.android.tools.idea.stats.LogRequest.LogSource log_source = 2 [default = UNKNOWN];
      public boolean hasLogSource() {
        return result.hasLogSource();
      }
      public com.android.tools.idea.stats.StatsProto.LogRequest.LogSource getLogSource() {
        return result.getLogSource();
      }
      public Builder setLogSource(com.android.tools.idea.stats.StatsProto.LogRequest.LogSource value) {
        if (value == null) {
          throw new NullPointerException();
        }
        result.hasLogSource = true;
        result.logSource_ = value;
        return this;
      }
      public Builder clearLogSource() {
        result.hasLogSource = false;
        result.logSource_ = com.android.tools.idea.stats.StatsProto.LogRequest.LogSource.UNKNOWN;
        return this;
      }
      
      // repeated .com.android.tools.idea.stats.LogEvent log_event = 3;
      public java.util.List<com.android.tools.idea.stats.StatsProto.LogEvent> getLogEventList() {
        return java.util.Collections.unmodifiableList(result.logEvent_);
      }
      public int getLogEventCount() {
        return result.getLogEventCount();
      }
      public com.android.tools.idea.stats.StatsProto.LogEvent getLogEvent(int index) {
        return result.getLogEvent(index);
      }
      public Builder setLogEvent(int index, com.android.tools.idea.stats.StatsProto.LogEvent value) {
        if (value == null) {
          throw new NullPointerException();
        }
        result.logEvent_.set(index, value);
        return this;
      }
      public Builder setLogEvent(int index, com.android.tools.idea.stats.StatsProto.LogEvent.Builder builderForValue) {
        result.logEvent_.set(index, builderForValue.build());
        return this;
      }
      public Builder addLogEvent(com.android.tools.idea.stats.StatsProto.LogEvent value) {
        if (value == null) {
          throw new NullPointerException();
        }
        if (result.logEvent_.isEmpty()) {
          result.logEvent_ = new java.util.ArrayList<com.android.tools.idea.stats.StatsProto.LogEvent>();
        }
        result.logEvent_.add(value);
        return this;
      }
      public Builder addLogEvent(com.android.tools.idea.stats.StatsProto.LogEvent.Builder builderForValue) {
        if (result.logEvent_.isEmpty()) {
          result.logEvent_ = new java.util.ArrayList<com.android.tools.idea.stats.StatsProto.LogEvent>();
        }
        result.logEvent_.add(builderForValue.build());
        return this;
      }
      public Builder addAllLogEvent(
          java.lang.Iterable<? extends com.android.tools.idea.stats.StatsProto.LogEvent> values) {
        if (result.logEvent_.isEmpty()) {
          result.logEvent_ = new java.util.ArrayList<com.android.tools.idea.stats.StatsProto.LogEvent>();
        }
        super.addAll(values, result.logEvent_);
        return this;
      }
      public Builder clearLogEvent() {
        result.logEvent_ = java.util.Collections.emptyList();
        return this;
      }
      
      // optional int64 request_time_ms = 4;
      public boolean hasRequestTimeMs() {
        return result.hasRequestTimeMs();
      }
      public long getRequestTimeMs() {
        return result.getRequestTimeMs();
      }
      public Builder setRequestTimeMs(long value) {
        result.hasRequestTimeMs = true;
        result.requestTimeMs_ = value;
        return this;
      }
      public Builder clearRequestTimeMs() {
        result.hasRequestTimeMs = false;
        result.requestTimeMs_ = 0L;
        return this;
      }
      
      // @@protoc_insertion_point(builder_scope:com.android.tools.idea.stats.LogRequest)
    }
    
    static {
      defaultInstance = new LogRequest(true);
      com.android.tools.idea.stats.StatsProto.internalForceInit();
      defaultInstance.initFields();
    }
    
    // @@protoc_insertion_point(class_scope:com.android.tools.idea.stats.LogRequest)
  }
  
  
  static {
  }
  
  public static void internalForceInit() {}
  
  // @@protoc_insertion_point(outer_class_scope)
}
