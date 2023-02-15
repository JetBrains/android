package library.typedef.usage;
import androidx.annotation.IntDef;
public class Amplifiers {
   public static final int PEAVEY = 0;
   public static final int ORANGE = 1;
   public static final int MARSHALL = 2;

   @IntDef({PEAVEY, ORANGE, MARSHALL})
   public @interface Amplifier {}
}
