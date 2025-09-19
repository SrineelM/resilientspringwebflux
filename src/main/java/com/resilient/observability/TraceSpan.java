/**
 * Annotation for marking methods to be traced as spans in distributed tracing systems.
 * <p>
 * When applied to a method, this annotation can be used by AOP or instrumentation frameworks
 * to automatically create and manage a tracing span for the method execution. The annotation
 * supports customizing the span name, operation, tags, and whether to record method arguments.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @TraceSpan(value = "user.create", operation = "createUser", tags = "role:admin", recordArgs = true)
 * public void createUser(User user) { ... }
 * }
 * </pre>
 */
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
