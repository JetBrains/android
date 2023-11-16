package library.typedef.usage;
import androidx.annotation.LongDef;
public class Vegetables {
  public static final long ASPARAGUS = 0L;
  public static final long BROCCOLI = 1L;
  public static final long CARROT = 2L;

  @LongDef({ASPARAGUS, BROCCOLI, CARROT})
  public @interface Vegetable {}
}
