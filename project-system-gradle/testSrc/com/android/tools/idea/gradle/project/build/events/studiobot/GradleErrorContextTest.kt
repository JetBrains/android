/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.events.studiobot

import com.android.tools.idea.gemini.formatForTests
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock

/**
 * Tests for [GradleErrorContext]
 */
@RunWith(JUnit4::class)
class GradleErrorContextTest: BasePlatformTestCase() {

  @Test
  fun toQuery_addsContextWithAllTheDetails() {
    val context = GradleErrorContext(
      source = GradleErrorContext.Source.BUILD,
      gradleTask = ":app:packageDebugResources",
      fullErrorDetails =
      """
  org.xml.sax.SAXParseException; systemId: file:/Users/shiree/AndroidStudioProjects/330441116/app/src/main/res/layout/nav_header_main.xml; lineNumber: 13; columnNumber: 1; Element type "LinearLayout" must be followed by either attribute specifications, ">" or "/>".
    at java.xml/com.sun.org.apache.xerces.internal.parsers.DOMParser.parse(DOMParser.java:262)
    at java.xml/com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderImpl.parse(DocumentBuilderImpl.java:342)
  """.trimIndent(),
      errorMessage = "Element type \"LinearLayout\" must be followed by either attribute specifications, \">\" or \"/>\".",
    )

    assertThat(context.toQuery()).isEqualTo("""
I'm getting the following error while building my project. The error is: Element type "LinearLayout" must be followed by either attribute specifications, ">" or "/>".
```
$ ./gradlew :app:packageDebugResources
org.xml.sax.SAXParseException; systemId: file:/Users/shiree/AndroidStudioProjects/330441116/app/src/main/res/layout/nav_header_main.xml; lineNumber: 13; columnNumber: 1; Element type "LinearLayout" must be followed by either attribute specifications, ">" or "/>".
  at java.xml/com.sun.org.apache.xerces.internal.parsers.DOMParser.parse(DOMParser.java:262)
  at java.xml/com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderImpl.parse(DocumentBuilderImpl.java:342)
```
How do I fix this?
      """.trimIndent()
    )
  }

  @Test
  fun toPrompt_addsContextWithAllTheDetails() {
    val context = GradleErrorContext(
      source = GradleErrorContext.Source.BUILD,
      gradleTask = ":app:packageDebugResources",
      fullErrorDetails =
      """
  org.xml.sax.SAXParseException; systemId: file:/Users/shiree/AndroidStudioProjects/330441116/app/src/main/res/layout/nav_header_main.xml; lineNumber: 13; columnNumber: 1; Element type "LinearLayout" must be followed by either attribute specifications, ">" or "/>".
    at java.xml/com.sun.org.apache.xerces.internal.parsers.DOMParser.parse(DOMParser.java:262)
    at java.xml/com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderImpl.parse(DocumentBuilderImpl.java:342)
  """.trimIndent(),
      errorMessage = null,
    )

    assertThat(context.toPrompt(project).formatForTests())
      .isEqualTo(
              """
USER
I'm getting the following error while building my project.
```
$ ./gradlew :app:packageDebugResources
org.xml.sax.SAXParseException; systemId: file:/Users/shiree/AndroidStudioProjects/330441116/app/src/main/res/layout/nav_header_main.xml; lineNumber: 13; columnNumber: 1; Element type "LinearLayout" must be followed by either attribute specifications, ">" or "/>".
  at java.xml/com.sun.org.apache.xerces.internal.parsers.DOMParser.parse(DOMParser.java:262)
  at java.xml/com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderImpl.parse(DocumentBuilderImpl.java:342)
```
How do I fix this?
            """.trimIndent())
  }


