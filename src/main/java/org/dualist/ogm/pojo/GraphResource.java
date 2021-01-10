package org.dualist.ogm.pojo;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.dualist.ogm.Dualist;
import org.dualist.ogm.annotations.OWLProperty;
import org.locationtech.jts.geom.Point;

public class GraphResource {

	public String uri = null;
	public String[] types;	
	public String type = null;
	
	public String graphUri = null;
	
	Dualist graph = null;

	boolean populateProperties = false;	
	
	boolean isReference = false;
	
	Point geo = null;
	
	@OWLProperty("society:name")
	public String name;
	
	List<Attribute> properties = null;
	
	
	@OWLProperty("http://www.w3.org/2003/01/geo/wgs84_pos#lat")
	float lat;

	@OWLProperty("http://www.w3.org/2003/01/geo/wgs84_pos#long")
	float lon;

	
	public enum ATTRIBUTE_RESTRICTION {MAX_CARDINALITY, MIN_CARDINALITY};
	
	
	public GraphResource() {
	}

	public GraphResource(String type) {
		this.types = new String[] {type};
	}

	public String getUri() {
		return uri;
	}

	public URI getUriObj() {
		return new URI(uri);
	}
	
	public void setUri(String uri) {
		this.uri = uri;
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
	
	
	/*
	 * Sets an additional attribute, in graph and pojo attributes
	 * 
	 */
	
	public void setExtraAttribute( String name, String value) {
		String attrUri = graph.modifyAttributeDirect(uri, name, value);
		boolean exists = false;
		for(Attribute a: getAttributes()) {
			if( a.name.equals(name)) {
				a.value = value;
				exists = true;
			}
		}				
		if( !exists ) {
			Attribute a = new Attribute(name, attrUri, value);
			properties.add(a);
		}
			
		
		
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
		String type = "[untyped]"; 
		if( types != null && types.length > 0) {
			type = types[0];
		}
		String ret = type + ": " + uri + "; ";
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

	public float getLat() {
		return lat;
	}

	public void setLat(float lat) {
		this.lat = lat;
	}

	public float getLon() {
		return lon;
	}

	public void setLon(float lon) {
		this.lon = lon;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Dualist getGraph() {
		return graph;
	}

	public void setGraph(Dualist graph) {
		this.graph = graph;
	}

	public Point getGeo() {
		return geo;
	}

	public void setGeo(Point geo) {
		this.geo = geo;
	}
	
	


}