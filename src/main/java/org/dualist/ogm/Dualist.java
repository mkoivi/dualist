package org.dualist.ogm;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.geosparql.configuration.GeoSPARQLConfig;
import org.apache.jena.geosparql.configuration.GeoSPARQLOperations;
import org.apache.jena.geosparql.spatial.SpatialIndex;
import org.apache.jena.geosparql.spatial.SpatialIndexException;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.ontology.ProfileRegistry;
import org.apache.jena.ontology.Restriction;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerFactory;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.log4j.Logger;

import org.dualist.ogm.annotations.OWLClass;
import org.dualist.ogm.annotations.OWLProperty;
import org.dualist.ogm.event.LocationUpdateListener;
import org.dualist.ogm.pojo.GraphResource;
import org.dualist.ogm.pojo.GraphResource.Attribute;
import org.dualist.ogm.pojo.GraphResource.AttributeRestriction;
import org.dualist.ogm.pojo.URI;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.index.ItemVisitor;
import org.locationtech.jts.index.quadtree.Quadtree;



public class Dualist {

	private static final Logger log = Logger
			.getLogger(Dualist.class.getName());

	HashMap<String, GraphResource> objectCache = new HashMap<>();
	
	
	Quadtree t = new Quadtree();
    GeometryFactory gf = new GeometryFactory();
    
    HashMap<String, Geometry> resGeometries = new HashMap<>();
    
    List<String> level0Classes = new LinkedList<>();
 
	
	HashMap<String, Class> resourceClasses = new HashMap<String, Class>();
	
	 protected OntModel model;

	 //dataset for spatial data, contains indexes
	 Dataset dataset;
	 
	 protected String baseNs;
	 
	 protected String queryPrefixMapping = null;
	 
	 protected boolean convertNamespaceMode = false;

	 protected HashMap<String, String> namespaceMappings;

	 protected Reasoner reasoner;

	 /*
	  * Enable/disable resolving SPARQL queries.
	  */
	protected boolean populateSparqlProperties = false;

	/*
	 * Jena/OWL - Java POJO mapping layer.
	 * 
	 * Mapping is bidirectional: from OWL graph to Java and Java to OWL 
	 * -Maps the OWL classes and properties to Java using @OWLResource and @OWLProperty annotations. 
	 * -Java classes must be inherited from FacetResource class. 
	 * -OWL - pojo mapping relies on method naming
	 * convention: all annotated Java properties must have public getter and
	 * setter methods.
	 */
	public Dualist() {
//		GeoSPARQLConfig.setupMemoryIndex();
		
		model = ModelFactory.createOntologyModel(ProfileRegistry.OWL_LITE_LANG);
		
	
		
	}
	
	public void initSpatialModel() {
		Model smodel = GeoSPARQLOperations.convertGeoPredicates(model, true);
		GeoSPARQLOperations.applyPrefixes(smodel);
		GeoSPARQLConfig.setupMemoryIndex();
//		GeoSPARQLConfig.setupNoIndex(false);
		//	InfModel imodel =  GeoSPARQLOperations.prepare(smodel, ReasonerRegistry.getOWLReasoner());
		reasoner = ReasonerRegistry.getOWLReasoner();
	      InputStream geosparqlSchemaInputStream = GeoSPARQLOperations.class.getClassLoader().getResourceAsStream("schema/geosparql_vocab_all_v1_0_1_updated.rdf");
	       Model schema = ModelFactory.createDefaultModel();
	        schema.read(geosparqlSchemaInputStream, null);
	        //Apply the schema to the reasoner.
	        reasoner = reasoner.bindSchema(schema);
	        //Setup inference model.
	        OntModel imodel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM,model);
	   
		try {
			dataset = SpatialIndex.wrapModel(imodel);
	//		dataset.setDefaultModel(imodel);
			model = imodel;
	//		SpatialIndex.buildSpatialIndex(dataset);
		
		} catch (SpatialIndexException e) {
			e.printStackTrace();
		}
		
	}
	

	public String getBaseNs() {
		return baseNs;
	}

	/*
	 * Register default pojo class to be instantiated for a resource OWL class. Basically a reverse mapping for @OWLClass annotation for Java classes
	 *	 * 
	 */
	public void registerResourceClass(String owlClassUri, Class className) {
		resourceClasses.put(model.expandPrefix(owlClassUri), className);
		log.error("Registered class " + className + " for resource type " + owlClassUri);
	}
	
	
	private Class resolveResourceClass(String uri) {
		Class defClass = null;
		try {
			defClass = resourceClasses.get(model.expandPrefix(uri));
		}
		catch( Exception e ) {
			log.error("Error resolving class " + uri);
			e.printStackTrace();
		}
			
		if( defClass == null) {
			String superC = this.getSuperClass(model.expandPrefix(uri));
			if( superC != null)
				defClass = resourceClasses.get(superC);
			if( defClass == null) {
				String superC2 = this.getSuperClass(superC);
				if( superC2 != null)
						defClass = resourceClasses.get(superC2);
				if( defClass == null) {
					String superC3 = this.getSuperClass(superC2);
					if( superC3 != null)
						defClass = resourceClasses.get(superC3);
					if( defClass == null) {
						String superC4 = this.getSuperClass(superC3);
						if( superC4 != null)
							defClass = resourceClasses.get(superC4);
					}
					else {
							registerResourceClass( uri,  defClass);
					}

				}
				else {
					registerResourceClass( uri,  defClass);
				}

			}
			else {
				registerResourceClass( uri,  defClass);
			}
		}


		if( defClass == null) {
			defClass = GraphResource.class;	
			log.warn("WARNING! Class not found for resource type: " + uri);
		}
		return defClass;
	}
	
	
	
