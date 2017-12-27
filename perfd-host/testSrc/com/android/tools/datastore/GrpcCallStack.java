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
package com.android.tools.datastore;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import java.util.ArrayList;
import java.util.List;

@XmlSeeAlso(GrpcCall.class)
@XmlRootElement(name="GrpcList")

/**
 * Container for Grpc calls, this class is the top level node to be serialized for test.
 */
public class GrpcCallStack {
  @XmlElement(name = "GrpcCall", required = true)
  protected List<GrpcCall> myGrpcCalls;

  public List<GrpcCall> getGrpcCalls() {
    return myGrpcCalls;
  }

  public GrpcCallStack() {
    myGrpcCalls = new ArrayList<>();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof GrpcCallStack) {
      return myGrpcCalls.equals(((GrpcCallStack)obj).getGrpcCalls());
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(GrpcCallStack.class.toString());
    for(GrpcCall call : myGrpcCalls) {
      builder.append(String.format("\t%s\n",call));
    }
    return builder.toString();
  }
}
