/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.logcat.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests for LogcatStudioBot.kt */
class LogcatStudioBotTest {
  @Test
  fun testExtractCrash() {
    assertThat(
        logcatMessage(
            tag = "MyTag",
            message =
              "Message is Long live credential not available.\n" +
                "zkj: Long live credential not available.\n" +
                "\tat lfk.a(:com.google.android.gms@225014044@22.50.14 (190400-499278674):8)\n" +
                "\tat lfq.c(:com.google.android.gms@225014044@22.50.14 (190400-499278674):3)\n" +
                "\tat ldx.l(:com.google.android.gms@225014044@22.50.14 (190400-499278674):47)\n" +
                "\tat lcz.a(:com.google.android.gms@225014044@22.50.14 (190400-499278674):24)\n" +
                "\tat lde.a(:com.google.android.gms@225014044@22.50.14 (190400-499278674):2)\n" +
                "\tat lgd.b(:com.google.android.gms@225014044@22.50.14 (190400-499278674):3)\n" +
                "\tat lgd.a(:com.google.android.gms@225014044@22.50.14 (190400-499278674):7)\n" +
                "\tat lcg.hasFeatures(:com.google.android.gms@225014044@22.50.14 (190400-499278674):2)\n" +
                "\tat android.accounts.AbstractAccountAuthenticator\$Transport.hasFeatures(AbstractAccountAuthenticator.java:313)\n" +
                "\tat android.accounts.IAccountAuthenticator\$Stub.onTransact(IAccountAuthenticator.java:288)\n" +
                "\tat android.os.Binder.transact(Binder.java:1164)\n" +
                "\tat arlh.onTransact(:com.google.android.gms@225014044@22.50.14 (190400-499278674):1)\n" +
                "\tat android.os.Binder.transact(Binder.java:1164)\n" +
                "\tat alqf.onTransact(:com.google.android.gms@225014044@22.50.14 (190400-499278674):17)\n" +
                "\tat android.os.Binder.execTransactInternal(Binder.java:1285)\n" +
                "\tat android.os.Binder.execTransact(Binder.java:1244)\n",
          )
          .extractStudioBotContent()
      )
      .isEqualTo(
        """
            Message is Long live credential not available.
            zkj: Long live credential not available.
            at lfk.a(:com.google.android.gms@225014044@22.50.14 (190400-499278674):8)
            at lfq.c(:com.google.android.gms@225014044@22.50.14 (190400-499278674):3)
            at ldx.l(:com.google.android.gms@225014044@22.50.14 (190400-499278674):47) with tag MyTag
        """
          .trimIndent()
      )
  }

  @Test
  fun testExtractPlain() {
    assertThat(
        logcatMessage(
            tag = "MyTag",
            message = "Compat change id reported: 183155436; UID 10111; state: ENABLED\n",
          )
          .extractStudioBotContent()
      )
      .isEqualTo("Compat change id reported: 183155436; UID 10111; state: ENABLED with tag MyTag")
  }

