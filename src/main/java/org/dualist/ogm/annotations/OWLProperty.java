package org.dualist.ogm.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OWLProperty {
	/**
	 * A qualified name or absolute URI of an RDF property
	 */
	String value() default "";
	String query() default "";

}
