package library.typedef.usage
import androidx.annotation.IntDef
const val FENDER = 0
object GibsonHolder {
  const val GIBSON = 1
}
@IntDef(FENDER, GibsonHolder.GIBSON, Guitar.MARTIN)
annotation class Guitar {
  companion object {
    const val MARTIN = 2
  }
}