  @Test
  fun testExtractCrashWithCausedBy() {
    assertThat(
        logcatMessage(
            tag = "MyTag",
            message =
              "Could not register for notifications with InnerTube: \n" +
                "xvd: dlj: java.lang.IllegalStateException: DefaultAccountIdResolver could not resolve pseudonymous, pseudonymous\n" +
                "\tat xut.apply(Unknown Source:4)\n" +
                "\tat vws.p(PG:5)\n" +
                "\tat vws.b(PG:2)\n" +
                "\tat xuv.d(PG:4)\n" +
                "\tat acvk.c(PG:51)\n" +
                "\tat acvk.j(PG:38)\n" +
                "\tat acvk.d(PG:3)\n" +
                "\tat acvi.run(Unknown Source:2)\n" +
                "\tat java.util.concurrent.Executors\$RunnableAdapter.call(Executors.java:463)\n" +
                "\tat java.util.concurrent.FutureTask.run(FutureTask.java:264)\n" +
                "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1137)\n" +
                "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:637)\n" +
                "\tat qhq.run(PG:2)\n" +
                "\tat qic.run(PG:4)\n" +
                "\tat java.lang.Thread.run(Thread.java:1012)\n" +
                "Caused by: dlj: java.lang.IllegalStateException: DefaultAccountIdResolver could not resolve pseudonymous, pseudonymous\n" +
                "\tat wfa.b(PG:1)\n" +
                "\tat wfa.run(PG:24)\n" +
                "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1137)\n" +
                "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:637)\n" +
                "\tat vvz.run(PG:11)\n" +
                "\tat java.lang.Thread.run(Thread.java:1012) \n" +
                "Caused by: java.lang.IllegalStateException: DefaultAccountIdResolver could not resolve pseudonymous, pseudonymous\n" +
                "\tat ucg.a(PG:7)\n" +
                "\tat xsd.a(PG:1)\n" +
                "\tat xtk.G(PG:5)\n" +
                "\tat xtk.d(PG:2)\n" +
                "\tat wfa.a(PG:15)\n" +
                "\tat wfa.run(PG:21)\n" +
                "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1137) \n" +
                "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:637) \n" +
                "\tat vvz.run(PG:11) \n" +
                "\tat java.lang.Thread.run(Thread.java:1012) \n" +
                "Caused by: java.util.concurrent.ExecutionException: aiwk: account of type pseudonymous is not enabled\n" +
                "\tat android.app.ActivityThread.main(ActivityThread.java:6440)\n" +
                "\tat akmv.get(PG:31)\n" +
                "\tat akmq.get(PG:2)\n" +
                "\tat ucg.a(PG:4)\n" +
                "\tat xsd.a(PG:1) \n" +
                "\tat xtk.G(PG:5) \n" +
                "\tat xtk.d(PG:2) \n" +
                "\tat wfa.a(PG:15) \n" +
                "\tat wfa.run(PG:21) \n" +
                "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1137) \n" +
                "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:637) \n" +
                "\tat vvz.run(PG:11) \n" +
                "\tat java.lang.Thread.run(Thread.java:1012) \n" +
                "Caused by: aiwk: account of type pseudonymous is not enabled\n" +
                "\tat aixz.apply(PG:6)\n" +
                "\tat ajls.apply(PG:2)\n" +
                "\tat akmy.c(PG:2)\n" +
                "\tat akmz.run(PG:9)\n" +
                "\tat akod.execute(PG:1)\n" +
                "\tat akmv.m(PG:1)\n" +
                "\tat akmv.i(PG:12)\n" +
                "\tat akmv.setFuture(PG:5)\n" +
                "\tat akpf.run(PG:1)\n" +
                "\tat akod.execute(PG:1)\n" +
                "\tat akmv.m(PG:1)\n" +
                "\tat akmv.i(PG:12)\n" +
                "\tat akmv.setFuture(PG:5)\n" +
                "\tat akmx.d(PG:2)\n" +
                "\tat akmz.run(PG:12)\n" +
                "\tat akod.execute(PG:1)\n" +
                "\tat akmv.m(PG:1)\n" +
                "\tat akmv.i(PG:12)\n" +
                "\tat akmv.setFuture(PG:5)\n" +
                "\tat aizz.setFuture(PG:1)\n" +
                "\tat aizv.run(PG:4)\n" +
                "\tat akod.execute(PG:1)\n" +
                "\tat akmv.m(PG:1)\n" +
                "\tat akmv.i(PG:12)\n" +
                "\tat akmv.set(PG:2)\n" +
                "\tat akoa.c(PG:1)\n" +
                "\tat akob.e(PG:2)\n" +
                "\tat akpo.run(PG:8)\n" +
                "\tat akod.execute(PG:1)\n" +
                "\tat akob.f(PG:1)\n" +
                "\tat akoc.m(PG:1)\n" +
                "\tat aknc.f(PG:6)\n" +
                "\tat akna.run(PG:1)\n" +
                "\tat akod.execute(PG:1)\n" +
                "\tat akmv.m(PG:1)\n" +
                "\tat akmv.i(PG:12)\n" +
                "\tat akmv.setFuture(PG:5)\n" +
                "\tat akmx.d(PG:2)\n" +
                "\tat akmz.run(PG:12)\n" +
                "\tat akod.execute(PG:1)\n" +
                "\tat akmv.m(PG:1)\n" +
                "\tat akmv.i(PG:12)\n" +
                "\tat akmv.setFuture(PG:5)\n" +
                "\tat akpf.run(PG:1)\n" +
                "\tat akod.execute(PG:1)\n" +
                "\tat akmv.m(PG:1)\n" +
                "\tat akmv.i(PG:12)\n" +
                "\tat akmv.setFuture(PG:5)\n" +
                "\tat akmx.d(PG:2)\n" +
                "\tat akmz.run(PG:12)\n" +
                "\tat akod.execute(PG:1)\n" +
                "\tat akmv.m(PG:1)\n" +
                "\tat akmv.i(PG:12)\n" +
                "\tat akmv.setFuture(PG:5)\n" +
                "\tat aknz.c(PG:2)\n" +
                "\tat akob.e(PG:2)\n" +
                "\tat akpo.run(PG:8)\n" +
                "\tat qiq.run(PG:1)\n" +
                "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1137)\n" +
                "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:637)\n" +
                "\tat qhq.run(PG:2)\n" +
                "\tat qic.run(PG:4)\n" +
                "\tat java.lang.Thread.run(Thread.java:1012) \n",
          )
          .extractStudioBotContent()
      )
      .isEqualTo(
        """
        java.util.concurrent.ExecutionException: aiwk: account of type pseudonymous is not enabled
        at android.app.ActivityThread.main(ActivityThread.java:6440)
        at akmv.get(PG:31)
        at akmq.get(PG:2)
        at ucg.a(PG:4) with tag MyTag
      """
          .trimIndent()
      )
  }

