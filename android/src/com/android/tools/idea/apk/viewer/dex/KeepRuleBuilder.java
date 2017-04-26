/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer.dex;

public class KeepRuleBuilder {

  public enum KeepType {
    KEEP, KEEPCLASSMEMBERS, KEEPNAMES, KEEPCLASSMEMBERNAMES
  }

  public static String ANY_CLASS = "**";
  public static String ANY_MEMBER = "*";

  private String myPackage;
  private String myClass;
  private String myMember;

  public KeepRuleBuilder setPackage(String aPackage) {
    myPackage = aPackage;
    return this;
  }

  public KeepRuleBuilder setClass(String aClass) {
    myClass = aClass;
    return this;
  }

  public KeepRuleBuilder setMember(String member) {
    myMember = member;
    return this;
  }

  public String build(KeepType keepType) {
    if (myPackage == null){
      throw new IllegalStateException("You must set a package.");
    }
    if (myClass == null) {
      myClass = ANY_CLASS;
    }
    if (myMember == null) {
      myMember = ANY_MEMBER;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("-");
    sb.append(keepType.toString().toLowerCase());
    sb.append(" class ");

    if (!myPackage.isEmpty()){
      sb.append(myPackage);
      sb.append(".");
    }
    sb.append(myClass);
    sb.append(" { ");
    sb.append(myMember);
    sb.append("; }");
    return sb.toString();
  }
}
