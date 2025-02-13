@interface Prefixed {
    String prefix() default "";
}

@Prefixed(prefix = "Ut")
interface UnderTestInterface {}