/*	private Class resolveResourceClass(String uri) {
		Class defClass = null;
		try {
			defClass = resourceClasses.get(model.expandPrefix(uri));
		}
		catch( Exception e ) {
			log.error("Error resolving class " + uri);
			e.printStackTrace();
		}
			
		if( defClass == null) {
			String superC = this.getSuperClass(model.expandPrefix(uri));
			defClass = resourceClasses.get(superC);
			if( defClass == null) {
				String superC2 = this.getSuperClass(superC);
				defClass = resourceClasses.get(superC2);
				if( defClass == null) {
					String superC3 = this.getSuperClass(superC2);
					defClass = resourceClasses.get(superC3);
					if( defClass == null) {
						String superC4 = this.getSuperClass(superC3);
						defClass = resourceClasses.get(superC4);
					}
					else {
							registerResourceClass( uri,  defClass);
							registerResourceClass( superC2,  defClass);
							registerResourceClass( superC,  defClass);
					}

				}
				else {
					registerResourceClass( uri,  defClass);
					registerResourceClass( superC,  defClass);
				}

			}
			else {
				registerResourceClass( uri,  defClass);
			}
		}


		if( defClass == null) {
			defClass = GraphResource.class;	
			log.error("SEVERE WARNING! Class not found for resource type: " + uri);
		}
		return defClass;
	}
*/	

	public void setBaseNs(String baseNs) {
		this.baseNs = baseNs;
	}


	public void loadModelFile(String fileName, String format) {
		InputStream in = FileManager.get().open(fileName);

		model.read(in, null, format);

	}

	public void loadModelFromURL(String url) {
		model.read(url);

	}

	public void loadModelFromInputStream(InputStream is, String type) {
		model.read(is, null, type);

	}

	public void loadModelFromString(String graphString, String type) {

		InputStream stream = new ByteArrayInputStream(
				graphString.getBytes(StandardCharsets.UTF_8));
		model.read(stream, null, type);

	}

	public void dumpModel() {
		model.write(System.out);
	}
	
	public void saveModel( String fileName, String outputLang ) {
		OutputStream out;
		try {
			if( outputLang.equals("JSON_LD_FLAT")) {
				out = new FileOutputStream(fileName);
				RDFDataMgr.write(out, model, RDFFormat.JSONLD_FLAT);
				return;
			}
			
			Lang lang = null;
			if( outputLang.equals("TTL"))
				lang = Lang.TURTLE;
			else if( outputLang.equals("CSV"))
				lang = Lang.CSV;
			else if( outputLang.equals("JSON"))
				lang = Lang.JSONLD;
			out = new FileOutputStream(fileName);
			RDFDataMgr.write(out, model, lang);
			
			
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
	}
	

	/* Initiate model reasoner (RDFS) and replace the original model with inferred model.
	 * 
	 * https://jena.apache.org/documentation/inference/#RDFSintro
	 * 
	 */
/*	public void initReasoner() {
	       reasoner = ReasonerRegistry.getRDFSReasoner();
	       InfModel inf = ModelFactory.createInfModel(reasoner, model);
	       log.info("Reasoner initiated: " + reasoner.toString());
	       model = inf; 
	}
	*/
	/* Initiate model reasoner (OWL) and replace the original model with inferred model.
	 * 
	 * https://jena.apache.org/documentation/inference/#OWLintro
	 */
/*	public void initOWLReasoner() {
	       reasoner = ReasonerRegistry.getOWLReasoner();
	       OntModel inf = ModelFactory.createOntologyModel(reasoner, model);
	       log.info("Reasoner initiated: " + reasoner.toString());
	       model = inf; 
	}
*/
	/*
	 * Stores a POJO resource in the graph, using default namespace.
	 * 
	 */
	public Resource create(GraphResource res) {
		return create(res, baseNs);
	}

	
	/*
	 * Stores a POJO resource in the graph, in defined namespace.
	 * 
	 */
	public Resource create(GraphResource res, String namespace) {
		try {
			Resource resourceClass;
			Resource resource = null;
			
			res.setGraph(this);
			
			if( convertNamespaceMode) {
				for( String nm: namespaceMappings.keySet()) {
					if( res.getUri().startsWith(nm)) {
						this.convertNamespace(res, namespaceMappings.get(nm));
					}
				}
				log.debug( "convert namespace ->" + res.getUri() );
			}
			
			
			log.debug("Create a " + res.getClass().toString() );
			if( res.getClass().toString().contains( "org.dualist.ogm.pojo.GraphResource")) {
				if( res.getTypes() == null) {
					log.error("Trying to create a direct instance of GraphResource. The graph resource type MUST be defined in GraphResource.types attribute!" );
					return null;
				}
				resourceClass = model
						.getResource(model.expandPrefix(res.getTypes()[0]));
			}
			else {
				Class<GraphResource> c = (Class<GraphResource>) res.getClass();
				
				if (!c.isAnnotationPresent(OWLClass.class)) {
					log.error( "Java class " + c + " is missing @OWLClass annotation!");
					return null;
				}
				
				String resType = null;
				if(res.getType() != null) {
					resType = res.getType();
				}
				else if( res.types != null && res.types.length == 1) {
					resType = res.types[0];
				}
				
				// if resource type is not defined in pojo types attribute, get the type from @OWLClass annotation
				if( resType == null) {
					OWLClass ta = c.getAnnotation(OWLClass.class);
	
					// Get resource class 
					resourceClass = model
							.getResource(model.expandPrefix(ta.value()));
				}
				else {
					resourceClass = model
							.getResource(model.expandPrefix(resType));
				}
			
			}
			String uri = res.getUri();

			if (uri != null) {
				// check if resource exists
				resource = model.getResource(uri);
				
			}
			
			String name = "";
	//		if( res.get)
			
			// resource does not exist.create new
			if (resource == null || !model.contains(resource, null)) {
				if (uri == null) { // if URI is not set, create a new URI
									// with random hash
					String resName = "";					
				//	if(res.getName() != null)
				//		resName = res.getName(). + "." + UUID.randomUUID().toString().substring(0,8);
				//	else 
						resName = UUID.randomUUID().toString().substring(0,13);
					uri = namespace + resourceClass.getLocalName() + "." + resName	;
					res.setUri(uri);
				}

				resource = model.createResource(uri, resourceClass);
				
				res.setTypes(new String[] {resourceClass.toString()});
				res.setType(resourceClass.toString());
				if( !res.getClass().toString().contains( "GraphResource"))
					this.putToCache(res);
				
				log.debug("Dualist.create, created " + uri);
				
	//			this.sendLocationUpdateEvent(new URI(res.getUri()));
				
			} else {
				log.debug("Graph already contains resource with URI " + uri);
				return resource;
			}
			
			/*
			 * Method[] methods = res.getClass().getMethods(); for (Method m :
			 * methods) { if
			 * (m.isAnnotationPresent(com.viceversatech.rdfbeans.annotations.RDF
			 * .class)) {
			 * 
			 * com.viceversatech.rdfbeans.annotations.RDF ta =
			 * m.getAnnotation(com.viceversatech.rdfbeans.annotations.RDF.class)
			 * ; Object value = m.invoke(res); // invoke getXXX method
			 * 
			 * Property property = model.getProperty(ta.value());
			 * resource.addProperty(property, value.toString());
			 * System.out.println("added property " + ta.value() + " = " +
			 * value.toString()); } }
			 */

			Field[] fields = getAllClassFields( res.getClass());
			for (Field f : fields) {
				createAttribute( f, resource, res);
			}
			log.debug("Created resource having URI " + resource.getURI() );
			
			if( res.getLat() > 2 && res.getLon() > 2) {
				updateSpatialIndex(res);
			}
			
			return resource;

		} catch (Exception e) {
			log.error("Exception during creating a graph ", e);
		}
		return null;
	}

	public void deleteFromSpatialIndex(GraphResource res) {
	//	Point pt = (Point)resGeometries.get(uri);
		Point pt = res.getGeo();
		if( pt != null)		
			t.remove(pt.getEnvelopeInternal(), pt);
	}
	
	public void updateSpatialIndex(GraphResource res) {
	//	Point pt = (Point)resGeometries.get(res.getUri());
	
		Point pt = res.getGeo();
		if( pt != null)		
			t.remove(pt.getEnvelopeInternal(), pt);
		pt = gf.createPoint(new Coordinate(res.getLon(), res.getLat()));
		pt.setUserData(res);
		res.setGeo(pt);
	    t.insert(pt.getEnvelopeInternal(), pt);
//	    resGeometries.put(res.getUri(), pt);
		
	}	
	
	/*
	public void updateSpatialIndex(String uri, float lat, float lon) {
		Point pt = (Point)resGeometries.get(uri);
		if( pt != null)		
			t.remove(pt.getEnvelopeInternal(), pt);
		pt = gf.createPoint(new Coordinate(lon, lat));
		pt.setUserData(uri);
	    t.insert(pt.getEnvelopeInternal(), pt);
	    resGeometries.put(uri, pt);
		
	}*/	
	
	
/*	public List<String> queryByLocation( String callerUri, double minLon, double maxLon, double minLat, double maxLat ) {

		List<String> res = new LinkedList<>();
		
		List<Point> ps = new LinkedList<>();
		Envelope e = new Envelope( minLon, maxLon, minLat, maxLat);
		t.query(e, new QuadPointVisitor( minLat, minLon, maxLat,maxLon, ps, callerUri));
	        for(Point p:ps) {
	        	res.add((String)p.getUserData());
	        }
	    return res;
	}
	*/
	
	/*
	 * Returns points, containing URI as userData
	 */
/*	public List<Point> queryPointsByLocation( String callerUri, double minLon, double maxLon, double minLat, double maxLat ) {
		List<Point> ps = new LinkedList<>();
		Envelope e = new Envelope( minLon, maxLon, minLat, maxLat);
		t.query(e, new QuadPointVisitor( minLat, minLon, maxLat,maxLon, ps, callerUri));
	    return ps;
	}
	*/
	/*
	 * Returns points, containing URI as userData
	 */
	/*public List<Point> queryPointsByPath( String callerUri, String callerOrg, float sLon, float tLon, float sLat, float tLat, float maxDist) {

		LineString path = gf.createLineString(new Coordinate[] {new Coordinate(sLon,sLat), new Coordinate(tLon,tLat)});

		List<Point> ps = new LinkedList<>();
		Envelope e = new Envelope( sLon, tLon, sLat, tLat);
		
		t.query(e, new QuadPointPathVisitor( ps,path, maxDist, callerUri, callerOrg));
	    return ps;
	}
	*/
	public void querySpatial( Envelope e,ItemVisitor v) {
		t.query(e, v);
		return;
	}

	
	public URI createEmptyResource( URI type ) {
		Resource resourceClass = model
				.getResource(model.expandPrefix(type.toString()));
		String uri = baseNs + resourceClass.getLocalName() + "."
				+ UUID.randomUUID().toString();

		Resource resource = model.createResource(uri, resourceClass);
		return new URI( resource.getURI());
	}
	
	private void createAttribute(Field f, Resource resource, GraphResource res) throws Exception {
		if (f.isAnnotationPresent(OWLProperty.class)) {
	
			OWLProperty taf = f.getAnnotation(OWLProperty.class);
	
			if(taf.value().contains("^")) {
				log.debug( "Complex property, skip: " + f.getName() + ", graph property " + taf.value());
				return;
			}
			if(taf.query().length() > 1) {
				log.debug( "Query property, skip: " + f.getName() );
				return;
			}
			
			
			Method getter;
	
			if( f.getType().toString().equals( "boolean")) {
				if( f.getName().startsWith( "is")) {
				getter = res.getClass()
						.getMethod(f.getName(), null);
				}
				else
				{
					getter = res.getClass()
							.getMethod("is"
									+ f.getName().substring(0, 1).toUpperCase()
									+ f.getName().substring(1), null);
				}
			}
			else {
				getter = res.getClass()
						.getMethod("get"
								+ f.getName().substring(0, 1).toUpperCase()
								+ f.getName().substring(1), null);
	
			}
	
			Object value = getter.invoke(res); // invoke getXXX method
			if( value == null) {
//				log.debug("Method call returned null value: " + getter.getName());
			}
			else {
				Property property = model
					.getProperty(model.expandPrefix(taf.value()));
			
				createGraphAttribute( resource, property, value,taf.populateReferencedResource() );
//				log.debug( "Handled attribute " + f.getName() + ", graph property " + taf.value() +  ", value " + value.toString());
			}
		}
	}	
			
	public void	createGraphAttribute( Resource resource, Property property, Object value, boolean populateReferencedResource) {			
			if (value instanceof String) {
		
				Literal l = ResourceFactory.createTypedLiteral((String)value, XSDDatatype.XSDstring);
				resource.addLiteral(property, l);
			}
			else if (value instanceof Integer) {
	
				Literal l = ResourceFactory.createTypedLiteral((Integer)value);
				resource.addLiteral(property, l);
			}
			else if (value instanceof Float) {

				Literal l = ResourceFactory.createTypedLiteral((Float)value);
				resource.addLiteral(property, l);
			}
			else if (value instanceof Double) {

				Literal l = ResourceFactory.createTypedLiteral((Double)value );
				resource.addLiteral(property, l);
			}
			else if (value instanceof Boolean) {
	
				Literal l = ResourceFactory.createTypedLiteral((Boolean)value);
				resource.addLiteral(property, l);
			}
			else if (value instanceof GraphResource) {
	
				Resource propResource;
				if( populateReferencedResource)
					propResource = create(((GraphResource) value));
				else {
					if( ((GraphResource) value).getUri() == null) {
						log.error("Object has a resource property with createReferencedResource=false, but the referenced URI is null! The resource must exist in graph! The resource with reference: " + resource.toString());
						return;
					}
					String refUri = this.getConvertedUri(((GraphResource) value).getUri());
					log.debug("Don't create referenced resource, just make a reference: " + refUri);
					propResource = model.getResource(refUri);
				}
				resource.addProperty(property, propResource);
	
			} else if (value instanceof List) {
	
				// if a List of URIs
				if( ((List) value).size() > 0 &&  ((List) value).get(0) instanceof URI) {

					for( URI luri : ((List<URI>)value)) {
						// create just predicates to resource, referenced by URI
						Resource propResource = model.getResource(this.getConvertedUri(luri.getUri()));
						resource.addProperty(property, propResource);
					}
				}
				// if a list of GraphResources
				else {
					for (GraphResource fr : (List<GraphResource>) value) {
	
						Resource propResource;
						if(populateReferencedResource)
							propResource = create(fr);
						else {
							if( fr.getUri() == null) {
								log.error("Object has a resource property with createReferencedResource=false, but the referenced URI is null! The resource must exist in graph! The resource with reference: " + resource.toString());
								continue;
							}
							log.debug("Don't create referenced resource, just make a reference: " + this.getConvertedUri(fr.getUri()));
							propResource = model.getResource(this.getConvertedUri(fr.getUri()));
						}
	
	
						resource.addProperty(property, propResource);
					}
				}
			}
			else if (value instanceof URI) {
				Resource ref = model.getResource(this.getConvertedUri(((URI)value).getUri()));
				resource.addProperty(property, ref);
			}
			 else {
	
				log.error("Error: unknown return value type for attribute " + property.getLocalName());
			}
	
			
	
		}
	
	
	
	public URI clone( URI resourceUri) {
		Resource resource = model
				.getResource(model.expandPrefix(resourceUri.toString()));
		StmtIterator iter1 = model.listStatements(
				new SimpleSelector(resource, RDF.type, (RDFNode) null));
		
			Statement stmt1 = iter1.nextStatement(); // get next statement

			RDFNode object1 = stmt1.getObject(); // get the object
			String type = object1.toString();		
		iter1.close();
		Resource resourceClass = model
				.getResource(model.expandPrefix(type.toString()));
		String newUri = baseNs + resourceClass.getLocalName() + "."
				+ UUID.randomUUID().toString();

		Resource newRes = model.createResource(newUri, resourceClass);
		
		StmtIterator iter = model.listStatements(
				new SimpleSelector(resource, null, (RDFNode) null));
		List<Statement> sts = new LinkedList<>();
		while (iter.hasNext()) {
			sts.add( iter.nextStatement()); // get next statement
		}
		iter.close();
		for( Statement stmt:sts ) {
			
			Resource subject = stmt.getSubject(); // get the subject
			Property predicate = stmt.getPredicate(); // get the predicate
			RDFNode object = stmt.getObject(); // get the object
	
			model.add(newRes, predicate, object);
		}
		return new URI(newUri);
		
	}
	
	
	/*
	 * Modifies a POJO resource in the graph. Does not delete the resource, just modifies the fields represented in this class
	 * 	Does not work with alternative OWLProperty attributes (value2, value3....)
	 */
	public void modify(GraphResource res) {
		try {
			log.debug("Dualist.modify " + res.getUri());

			String uri = res.getUri();
			Resource resource = null;
			if (uri == null) { // if URI is not set, create a new URI with
								// random hash
				return;
			} else {
				// check if resource exists
				resource = model.getResource(uri);
			}
			if (resource == null) { // if URI is not set, create a new URI with
									// random hash
				return;
			}
		
			Field[] fields = getAllClassFields( res.getClass());
			for (Field f : fields) {
				if (f.isAnnotationPresent(OWLProperty.class)) {
					OWLProperty taf = f.getAnnotation(OWLProperty.class);
					
					Property property = model
							.getProperty(model.expandPrefix(taf.value()));
					resource.removeAll(property);
					
					createAttribute(f,  resource, res) ;
				}
			}
			
			// update the object in the cache
			putToCache( res);

		} catch (Exception e) {
			log.error("Exception during modifying of a graph ", e);
		}
	}

	
		
	/*
	 * Updates an attribute of a pojo in the graph. 
	 * 
	 * attributeName is the Java class attribute name; graph attribute name is retrieved from OWLPropery annotation!
	 * Does not work with alternative OWLProperty attributes (value2, value3....)
	 */
	public void modifyAttribute(GraphResource res, String attributeName)  {
		try {
			
		Field field = getClassField(res.getClass(),attributeName);
		if( field==null) {
			log.error("No field " + attributeName + " in class " + res.getClass());
			throw new RuntimeException("No field " + attributeName + " in class " + res.getClass());
		
		}

		OWLProperty taf = field.getAnnotation(OWLProperty.class);
		
		Property property = model
				.getProperty(model.expandPrefix(taf.value()));

		Resource resource = model.getResource(res.getUri());

		
		resource.removeAll(property);
		createAttribute(field, resource, res);
		
		if( property.getLocalName().equals("lat") || property.getLocalName().equals("long") ) 
			log.error("Do not update location with modifyAttribute method! Use updateLocation instead");

		
		} catch (NoSuchFieldException e) {
			log.error("No field " + attributeName + " in class " + res.getClass());
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	/*
	 * Updates an attribute of a pojo in the graph. 
	 * 
	 * attributeName is the Java class attribute name; graph attribute name is retrieved from OWLPropery annotation!
	 * Does not work with alternative OWLProperty attributes (value2, value3....)
	 */
	public void updateLocation(GraphResource res, float lat, float lon, boolean writeToGraph)  {
		try {
		if(writeToGraph) {	
			this.modifyAttributeDirect(res.getUri(), "http://www.w3.org/2003/01/geo/wgs84_pos#lat", lat);
			this.modifyAttributeDirect(res.getUri(), "http://www.w3.org/2003/01/geo/wgs84_pos#long", lon);
		}
		res.setLat(lat);
		res.setLon(lon);
	//		this.sendLocationUpdateEvent(new URI(res.getUri()));
		updateSpatialIndex( res);
		}catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	
	/*
	 * Warning! This method updates attribute value in graph directly and does not update POJO objects. You should always reload related POJOs after calling this method.
	 * Method removes resource pojo from cache.
	 * 
	 * returns URI of the new attribute
	 */
	
	public String modifyAttributeDirect(String resUri, String attribute, Object value)  {
		Resource resource = model.getResource(model.expandPrefix(resUri));
		Property property = model.getProperty(model.expandPrefix(attribute));
		
		if( !model.containsResource(resource)) {
			log.error("Resource not found in modifyAttributeDirect: " + resUri);
		}

		resource.removeAll(property);
		model.removeAll(resource,property, (RDFNode) null);
		if( value != null)
			createGraphAttribute(resource, property, value,false);
		
	//	objectCache.remove(resUri);

		return property.getURI();
		
	}
	/*
	 * Stores a POJO resource in the graph. If the resource doesn't exist, create it.
	 * NOTE: if the graph resource exists, the method deletes all attributes, also if there are attributes not represented by this class.
	 */
	public void upsert(GraphResource res) {
		try {

			String uri = res.getUri();
			Resource resource = null;
			if( uri != null) {
			// check if resource exists
				resource = model.getResource(uri);
				if( model.containsResource(resource)) {
					model.removeAll(resource, null, (RDFNode) null);
				}
				objectCache.remove(uri);
			}

			create( res );
		
	
		} catch (Exception e) {
			log.error("Exception during modifying of a graph ", e);
		}
	}
	
	/*
	 * Delete a POJO resource from the graph. Also incoming references are
	 * removed from the graph!!
	 * 
	 */


	/*
	 * Delete a POJO resource from the graph. Also incoming references are
	 * removed from the graph!!
	 * 
	 */
	public void delete(GraphResource res) {
		log.debug("Dualist.delete " + res.getUri());
		try {
	

			deleteFromSpatialIndex(res);
			
			Resource resource = null;

						// check if resource exists
			resource = model.getResource(res.getUri());
			
			if (resource == null) { // if URI is not set, create a new URI with
									// random hash
				return;
			}

			// remove statements where resource is subject
			model.removeAll(resource, null, (RDFNode) null);
			// remove statements where resource is object
			model.removeAll(null, null, resource);

			objectCache.remove(res.getUri());

			res.setUri(null);
			res.setType(null);
			
		} catch (Exception e) {
			log.error("Exception during deleting of a graph ", e);
		}
	}

	

	public GraphResource getByAttribute(Class resourceClass,
			String property, String value) {
		List<GraphResource> res = queryByAttributeValue(resourceClass, property,
				(Object)value);
		if (res.size() == 0) {
			return null;
		}
		if( res.size() > 1) {
			log.warn("Warning: populate method returns more than 1 result!");
		}
		return res.get(0);
	}

	
	/*
	 * Get all graph resource of a specific type graphType, instantiated into objects of type resourceClass 
	 * 
	 * 
	 */
	public <T extends GraphResource> List<T> getAll(Class resourceClass,
			String graphType) {
		List<GraphResource> resPojoList = new LinkedList<>();
		try {
			StmtIterator iter = model.listStatements(
				new SimpleSelector(null, ResourceFactory.createProperty( model.expandPrefix(Constants.TYPE) ), ResourceFactory.createResource( model.expandPrefix(graphType) )));
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement(); // get next statement
				Resource subject = stmt.getSubject(); // get the subject
				// log.debug(soln.toString());
				
				GraphResource resource;
				if (objectCache.containsKey(subject.toString()) ) {
					resource = objectCache.get(subject.toString());
				} else {
					// Populate POJO and direct subclasses
					resource = populateFromGraph(subject);
					this.putToCache( resource);
				}
				resPojoList.add(resource);
			}
		} catch (Exception e) {
			log.error("Exception during querying of a graph, graphType " + graphType, e);
		}
		return (List<T>) resPojoList;
	}
	
	
	
	/* Perform a query with relative property path from originating resource 
	 * 
	 */
	public List<URI> queryRelative(String resUri, String propertyPath) {
		if(resUri.contains("http://")) resUri = "<" + resUri + ">";
		return query( "SELECT * where { " + resUri + " " + propertyPath + " ?result. }" );   
	}
	
	/* Perform a query with relative property path from originating resource 
	 * 
	 */
	public List<URI> queryRelative(GraphResource pojoResource, String propertyPath) {
		return query( "SELECT * where { <" + pojoResource.getUri() + "> " + propertyPath + " ?result. }" );   
	}

	
	
	/*
	 * A direct sparql query to retrieve results. 
	 * 
	 * NOTE: resource variable containing resource URIs is ?result
	 */
	public List<URI> query(String sparqlQuery) {
		
		if(!sparqlQuery.contains("result")) {
			log.error("Sparql query does not contain 'result' variable! Result variable must contain URI(s) of the resources to be returned from the query");
			return null;
		}
		
		List<URI> resPojoList = new LinkedList<>();
		try {

			String sparqlQueryMapped = this.getQueryPrefixMapping() + sparqlQuery;

			Query query = QueryFactory.create(sparqlQueryMapped);
			try (QueryExecution qexec = QueryExecutionFactory.create(query,
					model)) {
				ResultSet results = qexec.execSelect();

				for (; results.hasNext();) {
					QuerySolution soln = results.nextSolution();
					// log.debug(soln.toString());
					
					Resource s = soln.getResource("result");
				
					resPojoList.add(new URI(s.toString()));

				}
			}
		} catch (Exception e) {
			log.error("Exception during querying of a graph ", e);
		}

		return resPojoList;
	}
	
	
	/*
	 * 	 * A direct sparql query to retrieve results. 
	 * 
	 * NOTE: resource variable containing resource URIs is ?result
	 * 
	 */
	public <T extends GraphResource> List<T> query(Class resourceClass, String sparqlQuery) {
		List<GraphResource> resPojoList = new LinkedList<>();
		try {
			if(!sparqlQuery.contains("result")) {
				log.error("Sparql query does not contain 'result' variable! Result variable must contain URI(s) of the resources to be returned from the query");
				return null;
			}
		
				String sparqlQueryMapped = this.getQueryPrefixMapping() + sparqlQuery;

				Query query = QueryFactory.create(sparqlQueryMapped);
				try (QueryExecution qexec = QueryExecutionFactory.create(query,
						model)) {
					ResultSet results = qexec.execSelect();

					for (; results.hasNext();) {
						QuerySolution soln = results.nextSolution();
						Resource s = soln.getResource("result");
						
						GraphResource resource;
						if (objectCache.containsKey(s.toString()) ) {
							resource = objectCache.get(s.toString());
							log.debug("cache hit (query) " + s.toString());
						} else {
							log.debug("cache miss (query) " + s.toString());
						
							// Populate POJO and direct subclasses
							resource = populateFromGraph(s);
							putToCache(resource);
						}
				resPojoList.add(resource);
			}
		} catch (Exception e) {
			log.error("Exception during querying of a graph ", e);
		}
		} catch (Exception e) {
			log.error("Exception during querying of a graph ", e);
		}
				
		return (List<T>) resPojoList;
	}
	
	
	
	/*
	 * A direct sparql query to retrieve results. 
	 * 
	 * NOTE: resource variable containing resource URIs is ?result
	 */
/*	public List<GraphResource> query(Class resourceClass, String sparqlQuery) {
		
		if(!sparqlQuery.contains("result")) {
			log.error("Sparql query does not contain 'result' variable! Result variable must contain URI(s) of the resources to be returned from the query");
			return null;
		}
		
		List<GraphResource> resPojoList = new LinkedList<>();
		try {

			Map<String, String> nsmap = model.getNsPrefixMap();

			for (Entry<String, String> nsprefixEntry : nsmap.entrySet()) {
				String nsprefix = nsprefixEntry.getKey();
				StringBuilder sb = new StringBuilder();
				sb.append("PREFIX ");
				sb.append(nsprefix);
				sb.append(":   <");
				sb.append(nsmap.get(nsprefix));
				sb.append(">\n");
				sb.append(sparqlQuery);
				sparqlQuery = sb.toString();

			}

			Query query = QueryFactory.create(sparqlQuery);
			try (QueryExecution qexec = QueryExecutionFactory.create(query,
					model)) {
				ResultSet results = qexec.execSelect();

				for (; results.hasNext();) {
					QuerySolution soln = results.nextSolution();
					// log.debug(soln.toString());
					
					Resource s = soln.getResource("result");
						
					GraphResource resource;
					if (objects.containsKey(s.toString())) {
						resource = objects.get(s.toString());
					} else {
						resource = (GraphResource) Class
								.forName(resourceClass.getName()).newInstance();
						// Populate POJO and direct subclasses
						populate(resource, s);
						objects.put(s.toString(), resource);
					}
					resPojoList.add(resource);

				}
			}
		} catch (Exception e) {
			log.error("Exception during querying of a graph ", e);
		}

		return resPojoList;
	}
*/
	
	/*
	 * Query for resources by attribute value
	 *
	 * @value	Value can be an instance of GraphResource, a URI or a literal value (Integer, Boolean, Float, Double)
	 *          WARNING! Don't use resource URIs as Strings, they will be handled as String literals. Wrap in URL instances!
	 * @resourceClass Class of the resource (populates the resources) or URI.class (returns only list or URIs)
 	 */
	public List queryByAttributeValue(Class resourceClass,
			String property, Object value) {
		List resPojoList = new LinkedList<>();
		try {
			if( value instanceof String )
				value = "\"" + value + "\"";

			if( property.contains( "http://") && !property.startsWith("<")) { // not elegant
				property = "<" + property + ">";
			}
			String queryString = this.getQueryPrefixMapping() + " SELECT * where {?result "
					+ property + " " + value.toString() + ".}";


			long startTime = new Date().getTime();

			Query query = QueryFactory.create(queryString);
			try (QueryExecution qexec = QueryExecutionFactory.create(query,
					model)) {
				ResultSet results = qexec.execSelect();

				for (; results.hasNext();) {
					QuerySolution sol = results.nextSolution();
					// log.debug(soln.toString());
					Resource s = sol.getResource("result");

					if( resourceClass.equals(URI.class)) {
						URI uri = new URI( s.toString());
						resPojoList.add(uri);
					}
					else {
						GraphResource resource = this.get(s.toString(), resourceClass);
						
						resPojoList.add(resource);
					}
				}
			}
		} catch (Exception e) {
			log.error("Exception during querying of a graph ", e);
		}

		return resPojoList;
	}


	/* 
	 * Get a POJO by URI
	 */
	public <T extends GraphResource> T get(URI uri, Class resourceClass) {
		return get(uri, resourceClass, false);
	}
	
	/* 
	 * Get a POJO by URI
	 */
	public <T extends GraphResource> T get(String uri, Class resourceClass) {
		return get(new URI(uri), resourceClass, false);
	}
	
	
	/* 
	 * Get a POJO by URI
	 */
	public <T extends GraphResource> T get(URI ref, Class resourceClass, boolean populateAttributeList) {
		
		GraphResource resource = null;
		try {
			if( !this.containsResource(ref.toString()) ) {
				log.debug( "Resource not found in graph" );
				return null;
			}
			
			Resource s = model.getResource(model.expandPrefix(ref.uri));
	
			boolean populateNew = true;
			if (objectCache.containsKey(s.toString()) ) {
				resource = objectCache.get(s.toString());
			}
			else {
				log.debug("cache miss: " + ref.toString());

				resource = populateFromGraph( s);

			}
	

		return (T) resource;
		}
	catch( Exception e) {
		e.printStackTrace();
	}
		return null;
	}
	/*
	 * Instantiates a list of URIs to list of classes. Uses defaultClass to resolve resource's class if not found in cache.
	 * 
	 */
	public <T extends GraphResource> List<T> instantiate(List<URI> uris, Class resourceClass) {
		List<GraphResource> resPojoList = new LinkedList<>();
		for( URI uri: uris) {
			GraphResource res = get(uri, resourceClass, false);
		
			resPojoList.add(res);
		}
		
		return (List<T>) resPojoList;
	}
	
	/*
	 * Instantiates a list of URIs to list of classes. Uses defaultClass to resolve resource's class if not found in cache.
	 * 
	 */
	public <T extends GraphResource> List<T> instantiate(List<URI> uris) {
		List<GraphResource> resPojoList = new LinkedList<>();
		for( URI uri: uris) {
			GraphResource res = objectCache.get(uri.toString());
			if( res == null) {
				res = get(uri, null);
			}
			resPojoList.add(res);
		}
		
		return (List<T>) resPojoList;
	}
	
	


	protected void populateLiteralProperties(GraphResource pojoResource,
			Resource resource) {
		StmtIterator iter = model.listStatements(
				new SimpleSelector(resource, null, (RDFNode) null));

		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement(); // get next statement
			Resource subject = stmt.getSubject(); // get the subject
			Property predicate = stmt.getPredicate(); // get the predicate
			RDFNode object = stmt.getObject(); // get the object

			if (object instanceof Resource) {
				// object is a resource
			} else {
				// object is a literal

				setPropertyValue(pojoResource, predicate, object);
				
			}
		}
		iter.close();
	}
	
	
	public boolean isResourceTypeOf( URI resource, URI type) {
		Resource r = model.getResource(model.expandPrefix(resource.uri));
		Resource t = model.getResource(model.expandPrefix(type.uri));
		return model.contains(r, RDF.type, t);
	}

	public boolean isSubClassOf( String child, String parent ) {
		OntClass o = ((OntModel)model).getOntClass(model.expandPrefix(child));
		
		if(o== null )
			return false;
		Iterator<OntClass> it = o.listSuperClasses();
		while(it.hasNext()) {
	         OntClass par = it.next();
			if( par.getURI().equals(model.expandPrefix(parent))) {
				return true;
			}
		}
		return false;
	}
		
	public String getSuperClass(String child) {
		if( child == null)
			return null;
	OntClass o = ((OntModel)model).getOntClass(model.expandPrefix(child));
	
	if(o== null )
		return null;
	Iterator<OntClass> it = o.listSuperClasses();

	while(it.hasNext()) {
         OntClass par = it.next();
		if( par.getURI().startsWith(getBaseNs())) {
			return par.getURI();
		}
	}
	return null;
}
	
	
	
	public String getResourceType( GraphResource resource) {
		for( String t:resource.getTypes()) {
			OntClass o = ((OntModel)model).getOntClass(t);
			if( !o.listSubClasses().hasNext()) {
				return t;
			}
		}
		return null;
		
	}
	
	public String getResourceType( List<String> types) {
		for( String t:types) {
			OntClass o = ((OntModel)model).getOntClass(t);
			if( !o.listSubClasses().hasNext()) {
				return t;
			}
		}
		return null;
		
	}
	
	
	public void initResourceTypeCache( String domainPrefix ) {
		
	
		List<URI> allClasses = this.query("SELECT DISTINCT ?result {?result rdf:type owl:Class. FILTER (isURI(?result) && STRSTARTS(str(?result), str("+domainPrefix+":) ) ) }");

		for( URI c:allClasses) {
			resolveResourceClass(c.toString());
		}
		
		for( URI c:allClasses) {
			OntClass o = ((OntModel)model).getOntClass(c.toString());
			if( !o.listSubClasses().hasNext()) {
				level0Classes.add(c.toString().substring(c.toString().indexOf("#")+1));
			}
				
		}
/*		for( URI c:level0Classes) {
			OntClass o = ((OntModel)model).getOntClass(c.toString());
			Iterator<OntClass> is = o.listSuperClasses(false);
			while( is.hasNext()) {
				OntClass os =is.next();
				if( !level1Classes.contains(new URI(os.getURI())))
					level1Classes.add(c);
			}
		}
		for( URI c:level1Classes) {
			OntClass o = ((OntModel)model).getOntClass(c.toString());
			Iterator<OntClass> is = o.listSuperClasses(false);
			while( is.hasNext()) {
				OntClass os =is.next();
				if( !level2Classes.contains(new URI(os.getURI())))
					level2Classes.add(c);
			}
		}	*/	
	}
	

/*	private void populateInverseProperties(GraphResource res) {
		// TODO Auto-generated method stub
		try {
			Resource resourceClass;
			Class<GraphResource> c = (Class<GraphResource>) res.getClass();
			if (c.isAnnotationPresent(OWLClass.class)) {
				OWLClass ta = c.getAnnotation(OWLClass.class);
				resourceClass = model.getResource(ta.value());
			}

			Set<Field> fields = new HashSet<Field>();
			Field[] f1 = res.getClass().getFields();
			Field[] f2= res.getClass().getDeclaredFields();
			for( Field f:f1) fields.add(f);
			for( Field f:f2) fields.add(f);

			for (Field f : fields) {
				if (f.isAnnotationPresent(OWLProperty.class)) {
					OWLProperty ta = f.getAnnotation(OWLProperty.class);
					if( ta.value().length() > 0 && ta.value().startsWith("^")) {
						
						Property predicate = model.getProperty(model.expandPrefix(ta.value().substring(1)));
						Resource object = model.getResource(res.getUri());
						
						ResIterator iter = model.listSubjectsWithProperty( predicate, object);

						List<GraphResource> resPojoList = new LinkedList<>();
						boolean isList = false;
						
						while (iter.hasNext()) {
							Resource s = iter.nextResource();

							if( ta.uriFilter().length() > 1) {

								if( !(s.toString()).matches(wildcardToRegex(ta.uriFilter()))) {
									log.debug("Resource URI does not match filter, skip: " + f.getName() + ", object: " + s.toString());
									continue;
								}
								else
									log.debug("Resource URI matches filter: " + f.getName() + ", object: " + s.toString());

								}
								if( ta.attributeType().length() > 1) {
									boolean passed = false;
									StmtIterator iter2 = model.listStatements(
											new SimpleSelector((Resource)s, RDF.type, (RDFNode) null));
									while (iter2.hasNext()) {
										Statement stmt = iter2.nextStatement(); // get next statement
										RDFNode resType = stmt.getObject(); // get the object
										if( resType.toString().equals(model.expandPrefix(ta.attributeType()))) {
											log.debug("Resource type matches filter, field: " + f.getName() + ", object: " + s.toString());
											passed = true;
										}
									}
									if( !passed )
										continue;
								}							
				
							
							String pojoClass = f.getGenericType()
									.toString();
							
							if( pojoClass.contains("java.util.List")) {
								pojoClass = pojoClass.substring(
									pojoClass.indexOf("<") + 1,
									pojoClass.indexOf(">"));
								isList = true;
							}
							else {
								pojoClass=pojoClass;
							}
							GraphResource instance;
							if (objectCache.containsKey(s.toString())) {
								instance =objectCache.get(s.toString());
							} else {
								instance = (GraphResource) Class
										.forName(pojoClass).newInstance();
								instance.setUri(s.toString());
								instance.setReference(true);
								this.putToCache(instance);

							}
							if(ta.populateReferencedResource() && instance.isReference()) {
								populateFromGraph(instance, s);
								instance.setReference(false);
							}
							this.putToCache(instance);
							
							resPojoList.add(instance);
						}
						if( isList ) {
							Method setter;
							setter = res.getClass().getMethod(
									Constants.SET
											+ f.getName().substring(0, 1)
													.toUpperCase()
											+ f.getName().substring(1),
									List.class);
							setter.invoke(res, resPojoList); // invoke getXXX
															// method
						}
						else if( !isList && resPojoList.size() > 0){

							Method setter = res.getClass().getMethod(
									Constants.SET
											+ f.getName().substring(0, 1)
													.toUpperCase()
											+ f.getName().substring(1),
									Class.forName(f.getType().getName()));
							setter.invoke(res, resPojoList.get(0)); // invoke getXXX
						}
					}
				}
			}

		} catch (Exception e) {
			log.error("Exception during querying of a graph, resource URI= " + res.getUri(), e);
		}
	}
	
	*/
	
	

	/*
	 * Populate a POJO object from a graph resource hierarchically.
	 * 
	 * Verify that no circular references exist in the graph
	 * 
	 */
	protected GraphResource populateFromGraph(Resource resource) {

		StmtIterator iter = model.listStatements(
				new SimpleSelector(resource, null, (RDFNode) null));
		
		List<Property> properties = new LinkedList<>();
		List<RDFNode> objects = new LinkedList<>();
		List<String> types = new LinkedList<>();
		
		// get all triples
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement(); // get next statement
			Resource subject = stmt.getSubject(); // get the subject
			Property predicate = stmt.getPredicate(); // get the predicate
			RDFNode object = stmt.getObject(); // get the object

			// if a rdf:type triple, add to types
			if (predicate.toString().endsWith("ns#type")) {
				if( !object.toString().contains("owl")  && !object.toString().contains("rdfs") && !object.toString().contains("rdf-schema") && !object.toString().contains("wgs84_pos") && !object.toString().contains("geosparql") && !object.toString().contains("Class") && ( object.toString().contains(":") || object.toString().contains("/")  )) {
					types.add( object.toString());
				}
			}
			else {
				properties.add(predicate);
				objects.add(object);	
			}
		}
		
		String resourceType = null;
		
		if( types.size() == 0 ) {
			
		}
		else if( types.size() == 1 ) 
			resourceType = types.get(0);
		else {
			for( String type:types) {
				if( level0Classes.contains(type.toString().substring(type.toString().indexOf("#")+1)))
					resourceType = type;
			}
			if( resourceType == null) 
				resourceType = this.getResourceType(types);
		}
		
		Class resourceClass = this.resolveResourceClass(resourceType);	
		GraphResource pojoResource;
		try {
			pojoResource = (GraphResource) Class
						.forName(resourceClass.getName()).newInstance();

		pojoResource.setGraph(this);
		pojoResource.setUri (resource.getURI());
		pojoResource.setType(resourceType);
		pojoResource.setTypes((String[])(types.toArray(new String[types.size()])));
	//	pojoResource.setPopulateProperties(populateAttributeList);
		putToCache( pojoResource);
			// Populate POJO and direct subclasses	
			
			
		int i=0;	
		for(Property predicate: properties) {
			RDFNode object = objects.get(i);
			i++;
			if (object instanceof Resource) {
				// object is a resource
				if (predicate.toString().contains("subClassOf"))
					continue;

				instantiateResourceProperty(pojoResource, predicate, object);
				
			
			} else {
				// object is a literal

				setPropertyValue(pojoResource, predicate, object);
				}
		}

		if( populateSparqlProperties )
			populateQueryProperties(pojoResource);
		pojoResource.setReference(false);

//		this.putToCache(pojoResource);

		populateQueryProperties(pojoResource);

					
		updateSpatialIndex( pojoResource);
	
		pojoResource.setGraph(this);
		
		return pojoResource;
	//	pojoResource.setDirectType(this.getType(pojoResource.getUriObj()));
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private void populateQueryProperties(GraphResource res) {
		// TODO Auto-generated method stub
		try {
			Resource resourceClass;
			Class<GraphResource> c = (Class<GraphResource>) res.getClass();
			if (c.isAnnotationPresent(OWLClass.class)) {
				OWLClass ta = c.getAnnotation(OWLClass.class);
				resourceClass = model.getResource(ta.value());
			}
			/*
			 * Method[] methods = res.getClass().getMethods(); for (Method m :
			 * methods) { if
			 * (m.isAnnotationPresent(com.viceversatech.rdfbeans.annotations.RDF
			 * .class)) {
			 * 
			 * com.viceversatech.rdfbeans.annotations.RDF ta =
			 * m.getAnnotation(com.viceversatech.rdfbeans.annotations.RDF.class)
			 * ; Object value = m.invoke(res); // invoke getXXX method
			 * 
			 * Property property = model.getProperty(ta.value());
			 * resource.addProperty(property, value.toString());
			 * log.debug("added property " + ta.value() + " = " +
			 * value.toString()); } }
			 */

			Field[] fields = getAllClassFields( res.getClass());
			
			for (Field f : fields) {
				if (f.isAnnotationPresent(OWLProperty.class)) {
					OWLProperty ta = f.getAnnotation(OWLProperty.class);
					if (ta.query().length() > 0) {
						
						String queryString = this.getQueryPrefixMapping() + ta.query().replace("?resource",
									"<" + res.getUri() + ">");
							
						long startTime = new Date().getTime();

						Query query = QueryFactory.create(queryString);
						try (QueryExecution qexec = QueryExecutionFactory
								.create(query, model)) {
							ResultSet results = qexec.execSelect();

							List<GraphResource> resPojoList = new LinkedList<>();
							boolean isList = false;

							for (; results.hasNext();) {
								QuerySolution soln = results.nextSolution();
								// log.debug(soln.toString());
								Resource s = soln.getResource("result");
								String pojoClass = f.getGenericType()
										.toString();

								if( ta.uriFilter().length() > 1) {

									if( !(s.toString()).matches(wildcardToRegex(ta.uriFilter()))) {
										log.debug("Resource URI does not match filter, skip: " + f.getName() + ", object: " + s.toString());
										continue;
									}
									else
										log.debug("Resource URI matches filter: " + f.getName() + ", object: " + s.toString());

									}
									if( ta.attributeType().length() > 1) {
										StmtIterator iter = model.listStatements(
												new SimpleSelector((Resource) s, RDF.type, (RDFNode) null));
										while (iter.hasNext()) {
											Statement stmt = iter.nextStatement(); // get next statement
											RDFNode resType = stmt.getObject(); // get the object
											if( !resType.toString().equals(model.expandPrefix(ta.attributeType()))) {
												log.debug("Resource type does not match filter, skip: " + f.getName() + ", object: " + s.toString());
												continue;
											}
										}
									}							
					
								
								
								if( pojoClass.contains("java.util.List")) {
									pojoClass = pojoClass.substring(
										pojoClass.indexOf("<") + 1,
										pojoClass.indexOf(">"));
									isList = true;
								}
								else {
									pojoClass=pojoClass;
								}
								GraphResource instance;
								if (objectCache.containsKey(s.toString())) {
									instance = objectCache.get(s.toString());
								} else {
									instance = populateFromGraph( s);
									this.putToCache(instance);
								}
							/*	if(ta.populateReferencedResource() && instance.isReference()) {
									populateFromGraph(instance, s);
									instance.setReference(false);
								}*/
								this.putToCache(instance);
								
								resPojoList.add(instance);
							}
							if( isList ) {
								Method setter;
								setter = res.getClass().getMethod(
										Constants.SET
												+ f.getName().substring(0, 1)
														.toUpperCase()
												+ f.getName().substring(1),
										List.class);
								setter.invoke(res, resPojoList); // invoke getXXX
																// method
							}
							else if( !isList && resPojoList.size() > 0){

								Method setter = res.getClass().getMethod(
										Constants.SET
												+ f.getName().substring(0, 1)
														.toUpperCase()
												+ f.getName().substring(1),
										Class.forName(f.getType().getName()));
								setter.invoke(res, resPojoList.get(0)); // invoke getXXX
							}

						}

					}
				}
			}

		} catch (Exception e) {
			log.error("Exception during querying of a graph, resource URI= " + res.getUri(), e);
		}
	}

	
	/*
	 * isMin = true -> minCardinality
	 * isMin = false -> maxCardinality
	 * 
	 */
	public List<Attribute> getAttributeMetadata( GraphResource res) {
		
		if( res.isPopulateProperties())
			return res.getAttributes();
		Class resClass = res.getClass();
		
		
		Resource resource = model.getResource(res.getUri());

		StmtIterator iter = model.listStatements(
			new SimpleSelector(resource, null, (RDFNode) null));

		
		// get all triples
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement(); // get next statement
		//	Resource subject = stmt.getSubject(); // get the subject
			Property predicate = stmt.getPredicate(); // get the predicate
			RDFNode object = stmt.getObject(); // get the object
	
			// if a rdf:type triple, add to types
			if (predicate.toString().endsWith("ns#type")) {
				continue;
			}
		
			GraphResource.AttributeRestriction ar = null;
			OntProperty p2 = model.getOntProperty(predicate.getURI());
			Iterator<Restriction> i = p2.listReferringRestrictions();
			while (i.hasNext()) {
			    Restriction r = i.next();
			    if( r.getPropertyValue( OWL2.minQualifiedCardinality) != null ) {
				    	ar = res.new AttributeRestriction(GraphResource.ATTRIBUTE_RESTRICTION.MIN_CARDINALITY, r.getPropertyValue( OWL2.minQualifiedCardinality).asLiteral().getInt());
				    	
				    }
				    else if( r.getPropertyValue( OWL2.maxQualifiedCardinality) != null ) {
				    	ar = res.new AttributeRestriction(GraphResource.ATTRIBUTE_RESTRICTION.MAX_CARDINALITY, r.getPropertyValue( OWL2.maxQualifiedCardinality).asLiteral().getInt());						    
				    }
			}

			// get the attribute basic name (for example requires5 -> requires)
			String attrName = null;
			Field[] fields = getAllClassFields( resClass);
			for (Field f : fields) {
				if (f.isAnnotationPresent(OWLProperty.class)) {
					OWLProperty ta = f.getAnnotation(OWLProperty.class);
					
					if (ta.value().equals(predicate.toString())
							|| model.expandPrefix(ta.value())
									.equals(predicate.getURI()) || model.expandPrefix(ta.value2() ).equals(predicate.getURI()) || model.expandPrefix(ta.value3() ).equals(predicate.getURI()) || model.expandPrefix(ta.value4() ).equals(predicate.getURI()) || model.expandPrefix(ta.value5() ).equals(predicate.getURI()) || model.expandPrefix(ta.value6() ).equals(predicate.getURI()) )  {					
						attrName = f.getName();
					}
				}
			}
			if( attrName == null) 
				continue;
			List<GraphResource.Attribute> props = res.getAttributes();
			GraphResource.Attribute att = null;
			if( object instanceof Resource)
				att = res.new Attribute( attrName,predicate.getURI(),((Resource) object).getURI());
			else if( object instanceof Literal) {
				String cleanedLiteralValue = object.toString();
				if( cleanedLiteralValue.indexOf('^') > 0 ) {
					cleanedLiteralValue = cleanedLiteralValue.substring(0, cleanedLiteralValue.indexOf('^'));
				}
				att = res.new Attribute( attrName,predicate.getURI(),cleanedLiteralValue);

			}
			if( ar != null) 
				att.restriction = ar;
			props.add(att);
			res.setAttributes(props);
		}
		res.setPopulateProperties(true);
		return res.getAttributes();
		
	}
	
	
	/*
	 * Populate resource property in POJO model. Uses getXXX and setXXX to
	 * access values.
	 * 
	 * Supports the following Java attribute types: {Direct class, extending
	 * FacetResource} - a single attribute value, containing an invoked object
	 * List<{Property class} - a list of attributes org.dualist.ogm.pojo.URI -
	 * reference to the property, containing only URI refence. Referenced
	 * resource in not instantiated
	 * 
	 */
	protected void instantiateResourceProperty(GraphResource pojoResource,
			Property predicate, RDFNode object) {
		try {
		//	log.info("instantiate " + object.toString());
			AttributeRestriction ar = null;
			String pojoAttributeName = null;
			
			Field[] fields = getAllClassFields( pojoResource.getClass());
			for (Field f : fields) {
				if (f.isAnnotationPresent(OWLProperty.class)) {
					OWLProperty ta = f.getAnnotation(OWLProperty.class);
					
					if(ta.value().contains("^")) {
						log.debug("A complex property in annotation, skip: " + ta.value());
						continue;
					}

					if( ta.uriFilter().length() > 1) {

						if( !(object.toString()).matches(wildcardToRegex(ta.uriFilter()))) {
							log.debug("Resource URI does not match filter, skip: " + f.getName() + ", object: " + object.toString());
							continue;
						}
						else
							log.debug("Resource URI matches filter: " + f.getName() + ", object: " + object.toString());

					}
					if( ta.attributeType().length() > 1) {
						StmtIterator iter = model.listStatements(
								new SimpleSelector((Resource) object, RDF.type, (RDFNode) null));
						while (iter.hasNext()) {
							Statement stmt = iter.nextStatement(); // get next statement
							RDFNode resType = stmt.getObject(); // get the object
							if( !resType.toString().equals(model.expandPrefix(ta.attributeType()))) {
								log.debug("Resource type does not match filter, skip: " + f.getName() + ", object: " + object.toString());
								continue;
							}
						}
					}	

					
					if (ta.value().equals(predicate.toString())
							|| model.expandPrefix(ta.value())
									.equals(predicate.getURI()) || model.expandPrefix(ta.value2() ).equals(predicate.getURI()) || model.expandPrefix(ta.value3() ).equals(predicate.getURI()) || model.expandPrefix(ta.value4() ).equals(predicate.getURI()) || model.expandPrefix(ta.value5() ).equals(predicate.getURI()) || model.expandPrefix(ta.value6() ).equals(predicate.getURI()) )  {
						
						// POJO annotation matched with predicate
						
						pojoAttributeName = f.getName();
						if( ta.hasRestrictions()) {
							
							OntProperty p = model.getOntProperty(predicate.getURI() );
							Iterator<Restriction> i = p.listReferringRestrictions();
							while (i.hasNext()) {
							    Restriction r = i.next();
							   
							    if( r.getPropertyValue( OWL2.minQualifiedCardinality) != null ) {
							    	ar = pojoResource.new AttributeRestriction(GraphResource.ATTRIBUTE_RESTRICTION.MIN_CARDINALITY, r.getPropertyValue( OWL2.minQualifiedCardinality).asLiteral().getInt());
							    	
							    }
							    else if( r.getPropertyValue( OWL2.maxQualifiedCardinality) != null ) {
							    	ar = pojoResource.new AttributeRestriction(GraphResource.ATTRIBUTE_RESTRICTION.MAX_CARDINALITY, r.getPropertyValue( OWL2.maxQualifiedCardinality).asLiteral().getInt());						    
							    }
							}
						}
						
						if (f.getType().toString()
								.contains("org.dualist.ogm.pojo.URI")) {
							
							org.dualist.ogm.pojo.URI uri = new org.dualist.ogm.pojo.URI(object.toString());
							Method setter;
							setter = pojoResource.getClass().getMethod(
									Constants.SET
											+ f.getName().substring(0, 1)
													.toUpperCase()
											+ f.getName().substring(1),
									org.dualist.ogm.pojo.URI.class);
							setter.invoke(pojoResource, uri); // invoke getXXX
							break;
						} 
						if (f.getGenericType().toString()
								.contains("URI") && f.getType().toString()
								.contains("List")) {

							org.dualist.ogm.pojo.URI uri = new org.dualist.ogm.pojo.URI(object.toString());
			
							Method getter = pojoResource.getClass()
									.getMethod(
											"get" + f.getName().substring(0, 1)
													.toUpperCase()
													+ f.getName().substring(1),
											null);
							List<URI> list = (List<URI>) (getter.invoke(pojoResource)); // invoke getXXX
							if (list == null) {
								list = new LinkedList();
							}
							list.add(uri);
							Method setter;

							setter = pojoResource.getClass().getMethod(
									Constants.SET
											+ f.getName().substring(0, 1)
													.toUpperCase()
											+ f.getName().substring(1),
									List.class);
							setter.invoke(pojoResource, list); // invoke getXXX
						}
						
						else if (f.getType().toString().contains("String")) {
							Method setter;
							setter = pojoResource.getClass().getMethod(
									Constants.SET
											+ f.getName().substring(0, 1)
													.toUpperCase()
											+ f.getName().substring(1),
									String.class);
							setter.invoke(pojoResource, object.toString()); // getXXX 
						} 
						else if (f.getType().toString().contains("java.util.List")) {
							String pojoClass = f.getGenericType().toString();
							pojoClass = pojoClass.substring(
									pojoClass.indexOf("<") + 1,
									pojoClass.indexOf(">")); // TODO: not clean
							
							// if the pojo property type is List<URI>
							if(pojoClass.contains("org.dualist.ogm.pojo.URI")) {
								org.dualist.ogm.pojo.URI uri = new org.dualist.ogm.pojo.URI(object.toString());
								Method getter = pojoResource.getClass()
										.getMethod(
												"get" + f.getName().substring(0, 1)
														.toUpperCase()
														+ f.getName().substring(1),
												null);
								List list = (List) (getter.invoke(pojoResource)); // invoke getXXX
								if (list == null) {
									list = new LinkedList<>();
								}
								list.add(uri);
								Method setter;
								setter = pojoResource.getClass().getMethod(
										Constants.SET
												+ f.getName().substring(0, 1)
														.toUpperCase()
												+ f.getName().substring(1),
										List.class);
								setter.invoke(pojoResource, list); // invoke getXXX
								
							}
							// pojo attribute type is List<... extends GraphResource>
							else {
							
								GraphResource instance;
								
								if (objectCache.containsKey(object.toString())) {
									instance = objectCache.get(object.toString());
								} else {
									Resource subRes = model
											.getResource(object.toString());
									instance = populateFromGraph(subRes);
								}
						/*		if(ta.populateReferencedResource() && instance.isReference()) {
									Resource subRes = model
											.getResource(object.toString());
									populateFromGraph(instance, subRes);
									
								}*/
								this.putToCache(instance);
							
								Method getter = pojoResource.getClass()
										.getMethod(
												"get" + f.getName().substring(0, 1)
														.toUpperCase()
														+ f.getName().substring(1),
												null);
								List list = (List) (getter.invoke(pojoResource)); // invoke getXXX
								if (list == null) {
									list = new LinkedList();
								}
								list.add(instance);
								Method setter;
								setter = pojoResource.getClass().getMethod(
										Constants.SET
												+ f.getName().substring(0, 1)
														.toUpperCase()
												+ f.getName().substring(1),
										List.class);
								setter.invoke(pojoResource, list); // invoke getXXX
							}
							
						} else {
							
							GraphResource instance;
							if (objectCache.containsKey(object.toString())) {
								instance = objectCache.get(object.toString());
							} else {
								instance = (GraphResource) Class
										.forName(f.getType().getName())
										.newInstance();
								instance.setUri(object.toString());
								instance.setReference(true);
							}
							if(ta.populateReferencedResource() && instance.isReference()) {							
								Resource subRes = model
										.getResource(object.toString());
								instance = populateFromGraph( subRes);
								}
							this.putToCache(instance);
							Method setter;
							setter = pojoResource.getClass().getMethod(
									Constants.SET
											+ f.getName().substring(0, 1)
													.toUpperCase()
											+ f.getName().substring(1),
									Class.forName(f.getType().getName()));
							try {
 							setter.invoke(pojoResource, instance); // invoke getXXX
							}
							catch( IllegalArgumentException e) {
								log.error("Could not call" + setter.toString() + " with " + instance.toString() );
								e.printStackTrace();
							}
						}
						
						if( pojoResource.isPopulateProperties()) {
							List<GraphResource.Attribute> props = pojoResource.getAttributes();
							GraphResource.Attribute att = pojoResource.new Attribute( pojoAttributeName,predicate.getURI(),((Resource) object).getURI());
							if( ar != null)
								att.restriction = ar;
							props.add(att);
							pojoResource.setAttributes(props);
						}
						
						
						break;
					}
				}
			}
	
	
			
		} catch (InvocationTargetException | IllegalAccessException
				| ClassNotFoundException | NoSuchMethodException
				| InstantiationException e) {
			log.error("Exception during creating a graph: resource " + pojoResource.getUri() + ", predicate: " + predicate.toString(), e);
		}

	}

	private void setPropertyValue(GraphResource pojoResource, Property predicate,
			RDFNode object) {
		try {
		
			Field[] fields  =getAllClassFields( pojoResource.getClass());
			
			for (Field f : fields) {
				if (f.isAnnotationPresent(OWLProperty.class)) {

					OWLProperty ta = f.getAnnotation(OWLProperty.class);
					// log.debug("OWL prop: " + ta.value() + ",
					// expanded " + model.expandPrefix(ta.value()) + ",
					// predicate " + predicate.toString() );

					if (ta.value().equals(predicate.toString())
							|| model.expandPrefix(ta.value())
									.equals(predicate.toString())) {
						Method setter;
						boolean found = false;
						
						String cleanedLiteralValue = object.toString();
						if( cleanedLiteralValue.indexOf('^') > 0 ) {
							cleanedLiteralValue = cleanedLiteralValue.substring(0, cleanedLiteralValue.indexOf('^'));
						}
						
						try {
							setter = pojoResource.getClass().getMethod( // try to find a setter with String parameter
								Constants.SET
										+ f.getName().substring(0, 1)
												.toUpperCase()
										+ f.getName().substring(1),
								String.class);
							setter.invoke(pojoResource, object.toString()); // invoke it
							found = true;
						}
						catch( NoSuchMethodException e) { // no luck
						}
		
						if( !found ) { //  try to find a setter with int parameter
							try {
								setter = pojoResource.getClass().getMethod(
									Constants.SET
											+ f.getName().substring(0, 1)
													.toUpperCase()
											+ f.getName().substring(1),
									Integer.TYPE);
								setter.invoke(pojoResource, new Integer(cleanedLiteralValue).intValue()); // invoke it
								found = true;
							}
							catch( NoSuchMethodException e) { // no luck
							}
						}
	
						if( !found ) { //  try to find a setter with float parameter
							try {
								setter = pojoResource.getClass().getMethod(
									Constants.SET
											+ f.getName().substring(0, 1)
													.toUpperCase()
											+ f.getName().substring(1),
									Float.TYPE);
								setter.invoke(pojoResource, new Float(cleanedLiteralValue)); // invoke it
								found = true;
							}
							catch( NoSuchMethodException e) { // no luck
							}
						}
						
						if( !found ) { //  try to find a setter with double parameter
							try {
								setter = pojoResource.getClass().getMethod(
									Constants.SET
											+ f.getName().substring(0, 1)
													.toUpperCase()
											+ f.getName().substring(1),
									Double.TYPE);
								setter.invoke(pojoResource, new Double(cleanedLiteralValue)); // invoke it
								found = true;
							}
							catch( NoSuchMethodException e) { // no luck
							}
						}
		
						if( !found ) { //  try to find a setter with boolean parameter
							try {
								setter = pojoResource.getClass().getMethod(
									Constants.SET
											+ f.getName().substring(0, 1)
													.toUpperCase()
											+ f.getName().substring(1),
									Boolean.TYPE);
								setter.invoke(pojoResource, new Boolean(cleanedLiteralValue)); // invoke it
								found = true;
							}
							catch( NoSuchMethodException e) { // no luck
							}
						}				
						
					}
				}
			}
		} catch (Exception e) {

			log.error("Exception during setting a property value of a graph ",
					e);
		}

	}

	/*
	 * public void populateByQuery( FacetResource pojoResource, String
	 * queryString) { long startTime = new Date().getTime(); Query query =
	 * QueryFactory.create(queryString) ; Field[] fields =
	 * pojoResource.getClass().getDeclaredFields();
	 * 
	 * try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
	 * ResultSet results = qexec.execSelect() ; for ( ; results.hasNext() ; ) {
	 * QuerySolution soln = results.nextSolution() ;
	 * 
	 * 
	 * for (Field f : fields) { if (f.isAnnotationPresent(OWLProperty.class)) {
	 * 
	 * OWLProperty ta = f.getAnnotation(OWLProperty.class);
	 * 
	 * if( soln.contains(ta.value())) {
	 * 
	 * Method setter; // setter =
	 * pojoResource.getClass().getMethod(Constants.SET +
	 * f.getName().substring(0, 1).toUpperCase() + f.getName().substring(1),
	 * String.class ); // setter.invoke(pojoResource, object.toString()); //
	 * invoke getXXX method // log.debug("set property " + ta.value() +
	 * " = " + object.toString()); } } }
	 * 
	 * 
	 * // log.debug(soln.toString());
	 * 
	 * 
	 * // RDFNode s = soln.get("s") ; // Get a result variable by name. //
	 * Resource p = soln.getResource("p") ; // Get a result variable - must be a
	 * resource // Literal o = soln.getLiteral("o") ; // Get a result variable -
	 * must be a literal } } log.debug("Query time: " + (new
	 * Date().getTime() - startTime) + " ms"); }
	 * 
	 */

	private String wildcardToRegex(String wildcardString) {
	    // The 12 is arbitrary, you may adjust it to fit your needs depending
	    // on how many special characters you expect in a single pattern.
	    StringBuilder sb = new StringBuilder(wildcardString.length() + 12);
	    sb.append('^');
	    for (int i = 0; i < wildcardString.length(); ++i) {
	        char c = wildcardString.charAt(i);
	        if (c == '*') {
	            sb.append(".*");
	        } else if (c == '?') {
	            sb.append('.');
	        } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
	            sb.append('\\');
	            sb.append(c);
	        } else {
	            sb.append(c);
	        }
	    }
	    sb.append('$');
	    return sb.toString();
	}
	
	public Model getModel() {
		return model;
	}

	public Reasoner getReasoner() {
		return reasoner;
	}
	
	public Dataset getDataset() {
		return dataset;
	}
	
	public Field getClassField( Class<?> type, String fieldName) throws Exception {
		
		Field[] fields = getAllClassFields( type);
		for( Field f:fields) {
			if( f.getName().equals(fieldName))
				return f;
		}
		return null;
		
	}
	
	private Field[] getAllClassFields( Class<?> type) {
		LinkedList<Field> fs = new LinkedList<Field>();
		getAllFieldsRec(fs, type);
		return fs.toArray(new Field[0]);
	}
	
	private List<Field> getAllFieldsRec(List<Field> fields, Class<?> type) {
	    fields.addAll(Arrays.asList(type.getDeclaredFields()));

	    if (type.getSuperclass() != null) {
	        getAllFieldsRec(fields, type.getSuperclass());
	    }

	    return fields;
	}
	
	/*
	 * Convert type of this resource. Potentially unsafe!
	 */
	public void convertType( String uri, String newType) {
		try {		
			Resource resource = null;
			if( uri != null) {
			// check if resource exists
				resource = model.getResource(model.expandPrefix(uri));
				
				RDFNode typeRes = model.getResource(model.expandPrefix(newType));
				
				if( model.containsResource(resource)) {
					model.removeAll(resource, RDF.type, (RDFNode) null);
				}
				model.add(resource, RDF.type, typeRes);    
				
				objectCache.remove(model.expandPrefix(uri));
			}

			
		} catch (Exception e) {
			log.error("Exception during modifying of a graph ", e);
		}
		
	}
	
	public GraphResource convertNamespace(GraphResource res) {
		 return convertNamespace(res,baseNs);
	}

	public GraphResource convertNamespace(GraphResource res, String namespace) {

		try {
		if( res != null && res.getUri() != null)

			res.setUri(namespace + res.getUri().substring(res.getUri().lastIndexOf('#')+1));
			return res;
		}
		catch( Exception e ) {
			log.error("Exception namespace convert, res:" + res);
			e.printStackTrace();
		}
		return null;
	}

	public String convertURINamespace(String uri) {
		return convertURINamespace(uri, baseNs);
	}

	public String convertURINamespace(String uri, String ns) {
		return ns + uri.substring(uri.lastIndexOf('#')+1);
	}
	
	public String getConvertedUri(String uri) {
		if( convertNamespaceMode) {
			for( String nm: namespaceMappings.keySet()) {
				if( uri.startsWith(nm)) {
					uri = convertURINamespace(namespaceMappings.get(nm));
				}
			}
		}
		return uri;
	}
	/*
	 *
	 */
	public boolean containsResource(String resUri) {
			Resource resource = null;
			resource = model.getResource(resUri);
			if( model.containsResource(resource)) {
				return true;
			}
			else
				return false;
	}
	
	
	
	/*
	 * Clears the resource object cache
	 */
	public void clearCache() {
		objectCache.clear();
	}

	public String getQueryPrefixMapping() {
		if( queryPrefixMapping != null)
			return queryPrefixMapping;
		queryPrefixMapping = new String();
		Map<String, String> nsmap = model.getNsPrefixMap();

		for (Entry<String, String> nsprefixEntry : nsmap.entrySet()) {
			String nsprefix = nsprefixEntry.getKey();
			StringBuilder sb = new StringBuilder();
			sb.append("PREFIX ");
			sb.append(nsprefix);
			sb.append(":   <");
			sb.append(nsmap.get(nsprefix));
			sb.append(">\n");
			queryPrefixMapping += sb.toString();
		}
		return queryPrefixMapping;

	}

	/* Mode for converting resource namespace when reading resources from graph. 
	 * @params HashMap containing namespace mappings <sourceNamespace, targetNamespace>
	 * 
	 */
	public void enableConvertNamespaceMode( HashMap<String, String> namespaceMappings) {
		this.convertNamespaceMode = true;
		this.namespaceMappings = namespaceMappings;
	}

	public void disableConvertNamespaceMode() {
		this.convertNamespaceMode = false;
	}

	
	
	public GraphResource getFromCache( String uri) {
		return objectCache.get(uri);
	}
	
	
	/* Put 
	 * 
	 */
	public void putToCache( GraphResource obj) {
		if( obj.getUri() == null ) {
			log.error("ERROR: trying to put a null object to cache! ");
			return;
		}
		GraphResource obj2 = objectCache.get(obj.getUri());
		
		if( obj2 != null && System.identityHashCode(obj2) != System.identityHashCode(obj)) {
			log.error("Trying to insert different object to cache!");
		}
		objectCache.put(obj.getUri(), obj);
	}
	
	
	public int countCacheObjects() {
		return objectCache.size();
	}
	
	public void dumpCacheObjects() {
		for( String uri: objectCache.keySet()) {
			log.debug( uri + ": " + objectCache.get(uri).getUri());
		}
	}
	
	
	
/*	public class QuadPointVisitor implements ItemVisitor {

		public List<Point> result;
		
		double minLat; 
		double minLon;
		double maxLat;
		double maxLon;
		String callerUri;
		public QuadPointVisitor( double minLat, double minLon, double maxLat, double maxLon,  List<Point> result, String callerUri) {
			this.minLat = minLat;
			this.minLon = minLon;
			this.maxLat = maxLat;
			this.maxLon = maxLon;
			this.result = result;
			this.callerUri = callerUri;
		}
		
		@Override
		public void visitItem(Object item) {
			Point p = (Point)item;
			if(p.getX() > minLon && p.getX() < maxLon && p.getY() > minLat && p.getY() < maxLat && !((String)(p.getUserData())).equals(callerUri)) {
				result.add(p);
			}
				
		}
		
	}
	
	
	public class QuadPointPathVisitor implements ItemVisitor {

		public List<Point> result;
		LineString path;
		String callerUri;
		String callerOrg;
		float maxDist;
		
		public QuadPointPathVisitor( List<Point> result, LineString path, float maxDist, String callerUri, String callerOrg) {
			this.path = path;
			this.result = result;
			this.maxDist = maxDist;
			this.callerUri = callerUri;
			this.callerOrg = callerOrg;

		}
		
		
		@Override
		public void visitItem(Object item) {
			Point p = (Point)item;
			if( path.distance(p) < maxDist && !((String)(p.getUserData())).equals(callerUri)&& !((String)(p.getUserData())).equals(callerOrg)) {
				result.add(p);
			}
			
		}
		
	
	}
	*/
	
	public List<GraphResource.Attribute> dumpResourceAttributes(String uri) {
		List<GraphResource.Attribute> props = new LinkedList<>();
		if( !this.containsResource(uri) ) {
			log.debug( "Resource not found in graph" );
			return null;
		}

		Resource s = model.getResource(model.expandPrefix(uri));
		StmtIterator iter = model.listStatements( new SimpleSelector(s, null, (RDFNode) null));
		GraphResource res = new GraphResource();
		res.setGraph(this);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement(); // get next statement
			Resource subject = stmt.getSubject(); // get the subject
			Property predicate = stmt.getPredicate(); // get the predicate
			RDFNode object = stmt.getObject(); // get the object
			
			props.add(res.new Attribute( predicate.getLocalName(), predicate.getURI(), object.toString()));
		}
		iter.close();
		return props;
	}
	
	public List<GraphResource.Attribute> dumpResourceIncomingAttributes(String uri) {
		List<GraphResource.Attribute> props = new LinkedList<>();
		if( !this.containsResource(uri) ) {
			log.debug( "Resource not found in graph" );
			return null;
		}

		Resource s = model.getResource(model.expandPrefix(uri));
		StmtIterator iter = model.listStatements( new SimpleSelector(null, null, s));
		GraphResource res = new GraphResource();
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement(); // get next statement
			Resource subject = stmt.getSubject(); // get the subject
			Property predicate = stmt.getPredicate(); // get the predicate
	//		RDFNode object = stmt.getObject(); // get the object
			
			props.add(res.new Attribute( predicate.getLocalName(), predicate.getURI(), subject.toString()));
		}
		iter.close();
		return props;
	}
	
	
}
