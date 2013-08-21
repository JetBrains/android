package p1.p2;

@Retention(RUNTIME)
@Target( { ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
@BindingAnnotation
public @interface InjectResource {
  int value() default -1;
  String name() default "";
}