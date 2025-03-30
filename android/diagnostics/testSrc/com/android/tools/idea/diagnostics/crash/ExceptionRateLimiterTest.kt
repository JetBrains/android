package com.android.tools.idea.diagnostics.crash

import com.android.testutils.VirtualTimeScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ExceptionRateLimiterTest {

  private lateinit var exceptionRateLimiter: ExceptionRateLimiter

  private lateinit var scheduler: VirtualTimeScheduler

  @Before
  fun setUp() {
    scheduler = VirtualTimeScheduler()
    scheduler.advanceBy(1L)

    exceptionRateLimiter = ExceptionRateLimiter(
      maxEventsPerPeriod = 3,
      periodMs = TimeUnit.MINUTES.toMillis(10),
      allowancePerSignature = 2,
      timeProvider = { scheduler.currentTimeMillis }
    )
  }

  @After
  fun tearDown() {
  }

  @Test
  fun tryAcquireForSignature_separeteLimitPerException() {
    val permits = mutableListOf<ExceptionRateLimiter.Permit>()
    permits.add(exceptionRateLimiter.tryAcquireForSignature("sigA"));
    permits.add(exceptionRateLimiter.tryAcquireForSignature("sigB"));
    permits.add(exceptionRateLimiter.tryAcquireForSignature("sigC"));
    permits.add(exceptionRateLimiter.tryAcquireForSignature("sigD"));
    permits.add(exceptionRateLimiter.tryAcquireForSignature("sigE"));

    permits.forEachIndexed { index, permit ->
      assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permit.permissionType)
      assertEquals(1, permit.localExceptionCounter)
      assertEquals(index + 1, permit.globalExceptionCounter)
      assertEquals(0, permit.deniedSinceLastAllow)
    }
  }

  @Test
  fun tryAcquireForSignature_allowance() {
    val permits = mutableListOf<ExceptionRateLimiter.Permit>()

    for (i in 1..10) {
      permits.add(exceptionRateLimiter.tryAcquireForSignature("sigA"));
      scheduler.advanceBy(10, TimeUnit.SECONDS)
    }

    // Allowance
    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[0].permissionType)
    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[1].permissionType)
    // Would be skipped, but within rate limit
    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[2].permissionType)
    // Allowed every power of 2 past allowance:
    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[3].permissionType)
    // Skipped (rate limit full)
    assertEquals(ExceptionRateLimiter.PermissionType.DENY, permits[4].permissionType)
    assertEquals(ExceptionRateLimiter.PermissionType.DENY, permits[5].permissionType)
    assertEquals(ExceptionRateLimiter.PermissionType.DENY, permits[6].permissionType)
    // Allowed every 1, 2, 4, 8, ...
    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[7].permissionType)
    assertEquals(3, permits[7].deniedSinceLastAllow)
    assertEquals(ExceptionRateLimiter.PermissionType.DENY, permits[8].permissionType)
    assertEquals(ExceptionRateLimiter.PermissionType.DENY, permits[9].permissionType)
    assertEquals(2, permits[9].deniedSinceLastAllow)
  }

  @Test
  fun tryAcquireForSignature_rateLimiterBlocks() {
    val permits = mutableListOf<ExceptionRateLimiter.Permit>()

    for (c in 'A'..'C') {
      for (i in 1..3) {
        permits.add(exceptionRateLimiter.tryAcquireForSignature("sig$c"));
        scheduler.advanceBy(10, TimeUnit.SECONDS)
      }
    }

    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[0].permissionType)
    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[1].permissionType)
    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[2].permissionType)

    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[3].permissionType)
    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[4].permissionType)
    assertEquals(ExceptionRateLimiter.PermissionType.DENY, permits[5].permissionType)

    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[6].permissionType)
    // deniedSinceLastAllow is calculated per signature, so it should be 0 not 1
    assertEquals(0, permits[6].deniedSinceLastAllow)
    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[7].permissionType)
    assertEquals(ExceptionRateLimiter.PermissionType.DENY, permits[8].permissionType)
  }

  @Test
  fun tryAcquireForSignature_rateLimiterAllows() {
    val permits = mutableListOf<ExceptionRateLimiter.Permit>()

    for (c in 'A'..'B') {
      for (i in 1..3) {
        permits.add(exceptionRateLimiter.tryAcquireForSignature("sig$c"));
        scheduler.advanceBy(10, TimeUnit.SECONDS)
      }
      scheduler.advanceBy(1, TimeUnit.HOURS)
    }

    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[0].permissionType)
    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[1].permissionType)
    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[2].permissionType)

    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[3].permissionType)
    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[4].permissionType)
    assertEquals(ExceptionRateLimiter.PermissionType.ALLOW, permits[5].permissionType)
  }

}
