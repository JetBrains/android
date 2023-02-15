package library.typedef.usage
import androidx.annotation.LongDef
const val APPLE = 0L
object BananaHolder {
  const val BANANA = 1L
}
@LongDef(APPLE, BananaHolder.BANANA, Fruit.CHERRY)
annotation class Fruit {
  companion object {
    const val CHERRY = 2L
  }
}
