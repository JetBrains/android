package com.myrbsdk

import kotlin.Int
import kotlin.Unit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

public class MySdkStubDelegate internal constructor(
  private val `delegate`: MySdk,
) : IMySdk.Stub() {
  public override fun doMath(
    x: Int,
    y: Int,
    transactionCallback: IIntTransactionCallback,
  ): Unit {
    val job = GlobalScope.launch(Dispatchers.Main) {
      try {
        val result = delegate.doMath(x, y)
        transactionCallback.onSuccess(result)
      }
      catch (t: Throwable) {
        transactionCallback.onFailure(404, t.message)
      }
    }
    val cancellationSignal = TransportCancellationCallback { job.cancel() }
    transactionCallback.onCancellable(cancellationSignal)
  }
}
