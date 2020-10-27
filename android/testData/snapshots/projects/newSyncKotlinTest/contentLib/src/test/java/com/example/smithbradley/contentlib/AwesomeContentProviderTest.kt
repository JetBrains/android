package com.example.smithbradley.contentlib

import junit.framework.TestCase
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

@RunWith(BlockJUnit4ClassRunner::class)
class AwesomeContentProviderTest : TestCase() {
  var contentProvider: AwesomeContentProvider? = null

  @Before
  fun testSetup() {
    contentProvider = AwesomeContentProvider()
  }

  @Test
  fun ensureAwesomeContent() {
    assertThat(contentProvider!!.produceContent(), containsString("awesome"))
  }
}