package library.typedef.usage
import androidx.annotation.LongDef
const val PORTOBELLO = 0L
object ChanterelleHolder {
  const val CHANTERELLE = 1L
}
@LongDef(value = [PORTOBELLO, ChanterelleHolder.CHANTERELLE, Mushroom.MOREL])
annotation class Mushroom {
  companion object {
    const val MOREL = 2L
  }
}
