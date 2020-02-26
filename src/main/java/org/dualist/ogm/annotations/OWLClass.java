package org.dualist.ogm.annotations;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OWLClass {

	/**
	 * A qualified name or absolute URI of an RDFBean type
	 */
	String value();

}
