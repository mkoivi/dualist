package org.dualist.ogm.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
/*
 * This tag is used in combining the Java attribute into graph attributes. 
 */
public @interface OWLProperty {
	/**
	 * A qualified name or absolute URI of an RDF property
	 */
	String value() default "";

	String value2() default "";
	String value3() default "";
	String value4() default "";
	String value5() default "";
	String value6() default "";
	/* Use SPARQL query to select resources. Note! Returned resource uris must use variable ?result.
	 * 
	 */
	String query() default "";
	/* Filter the attribute values. Useful especially if the values contains instances of multiple classes.
	 * 
	 */
	String uriFilter() default "";
	/* Accept attributes of the specified type only. 
	 */
	String attributeType() default "";
	
	/* Indicates that the property may contain OWL restrictions, such as minCardinality, some.... 
	 */
	boolean hasRestrictions() default false;
	
	/* If set to false, instantiate only 'reference' object instead of fully populated object. A reference object is recognized by calling 'isReference()'
	 * 
	 */
	boolean populateReferencedResource() default true;
	
}