  @Test
  fun toQuery_trimsStackTrace_toFirstTenLines_andFiveLinesOfRootCause() {
    val context = GradleErrorContext(
      fullErrorDetails =
      """
javax.servlet.ServletException: Servlet exception
    at com.example.myproject.OpenSessionInViewFilter.doFilter(OpenSessionInViewFilter.java:60)
    at org.mortbay.jetty.servlet.ServletHandler${'$'}CachedChain.doFilter(ServletHandler.java:1157)
    at com.example.myproject.ExceptionHandlerFilter.doFilter(ExceptionHandlerFilter.java:28)
    at org.mortbay.jetty.servlet.ServletHandler${'$'}CachedChain.doFilter(ServletHandler.java:1157)
    at com.example.myproject.OutputBufferFilter.doFilter(OutputBufferFilter.java:33)
    at org.mortbay.jetty.servlet.ServletHandler${'$'}CachedChain.doFilter(ServletHandler.java:1157)
    at org.mortbay.jetty.servlet.ServletHandler.handle(ServletHandler.java:388)
    at org.mortbay.jetty.security.SecurityHandler.handle(SecurityHandler.java:216)
    at org.mortbay.jetty.servlet.SessionHandler.handle(SessionHandler.java:182)
    at org.mortbay.jetty.handler.ContextHandler.handle(ContextHandler.java:765)
    at org.mortbay.jetty.webapp.WebAppContext.handle(WebAppContext.java:418)
    at org.mortbay.jetty.handler.HandlerWrapper.handle(HandlerWrapper.java:152)
    at org.mortbay.jetty.Server.handle(Server.java:326)
    at org.mortbay.jetty.HttpConnection.handleRequest(HttpConnection.java:542)
    at org.mortbay.jetty.HttpConnection${'$'}RequestHandler.content(HttpConnection.java:943)
    at org.mortbay.jetty.HttpParser.parseNext(HttpParser.java:756)
    at org.mortbay.jetty.HttpParser.parseAvailable(HttpParser.java:218)
    at org.mortbay.jetty.HttpConnection.handle(HttpConnection.java:404)
    at org.mortbay.jetty.bio.SocketConnector${'$'}Connection.run(SocketConnector.java:228)
    at org.mortbay.thread.QueuedThreadPool${'$'}PoolThread.run(QueuedThreadPool.java:582)
Caused by: com.example.myproject.MyProjectServletException
    at com.example.myproject.MyServlet.doPost(MyServlet.java:169)
    at javax.servlet.http.HttpServlet.service(HttpServlet.java:727)
    at javax.servlet.http.HttpServlet.service(HttpServlet.java:820)
    at org.mortbay.jetty.servlet.ServletHolder.handle(ServletHolder.java:511)
    at org.mortbay.jetty.servlet.ServletHandler${'$'}CachedChain.doFilter(ServletHandler.java:1166)
    at com.example.myproject.OpenSessionInViewFilter.doFilter(OpenSessionInViewFilter.java:30)
    ... 27 more
Caused by: org.hibernate.exception.ConstraintViolationException: could not update: [com.example.myproject.MyEntity]
    at org.hibernate.exception.SQLStateConverter.convert(SQLStateConverter.java:96)
    at org.hibernate.exception.JDBCExceptionHelper.convert(JDBCExceptionHelper.java:66)
    at org.hibernate.id.insert.AbstractSelectingDelegate.performInsert(AbstractSelectingDelegate.java:64)
    at org.hibernate.persister.entity.AbstractEntityPersister.insert(AbstractEntityPersister.java:2329)
    at org.hibernate.persister.entity.AbstractEntityPersister.insert(AbstractEntityPersister.java:2822)
    at org.hibernate.action.EntityIdentityInsertAction.execute(EntityIdentityInsertAction.java:71)
    at org.hibernate.engine.ActionQueue.execute(ActionQueue.java:268)
    at org.hibernate.event.def.AbstractSaveEventListener.performSaveOrReplicate(AbstractSaveEventListener.java:321)
    at org.hibernate.event.def.AbstractSaveEventListener.performSave(AbstractSaveEventListener.java:204)
    at org.hibernate.event.def.AbstractSaveEventListener.saveWithGeneratedId(AbstractSaveEventListener.java:130)
    at org.hibernate.event.def.DefaultSaveOrUpdateEventListener.saveWithGeneratedOrRequestedId(DefaultSaveOrUpdateEventListener.java:210)
    at org.hibernate.event.def.DefaultSaveEventListener.saveWithGeneratedOrRequestedId(DefaultSaveEventListener.java:56)
    at org.hibernate.event.def.DefaultSaveOrUpdateEventListener.entityIsTransient(DefaultSaveOrUpdateEventListener.java:195)
    at org.hibernate.event.def.DefaultSaveEventListener.performSaveOrUpdate(DefaultSaveEventListener.java:50)
    at org.hibernate.event.def.DefaultSaveOrUpdateEventListener.onSaveOrUpdate(DefaultSaveOrUpdateEventListener.java:93)
    at org.hibernate.impl.SessionImpl.fireSave(SessionImpl.java:705)
    at org.hibernate.impl.SessionImpl.save(SessionImpl.java:693)
    at org.hibernate.impl.SessionImpl.save(SessionImpl.java:689)
    at sun.reflect.GeneratedMethodAccessor5.invoke(Unknown Source)
    at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)
    at java.lang.reflect.Method.invoke(Method.java:597)
    at org.hibernate.context.ThreadLocalSessionContext${'$'}TransactionProtectionWrapper.invoke(ThreadLocalSessionContext.java:344)
    at ${'$'}Proxy19.save(Unknown Source)
    at com.example.myproject.MyEntityService.save(MyEntityService.java:59) <-- relevant call (see notes below)
    at com.example.myproject.MyServlet.doPost(MyServlet.java:164)
    ... 32 more
Caused by: java.sql.BatchUpdateException: Duplicate key or integrity constraint violation,  message from server: "Cannot add or update a child row: a foreign key constraint fails"
    at com.mysql.jdbc.PreparedStatement.executeBatch(PreparedStatement.java:1461)
    at com.mchange.v2.c3p0.impl.NewProxyPreparedStatement.executeBatch(NewProxyPreparedStatement.java:1723)
    at org.hibernate.jdbc.BatchingBatcher.doExecuteBatch(BatchingBatcher.java:48)
    at org.hibernate.jdbc.BatchingBatcher.addToBatch(BatchingBatcher.java:34)
    at org.hibernate.persister.entity.AbstractEntityPersister.update(AbstractEntityPersister.java:2408)
    ... 29 more
  """.trimIndent(),
      gradleTask = null,
      errorMessage = null,
      source = null,
    )

    assertThat(context.toQuery()).isEqualTo("""
I'm getting the following error in my project.
```
javax.servlet.ServletException: Servlet exception
    at com.example.myproject.OpenSessionInViewFilter.doFilter(OpenSessionInViewFilter.java:60)
    at org.mortbay.jetty.servlet.ServletHandler${'$'}CachedChain.doFilter(ServletHandler.java:1157)
    at com.example.myproject.ExceptionHandlerFilter.doFilter(ExceptionHandlerFilter.java:28)
    at org.mortbay.jetty.servlet.ServletHandler${'$'}CachedChain.doFilter(ServletHandler.java:1157)
    at com.example.myproject.OutputBufferFilter.doFilter(OutputBufferFilter.java:33)
    at org.mortbay.jetty.servlet.ServletHandler${'$'}CachedChain.doFilter(ServletHandler.java:1157)
    at org.mortbay.jetty.servlet.ServletHandler.handle(ServletHandler.java:388)
    at org.mortbay.jetty.security.SecurityHandler.handle(SecurityHandler.java:216)
    at org.mortbay.jetty.servlet.SessionHandler.handle(SessionHandler.java:182)
...
Caused by: java.sql.BatchUpdateException: Duplicate key or integrity constraint violation,  message from server: "Cannot add or update a child row: a foreign key constraint fails"
    at com.mysql.jdbc.PreparedStatement.executeBatch(PreparedStatement.java:1461)
    at com.mchange.v2.c3p0.impl.NewProxyPreparedStatement.executeBatch(NewProxyPreparedStatement.java:1723)
    at org.hibernate.jdbc.BatchingBatcher.doExecuteBatch(BatchingBatcher.java:48)
    at org.hibernate.jdbc.BatchingBatcher.addToBatch(BatchingBatcher.java:34)
```
How do I fix this?
      """.trimIndent()
    )
  }
}
