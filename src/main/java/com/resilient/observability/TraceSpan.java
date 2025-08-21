package com.resilient.observability;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TraceSpan {
    /** The span name. Defaults to className.methodName if empty. */
    String value() default "";

    /** The operation name for the span. */
    String operation() default "";

    /** Custom tags to add to the span. Format: "key1:value1,key2:value2" */
    String tags() default "";

    /** Whether to record method arguments as span attributes. */
    boolean recordArgs() default false;
}
