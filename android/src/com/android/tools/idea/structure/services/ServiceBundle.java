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
package com.android.tools.idea.structure.services;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.*;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Reader;
import java.util.List;

/**
 * JAXB enabled POJO representing a group of {@code Services}. Can be used by plugins to define a grouping of related
 * {@code DeveloperServiceCreators} in an XML file.
 */
@XmlRootElement(name = "servicebundle")
public class ServiceBundle {
  // @formatter:off
  @XmlAttribute(name="name")
  @SuppressWarnings("unused")
  private String myName;

  @XmlElements({
    @XmlElement(name = "service", type = Service.class)
  })
  private List<Service> myServices = Lists.newArrayList();
  // @formatter:on

  /**
   * Parse a bundle XML.
   */
  public static ServiceBundle parse(@NotNull Reader xmlReader) throws JAXBException {
    return unmarshal(xmlReader);
  }

  private static ServiceBundle unmarshal(@NotNull Reader xmlReader) throws JAXBException {
    Unmarshaller unmarshaller = JAXBContext.newInstance(ServiceBundle.class).createUnmarshaller();
    unmarshaller.setEventHandler(new ValidationEventHandler() {
      @Override
      public boolean handleEvent(ValidationEvent event) {
        throw new RuntimeException(event.getLinkedException());
      }
    });
    return (ServiceBundle)unmarshaller.unmarshal(xmlReader);
  }

  public List<Service> getServices() {
    return ImmutableList.copyOf(myServices);
  }

  @Override
  public String toString() {
    return "ServiceBundle{" +
           "myName='" + myName + '\'' +
           ", myServices=" + myServices +
           '}';
  }

  /**
   * Individual service configuration declaration that represents a {@link DeveloperServiceCreator} that will be created within a
   * {@code ServiceBundle}.
   */
  @SuppressWarnings({"NullableProblems", "unused"})
  public static final class Service {
    // @formatter:off
    @XmlAttribute(name="name")
    @NotNull
    @SuppressWarnings("unused")
    private String myName;

    @XmlAttribute(name="resourceRoot")
    @NotNull
    @SuppressWarnings("unused")
    private String myResourceRoot;

    @XmlElements({
      @XmlElement(name = "resource", type = String.class)
    })
    private List<String> myResources = Lists.newArrayList();
    // @formatter:on

    public List<String> getResources() {
      return ImmutableList.copyOf(myResources);
    }

    public String getResourceRoot() {
      return myResourceRoot;
    }

    @Override
    public String toString() {
      return "Service{" +
             "myName='" + myName + '\'' +
             ", myResourceRoot='" + myResourceRoot + '\'' +
             ", myResources=" + myResources +
             '}';
    }
  }
}
