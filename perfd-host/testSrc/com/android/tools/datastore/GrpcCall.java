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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 * GrpcCall object that represents the class, message, and function intercepted.
 */
public class GrpcCall {

  @XmlElement(name = "Message", required = true)
  protected String myMessage;
  @XmlAttribute(name = "FunctionCalled")
  protected String myFunctionCalled;
  @XmlAttribute(name = "Class")
  protected String myClazz;

  /**
   * Gets the value of the message property.
   *
   * @return
   *     possible object is
   *     {@link String }
   *
   */
  public String getMessage() {
    return myMessage;
  }

  /**
   * Sets the message property. The message should be the raw rpc message format as string.
   *
   * @param value
   *     allowed object is
   *     {@link String }
   *
   */
  public void setMessage(String value) {
    this.myMessage = value;
  }

  /**
   * Gets the function called on the rpc.
   *
   * @return
   *     possible object is
   *     {@link String }
   *
   */
  public String getFunctionCalled() {
    return myFunctionCalled;
  }

  /**
   * Sets the function called on the rpc.
   *
   * @param value
   *     allowed object is
   *     {@link String }
   *
   */
  public void setFunctionCalled(String value) {
    this.myFunctionCalled = value;
  }

  /**
   * Gets the class name.
   *
   * @return
   *     possible object is
   *     {@link String }
   *
   */
  public String getClazz() {
    return myClazz;
  }

  /**
   * Sets the class name of the calling rpc.
   *
   * @param value
   *     allowed object is
   *     {@link String }
   *
   */
  public void setClazz(String value) {
    this.myClazz = value;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof GrpcCall) {
      GrpcCall other = (GrpcCall)obj;
      return myMessage.equals(other.myMessage) &&
             myClazz.equals(other.myClazz) &&
             myFunctionCalled.equals(other.myFunctionCalled);
    }
    return false;
  }

  @Override
  public String toString() {
    return String.format("%s\n%s\n%s", myClazz, myFunctionCalled, myMessage);
  }
}
