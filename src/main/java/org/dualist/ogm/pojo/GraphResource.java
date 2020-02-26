package org.dualist.ogm.pojo;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class GraphResource {

	public String uri = null;
	public String[] types;	
	
	public String graphUri = null;

	boolean populateProperties = false;	
	List<Attribute> properties = new LinkedList<>();
	
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
	

	public void copy(GraphResource copy){
		copy.graphUri = this.graphUri;
		copy.uri = this.uri;
		System.arraycopy(this.types, 0, copy.types, 0, this.types.length);
	}

	/*
	 * Get list of object properties
	 */
	public List<Attribute> getAttributes() {
		return properties;
	}

	public List<String> getAttributeValues( String name) {
		LinkedList<String> ret = new LinkedList<>();
		for(Attribute a: properties) {
			if( a.name.equals(name)) 
					ret.add(a.value);
		}
		return ret;
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

	public void setTypes(String[] types) {
		this.types = types;
	}


	public String toString() {
		
		String ret = types[0] + ": " + uri + "; ";
		for( Attribute a : properties ) {
			ret+= a.name + " = " + a.value + ", ";
		}
		return ret;
		
	}
	
	public class Attribute {
		  public String name;
		  public String value;
		  
		  public Attribute( String n, String v) {
			  this.name = n;
			  this.value = v;
		  }
		  
	}
	


}