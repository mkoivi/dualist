package org.dualist.ogm.pojo;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class GraphResource {

	public String uri = null;
	public String[] types;	
	
	// Warning: primary type is retrieved from Pojo @OWLClass annotation. Primary type will change depending on which class you use in instantiation
	public String primaryType = null;
	
	public String graphUri = null;

	boolean populateProperties = false;	
	
	boolean isReference = false;
	
	List<Attribute> properties = null;
	
	public enum ATTRIBUTE_RESTRICTION {MAX_CARDINALITY, MIN_CARDINALITY};
	
	
	public GraphResource() {
	}

	public GraphResource(String uri) {
		this.uri = uri;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public String getGraphUri() {
		return graphUri;
	}

	public void setGraphUri(String graphUri) {
		this.graphUri = graphUri;
	}
	
	public String getPrimaryType() {
		return primaryType;
	}

	public void setPrimaryType(String primaryType) {
		this.primaryType = primaryType;
	}

	public List<Attribute> getProperties() {
		return properties;
	}

	public void setProperties(List<Attribute> properties) {
		this.properties = properties;
	}

	public void copy(GraphResource copy){
		copy.graphUri = this.graphUri;
		copy.uri = this.uri;
		System.arraycopy(this.types, 0, copy.types, 0, this.types.length);
	}

	/*
	 * Get list of object properties
	 */
	public List<Attribute> getAttributes() {
		if( properties == null)
			properties = new LinkedList<>();
		return properties;
	}

	public List<String> getAttributeValues( String name) {
		if( properties == null)
			properties = new LinkedList<>();
		LinkedList<String> ret = new LinkedList<>();
		for(Attribute a: properties) {
			if( a.name.equals(name)) 
					ret.add(a.value);
		}
		return ret;
	}
	
	/*
	 * Use only when you know there is a single value
	 */
	public String getAttributeValue( String name) {
		if( properties == null)
			properties = new LinkedList<>();
		for(Attribute a: properties) {
			if( a.name.equals(name)) 
					return a.value;
		}
		return null;
	}
	
	
	public void setAttributes(List<Attribute> properties) {
		this.properties = properties;
	}

	public boolean isPopulateProperties() {
		return populateProperties;
	}

	public void setPopulateProperties(boolean populateProperties) {
		this.populateProperties = populateProperties;
	}

	public String[] getTypes() {
		if( types == null ) 
			types = new String[0];
		return types;
	}

	public boolean hasType( String htype) {
		if( types == null ) 
			types = new String[0];
		for( String type: types) {
			if( type.equals(htype)) {
				return true;
			}
		}
		return false;
	}
	
	
	public void setTypes(String[] types) {
		this.types = types;
	}


	public String toString() {
		
		String ret = types[0] + ": " + uri + "; ";
			if( properties != null) {
			for( Attribute a : properties ) {
				ret+= a.name + " = " + a.value + ", ";
			}
		}
		return ret;
		
	}
	
	
	public boolean isReference() {
		return isReference;
	}

	public void setReference(boolean isReference) {
		this.isReference = isReference;
	}
	
	public class Attribute {
		  public String name;
		  public String uri;
		  public String value;
		  public AttributeRestriction restriction;
		  
		  public Attribute( String n, String uri, String v) {
			  this.name = n;
			  this.value = v;
			  this.uri = uri;
		  }
		  
	}
	
	public class AttributeRestriction {
		public ATTRIBUTE_RESTRICTION restriction;
		public int value;
		
		public AttributeRestriction( ATTRIBUTE_RESTRICTION res, int val) {
			this.restriction = res;
			this.value = val;
		}
	}
	
	


}