  @Test
  fun testExtractingNested() {
    // For internet permission, I probably want the outermost one!
    assertThat(
        logcatMessage(
            tag = "MyTag",
            message =
              "FATAL EXCEPTION: OkHttp Dispatcher\n" +
                "Process: com.example.astrobin, PID: 28020\n" +
                "java.lang.SecurityException: Permission denied (missing INTERNET permission?)\n" +
                "\tat java.net.Inet6AddressImpl.lookupHostByName(Inet6AddressImpl.java:150)\n" +
                "\tat java.net.Inet6AddressImpl.lookupAllHostAddr(Inet6AddressImpl.java:103)\n" +
                "\tat java.net.InetAddress.getAllByName(InetAddress.java:1152)\n" +
                "\tat okhttp3.Dns\$Companion\$DnsSystem.lookup(Dns.kt:49)\n" +
                "\tat okhttp3.internal.connection.RouteSelector.resetNextInetSocketAddress(RouteSelector.kt:164)\n" +
                "\tat okhttp3.internal.connection.RouteSelector.nextProxy(RouteSelector.kt:129)\n" +
                "\tat okhttp3.internal.connection.RouteSelector.next(RouteSelector.kt:71)\n" +
                "\tat okhttp3.internal.connection.ExchangeFinder.findConnection(ExchangeFinder.kt:205)\n" +
                "\tat okhttp3.internal.connection.ExchangeFinder.findHealthyConnection(ExchangeFinder.kt:106)\n" +
                "\tat okhttp3.internal.connection.ExchangeFinder.find(ExchangeFinder.kt:74)\n" +
                "\tat okhttp3.internal.connection.RealCall.initExchange\$okhttp(RealCall.kt:255)\n" +
                "\tat okhttp3.internal.connection.ConnectInterceptor.intercept(ConnectInterceptor.kt:32)\n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109)\n" +
                "\tat okhttp3.internal.cache.CacheInterceptor.intercept(CacheInterceptor.kt:95)\n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109)\n" +
                "\tat okhttp3.internal.http.BridgeInterceptor.intercept(BridgeInterceptor.kt:83)\n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109)\n" +
                "\tat okhttp3.internal.http.RetryAndFollowUpInterceptor.intercept(RetryAndFollowUpInterceptor.kt:76)\n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109)\n" +
                "\tat okhttp3.logging.HttpLoggingInterceptor.intercept(HttpLoggingInterceptor.kt:221)\n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109)\n" +
                "\tat com.example.astrobin.api.AuthenticationInterceptor.intercept(Authentication.kt:86)\n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109)\n" +
                "\tat com.example.astrobin.api.AstrobinComponent\$baseOkHttpClient\\$\$inlined\\$-addInterceptor\\$1.intercept(OkHttpClient.kt:1080)\n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109)\n" +
                "\tat okhttp3.internal.connection.RealCall.getResponseWithInterceptorChain\$okhttp(RealCall.kt:201)\n" +
                "\tat okhttp3.internal.connection.RealCall\$AsyncCall.run(RealCall.kt:517)\n" +
                "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1137)\n" +
                "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:637)\n" +
                "\tat java.lang.Thread.run(Thread.java:1012)\n" +
                "Caused by: android.system.GaiException: android_getaddrinfo failed: EAI_NODATA (No address associated with hostname)\n" +
                "\tat libcore.io.Linux.android_getaddrinfo(Native Method)\n" +
                "\tat libcore.io.ForwardingOs.android_getaddrinfo(ForwardingOs.java:133)\n" +
                "\tat libcore.io.BlockGuardOs.android_getaddrinfo(BlockGuardOs.java:222)\n" +
                "\tat libcore.io.ForwardingOs.android_getaddrinfo(ForwardingOs.java:133)\n" +
                "\tat java.net.Inet6AddressImpl.lookupHostByName(Inet6AddressImpl.java:135)\n" +
                "\tat java.net.Inet6AddressImpl.lookupAllHostAddr(Inet6AddressImpl.java:103) \n" +
                "\tat java.net.InetAddress.getAllByName(InetAddress.java:1152) \n" +
                "\tat okhttp3.Dns\$Companion\$DnsSystem.lookup(Dns.kt:49) \n" +
                "\tat okhttp3.internal.connection.RouteSelector.resetNextInetSocketAddress(RouteSelector.kt:164) \n" +
                "\tat okhttp3.internal.connection.RouteSelector.nextProxy(RouteSelector.kt:129) \n" +
                "\tat okhttp3.internal.connection.RouteSelector.next(RouteSelector.kt:71) \n" +
                "\tat okhttp3.internal.connection.ExchangeFinder.findConnection(ExchangeFinder.kt:205) \n" +
                "\tat okhttp3.internal.connection.ExchangeFinder.findHealthyConnection(ExchangeFinder.kt:106) \n" +
                "\tat okhttp3.internal.connection.ExchangeFinder.find(ExchangeFinder.kt:74) \n" +
                "\tat okhttp3.internal.connection.RealCall.initExchange\$okhttp(RealCall.kt:255) \n" +
                "\tat okhttp3.internal.connection.ConnectInterceptor.intercept(ConnectInterceptor.kt:32) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat okhttp3.internal.cache.CacheInterceptor.intercept(CacheInterceptor.kt:95) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat okhttp3.internal.http.BridgeInterceptor.intercept(BridgeInterceptor.kt:83) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat okhttp3.internal.http.RetryAndFollowUpInterceptor.intercept(RetryAndFollowUpInterceptor.kt:76) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat okhttp3.logging.HttpLoggingInterceptor.intercept(HttpLoggingInterceptor.kt:221) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat com.example.astrobin.api.AuthenticationInterceptor.intercept(Authentication.kt:86) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat com.example.astrobin.api.AstrobinComponent\$baseOkHttpClient\\$\$inlined\\$-addInterceptor\\$1.intercept(OkHttpClient.kt:1080) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat okhttp3.internal.connection.RealCall.getResponseWithInterceptorChain\$okhttp(RealCall.kt:201) \n" +
                "\tat okhttp3.internal.connection.RealCall\$AsyncCall.run(RealCall.kt:517) \n" +
                "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1137) \n" +
                "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:637) \n" +
                "\tat java.lang.Thread.run(Thread.java:1012) \n" +
                "Caused by: android.system.ErrnoException: android_getaddrinfo failed: EPERM (Operation not permitted)\n" +
                "\tat libcore.io.Linux.android_getaddrinfo(Native Method) \n" +
                "\tat libcore.io.ForwardingOs.android_getaddrinfo(ForwardingOs.java:133) \n" +
                "\tat libcore.io.BlockGuardOs.android_getaddrinfo(BlockGuardOs.java:222) \n" +
                "\tat libcore.io.ForwardingOs.android_getaddrinfo(ForwardingOs.java:133) \n" +
                "\tat java.net.Inet6AddressImpl.lookupHostByName(Inet6AddressImpl.java:135) \n" +
                "\tat java.net.Inet6AddressImpl.lookupAllHostAddr(Inet6AddressImpl.java:103) \n" +
                "\tat java.net.InetAddress.getAllByName(InetAddress.java:1152) \n" +
                "\tat okhttp3.Dns\$Companion\$DnsSystem.lookup(Dns.kt:49) \n" +
                "\tat okhttp3.internal.connection.RouteSelector.resetNextInetSocketAddress(RouteSelector.kt:164) \n" +
                "\tat okhttp3.internal.connection.RouteSelector.nextProxy(RouteSelector.kt:129) \n" +
                "\tat okhttp3.internal.connection.RouteSelector.next(RouteSelector.kt:71) \n" +
                "\tat okhttp3.internal.connection.ExchangeFinder.findConnection(ExchangeFinder.kt:205) \n" +
                "\tat okhttp3.internal.connection.ExchangeFinder.findHealthyConnection(ExchangeFinder.kt:106) \n" +
                "\tat okhttp3.internal.connection.ExchangeFinder.find(ExchangeFinder.kt:74) \n" +
                "\tat okhttp3.internal.connection.RealCall.initExchange\$okhttp(RealCall.kt:255) \n" +
                "\tat okhttp3.internal.connection.ConnectInterceptor.intercept(ConnectInterceptor.kt:32) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat okhttp3.internal.cache.CacheInterceptor.intercept(CacheInterceptor.kt:95) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat okhttp3.internal.http.BridgeInterceptor.intercept(BridgeInterceptor.kt:83) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat okhttp3.internal.http.RetryAndFollowUpInterceptor.intercept(RetryAndFollowUpInterceptor.kt:76) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat okhttp3.logging.HttpLoggingInterceptor.intercept(HttpLoggingInterceptor.kt:221) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat com.example.astrobin.api.AuthenticationInterceptor.intercept(Authentication.kt:86) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat com.example.astrobin.api.AstrobinComponent\$baseOkHttpClient\\$\$inlined\\$-addInterceptor\\$1.intercept(OkHttpClient.kt:1080) \n" +
                "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109) \n" +
                "\tat okhttp3.internal.connection.RealCall.getResponseWithInterceptorChain\$okhttp(RealCall.kt:201) \n" +
                "\tat okhttp3.internal.connection.RealCall\$AsyncCall.run(RealCall.kt:517) \n" +
                "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1137) \n" +
                "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:637) \n" +
                "\tat java.lang.Thread.run(Thread.java:1012) ",
          )
          .extractStudioBotContent()
      )
      .isEqualTo(
        """
        FATAL EXCEPTION: OkHttp Dispatcher
        Process: com.example.astrobin, PID: 28020
        java.lang.SecurityException: Permission denied (missing INTERNET permission?)
        at java.net.Inet6AddressImpl.lookupHostByName(Inet6AddressImpl.java:150)
        at java.net.Inet6AddressImpl.lookupAllHostAddr(Inet6AddressImpl.java:103) with tag MyTag
      """
          .trimIndent()
      )
  }

  @Test
  fun testExtractCrashWithNPE() {
    assertThat(
        logcatMessage(
            tag = "MyTag",
            message =
              "FATAL EXCEPTION: main\n" +
                "Process: com.example.jetnews, PID: 8418\n" +
                "java.lang.RuntimeException: Unable to start activity ComponentInfo{com.example.jetnews/com.example.jetnews.ui.MainActivity}: java.lang.NullPointerException: something\n" +
                "\tat android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3738)\n" +
                "\tat android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:3878)\n" +
                "\tat android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:103)\n" +
                "\tat android.app.servertransaction.TransactionExecutor.executeCallbacks(TransactionExecutor.java:138)\n" +
                "\tat android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:95)\n" +
                "\tat android.app.ActivityThread\$H.handleMessage(ActivityThread.java:2401)\n" +
                "\tat android.os.Handler.dispatchMessage(Handler.java:106)\n" +
                "\tat android.os.Looper.loopOnce(Looper.java:205)\n" +
                "\tat android.os.Looper.loop(Looper.java:294)\n" +
                "\tat android.app.ActivityThread.main(ActivityThread.java:8128)\n" +
                "\tat java.lang.reflect.Method.invoke(Native Method)\n" +
                "\tat com.android.internal.os.RuntimeInit\$MethodAndArgsCaller.run(RuntimeInit.java:578)\n" +
                "\tat com.android.internal.os.ZygoteInit.main(ZygoteInit.java:946)\n" +
                "Caused by: java.lang.NullPointerException: something\n" +
                "\tat com.example.jetnews.ui.MainActivity.onCreate(MainActivity.kt:40)\n" +
                "\tat android.app.Activity.performCreate(Activity.java:8591)\n" +
                "\tat android.app.Activity.performCreate(Activity.java:8569)\n" +
                "\tat android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1455)\n" +
                "\tat android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3720)\n" +
                "\tat android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:3878) \n" +
                "\tat android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:103) \n" +
                "\tat android.app.servertransaction.TransactionExecutor.executeCallbacks(TransactionExecutor.java:138) \n" +
                "\tat android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:95) \n" +
                "\tat android.app.ActivityThread\$H.handleMessage(ActivityThread.java:2401) \n" +
                "\tat android.os.Handler.dispatchMessage(Handler.java:106) \n" +
                "\tat android.os.Looper.loopOnce(Looper.java:205) \n" +
                "\tat android.os.Looper.loop(Looper.java:294) \n" +
                "\tat android.app.ActivityThread.main(ActivityThread.java:8128) \n" +
                "\tat java.lang.reflect.Method.invoke(Native Method) \n" +
                "\tat com.android.internal.os.RuntimeInit\$MethodAndArgsCaller.run(RuntimeInit.java:578) \n" +
                "\tat com.android.internal.os.ZygoteInit.main(ZygoteInit.java:946) ",
          )
          .extractStudioBotContent()
      )
      .isEqualTo(
        "FATAL EXCEPTION: main\n" +
          "Process: com.example.jetnews, PID: 8418\n" +
          "java.lang.RuntimeException: Unable to start activity ComponentInfo{com.example.jetnews/com.example.jetnews.ui.MainActivity}: java.lang.NullPointerException: something\n" +
          "at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3738)\n" +
          "at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:3878) with tag MyTag"
      )
  }
}
