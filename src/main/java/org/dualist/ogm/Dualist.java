package org.dualist.ogm;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.util.FileManager;
import org.apache.log4j.Logger;

import org.dualist.ogm.annotations.OWLClass;
import org.dualist.ogm.annotations.OWLProperty;
import org.dualist.ogm.pojo.GraphResource;

public class Dualist {

	private static final Logger log = Logger
			.getLogger(Dualist.class.getName());

	HashMap<String, GraphResource> objects = new HashMap<>();

	 protected Model model;
	 protected String baseNs;

	 protected Reasoner reasoner;
	 
	/*
	 * Jena/OWL - Java POJO mapping layer.
	 * 
	 * Mapping is bidirectional: from OWL graph to Java and Java to OWL -Maps
	 * the OWL classes and properties to Java using @OWLResource
	 * and @OWLProperty annotations. -Java classes must be inherited from
	 * FacetResource class. -OWL - pojo mapping relies on method naming
	 * convention: all annotated Java properties must have public getter and
	 * setter methods.
	 */
	public Dualist() {
		model = ModelFactory.createDefaultModel();
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
	
	/* Initiate model reasoner (RDFS) and replace the original model with inferred model.
	 * 
	 * https://jena.apache.org/documentation/inference/#RDFSintro
	 * 
	 */
	public void initReasoner() {
	       reasoner = ReasonerRegistry.getRDFSReasoner();
	       InfModel inf = ModelFactory.createInfModel(reasoner, model);
	       log.info("Reasoner initiated: " + reasoner.toString());
	       model = inf; 
	}
	
	/* Initiate model reasoner (OWL) and replace the original model with inferred model.
	 * 
	 * https://jena.apache.org/documentation/inference/#OWLintro
	 */
	public void initOWLReasoner() {
	       reasoner = ReasonerRegistry.getOWLReasoner();
	       InfModel inf = ModelFactory.createInfModel(reasoner, model);
	       log.info("Reasoner initiated: " + reasoner.toString());
	       model = inf; 
	}

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
			Class<GraphResource> c = (Class<GraphResource>) res.getClass();
			if (c.isAnnotationPresent(OWLClass.class)) {

				OWLClass ta = c.getAnnotation(OWLClass.class);

				// Get resource class for example sdm:Arm
				resourceClass = model
						.getResource(model.expandPrefix(ta.value()));

				String uri = res.getUri();

				if (uri != null) {
					// check if resource exists
					resource = model.getResource(uri);

				}
				// resource does not exist.create new
				if (resource == null || !model.contains(resource, null)) {
					if (uri == null) { // if URI is not set, create a new URI
										// with random hash
						uri = namespace + resourceClass.getLocalName() + "."
								+ UUID.randomUUID().toString();
						res.setUri(uri);
					}

					resource = model.createResource(uri, resourceClass);
					// res.setUri(uri);
					objects.put(uri, res);
				} else {
					return resource;
				}
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

			Field[] fields = res.getClass().getDeclaredFields();
			for (Field f : fields) {
				if (f.isAnnotationPresent(OWLProperty.class)) {

					OWLProperty ta = f.getAnnotation(OWLProperty.class);

					Method getter = res.getClass()
							.getMethod("get"
									+ f.getName().substring(0, 1).toUpperCase()
									+ f.getName().substring(1), null);

					Object value = getter.invoke(res); // invoke getXXX method

					if( value == null) {
						log.info("Method call returned null value: " + getter.getName());
					}
					else if (value instanceof String) {

						Property property = model
								.getProperty(model.expandPrefix(ta.value()));
						resource.addProperty(property, value.toString());
					} 
					else if (value instanceof Integer) {

						Property property = model
								.getProperty(model.expandPrefix(ta.value()));
						resource.addProperty(property, value.toString());
					}
					else if (value instanceof Float) {

						Property property = model
								.getProperty(model.expandPrefix(ta.value()));
						resource.addProperty(property, value.toString());
					}		
					else if (value instanceof Double) {

						Property property = model
								.getProperty(model.expandPrefix(ta.value()));
						resource.addProperty(property, value.toString());
					}	
					else if (value instanceof Boolean) {

						Property property = model
								.getProperty(model.expandPrefix(ta.value()));
						resource.addProperty(property, value.toString());
					}	
					else if (value instanceof GraphResource) {
						Property property = model
								.getProperty(model.expandPrefix(ta.value()));
						Resource propResource = create(((GraphResource) value));

						resource.addProperty(property, propResource);

					} else if (value instanceof List) {
						for (GraphResource fr : (List<GraphResource>) value) {
							Property property = model.getProperty(
									model.expandPrefix(ta.value()));
							Resource propResource = create(fr);
							resource.addProperty(property, propResource);
						}
					}
					 else {
						
						log.error("Error: unknown return value type for method " + getter.getName());
					}
				}
			}
			return resource;

		} catch (Exception e) {
			log.error("Exception during creating a graph ", e);
		}
		return null;
	}

	/*
	 * Modifies a POJO resource in the graph.
	 * 
	 */
	public void modify(GraphResource res) {
		try {
			System.out.println("Dualist.modify ");

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

			Field[] fields = res.getClass().getDeclaredFields();
			for (Field f : fields) {
				if (f.isAnnotationPresent(OWLProperty.class)) {

					OWLProperty ta = f.getAnnotation(OWLProperty.class);

					Method getter = res.getClass()
							.getMethod("get"
									+ f.getName().substring(0, 1).toUpperCase()
									+ f.getName().substring(1), null);

					Object value = getter.invoke(res); // invoke getXXX method

					if (value instanceof String) {

						Property property = model
								.getProperty(model.expandPrefix(ta.value()));

						resource.removeAll(property);

						resource.addProperty(property, value.toString());
					} else if (value instanceof GraphResource) {
						Property property = model
								.getProperty(model.expandPrefix(ta.value()));

						resource.removeAll(property);

						Resource propResource = create(((GraphResource) value));
						resource.addProperty(property, propResource);

					} else if (value instanceof List) {
						Property property = model
								.getProperty(model.expandPrefix(ta.value()));
						resource.removeAll(property);

						for (GraphResource fr : (List<GraphResource>) value) {

							Resource propResource = create(fr);
							resource.addProperty(property, propResource);
						}
						// Property property = model.getRDFNode(ta.value());
						// resource.addProperty(property, value);
					}

				}
			}

		} catch (Exception e) {
			log.error("Exception during modifying of a graph ", e);
		}
	}

	/*
	 * Delete a POJO resource from the graph. Also incoming references are
	 * removed from the graph!!
	 * 
	 */
	public void delete(GraphResource res) {
		System.out.println("Dualist.delete ");
		try {
			Resource resourceClass;
			Resource resource = null;

			String uri = res.getUri();

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

			// remove statements where resource is subject
			model.removeAll(resource, null, (RDFNode) null);
			// remove statements where resource is object
			model.removeAll(null, null, resource);

			objects.remove(uri);

		} catch (Exception e) {
			log.error("Exception during deleting of a graph ", e);
		}
	}

	
	/*
	 *  Queries for graph resources by an attribute name and value
	 */
	public List<GraphResource> queryByAttributeValue(Class resourceClass,
			String property, String value) {
		List<GraphResource> resPojoList = new LinkedList<>();
		try {
		
			if(!value.contains("http://") && !value.contains(":")) //TODO FIX ME, ugly code
				value = "\"" + value + "\"";
		
			StmtIterator iter = model
					.listStatements(
							new SimpleSelector((Resource) null,
									ResourceFactory.createProperty(
											model.expandPrefix(property)),
									value));

			String queryString = "SELECT * where {?result <"
					+ model.expandPrefix(property) + "> "+ value+ "}";
			Map<String, String> nsmap = model.getNsPrefixMap();

			for (Entry<String, String> nsprefixEntry : nsmap.entrySet()) {
				String nsprefix = nsprefixEntry.getKey();
				StringBuilder sb = new StringBuilder();
				sb.append("PREFIX ");
				sb.append(nsprefix);
				sb.append(":   <");
				sb.append(nsmap.get(nsprefix));
				sb.append(">\n");
				sb.append(queryString);
				queryString = sb.toString();

			}

			long startTime = new Date().getTime();

			Query query = QueryFactory.create(queryString);
			try (QueryExecution qexec = QueryExecutionFactory.create(query,
					model)) {
				ResultSet results = qexec.execSelect();

				for (; results.hasNext();) {
					QuerySolution soln = results.nextSolution();
					// System.out.println(soln.toString());
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

	
	/* Perform a query with relative property path from originating resource 
	 * 
	 */
	public List<GraphResource> queryRelative(String resUri, String propertyPath) {
		if(resUri.contains("http://")) resUri = "<" + resUri + ">";
		return query( GraphResource.class, "SELECT * where { " + resUri + " " + propertyPath + " ?result. }" );   
	}
	
	/* Perform a query with relative property path from originating resource 
	 * 
	 */
	public List<GraphResource> queryRelative(GraphResource pojoResource, String propertyPath) {
		return query( GraphResource.class, "SELECT * where { <" + pojoResource.getUri() + "> " + propertyPath + " ?result. }" );   
	}

	
	public GraphResource populateByAttribute(Class resourceClass,
			String property, String value) {
		List<GraphResource> res = queryByAttributeValue(resourceClass, property,
				value);
		if (res.size() == 0) {
			return null;
		}
		if( res.size() > 1) {
			log.info("Warning: populate method returns more than 1 result!");
		}
		return res.get(0);
	}

	
	/*
	 * A direct sparql query to retrieve results. 
	 * 
	 * NOTE: resource variable containing resource URIs is ?result
	 */
	public List<GraphResource> query(Class resourceClass, String sparqlQuery) {
		
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
					// System.out.println(soln.toString());
					
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

	
	
	/*
	 * Get all graph resource of a specific type graphType, instantiated into objects of type resourceClass 
	 * 
	 * 
	 */
	public List<GraphResource> getAll(Class resourceClass,
			String graphType) {
		List<GraphResource> resPojoList = new LinkedList<>();
		try {
			StmtIterator iter = model.listStatements(
				new SimpleSelector(null, ResourceFactory.createProperty( model.expandPrefix(Constants.TYPE) ), ResourceFactory.createResource( model.expandPrefix(graphType) )));
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement(); // get next statement
				Resource subject = stmt.getSubject(); // get the subject
				// System.out.println(soln.toString());
				

				GraphResource resource;
				if (objects.containsKey(subject.toString())) {
					resource = objects.get(subject.toString());
				} else {
					resource = (GraphResource) Class
							.forName(resourceClass.getName()).newInstance();
					// Populate POJO and direct subclasses
					populate(resource, subject);
					objects.put(subject.toString(), resource);
				}
				resPojoList.add(resource);
			}
		} catch (Exception e) {
			log.error("Exception during querying of a graph ", e);
		}
		return resPojoList;
	}
	
	/* 
	 * Populate a POJO by URI. Populate means that the object's attributes will be retrieved from corresponding instance at the graph.  
	 * 
	 * Resource URI needs to be set in the object before calling the method!
	 */
	public GraphResource populate(GraphResource resource) {
		if(resource.getUri() == null) {
			log.error("Called a populate reource with null URI value!" );
			return null;
		}
		resource = populate(resource, resource.getUri());
		return resource;
	}
	
	/* 
	 * Get (or populate) a POJO by URI
	 */
	public GraphResource populate(GraphResource resource, String uri) {

		Resource s = model.getResource(model.expandPrefix(uri));

		if (objects.containsKey(s.toString())) {
			resource = objects.get(s.toString());
		} else {
			objects.put(s.toString(), resource);
			// Populate POJO and direct subclasses
			populate(resource, s);

		}

		return resource;
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
	}

	/*
	 * Populate a POJO object from a graph resource hierarchically.
	 * 
	 * Verify that no circular references exist in the graph
	 * 
	 */
	protected void populate(GraphResource pojoResource,
			Resource resource) {

		pojoResource.setUri(resource.getURI());

		StmtIterator iter = model.listStatements(
				new SimpleSelector(resource, null, (RDFNode) null));
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement(); // get next statement
			Resource subject = stmt.getSubject(); // get the subject
			Property predicate = stmt.getPredicate(); // get the predicate
			RDFNode object = stmt.getObject(); // get the object
			
			if (object instanceof Resource) {
				// object is a resource
				if (predicate.toString().endsWith("ns#type")) {
					if( !object.toString().contains("owl") && !object.toString().contains("rdfs") && !object.toString().contains("Class")) {
						String[] types = pojoResource.getTypes();
						types = ArrayUtils.add(types, object.toString());
						pojoResource.setTypes(types);
					}
					continue; // skip RDF type predicate
				}

				instantiateResourceProperty(pojoResource, predicate, object);
				
				// add property if defined to be populated for this pojo
				if( pojoResource.isPopulateProperties()) { 
					List<GraphResource.Attribute> props = pojoResource.getAttributes();
					if( props == null)
						props = new LinkedList<GraphResource.Attribute>();
					
					props.add(pojoResource.new Attribute( predicate.getLocalName(), ((Resource) object).getURI()));
					pojoResource.setAttributes(props);
				}
			
			} else {
				// object is a literal

				setPropertyValue(pojoResource, predicate, object);
				
				// add property if defined to be populated for this pojo
				if( pojoResource.isPopulateProperties()) { 
					List<GraphResource.Attribute> props = pojoResource.getAttributes();
					if( props == null)
						props = new LinkedList<GraphResource.Attribute>();
					props.add(pojoResource.new Attribute( predicate.getLocalName(), object.toString()));
					pojoResource.setAttributes(props);
				}
				
			}
		}

		populateQueryProperties(pojoResource);

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
			 * System.out.println("added property " + ta.value() + " = " +
			 * value.toString()); } }
			 */

			Field[] fields = res.getClass().getDeclaredFields();
			for (Field f : fields) {
				if (f.isAnnotationPresent(OWLProperty.class)) {
					OWLProperty ta = f.getAnnotation(OWLProperty.class);
					if (ta.query().length() > 0) {
						String queryString = ta.query().replace("?resource",
								"<" + res.getUri() + ">");

						Map<String, String> nsmap = model.getNsPrefixMap();

						for (Entry<String, String> nsprefixEntry : nsmap
								.entrySet()) {
							String nsprefix = nsprefixEntry.getKey();
							StringBuilder sb = new StringBuilder();
							sb.append("PREFIX ");
							sb.append(nsprefix);
							sb.append(":   <");
							sb.append(nsmap.get(nsprefix));
							sb.append(">\n");
							sb.append(queryString);
							queryString = sb.toString();

						}

						long startTime = new Date().getTime();

						Query query = QueryFactory.create(queryString);
						try (QueryExecution qexec = QueryExecutionFactory
								.create(query, model)) {
							ResultSet results = qexec.execSelect();

							List<GraphResource> resPojoList = new LinkedList<>();

							for (; results.hasNext();) {
								QuerySolution soln = results.nextSolution();
								// System.out.println(soln.toString());
								Resource s = soln.getResource("result");
								String pojoClass = f.getGenericType()
										.toString();
								pojoClass = pojoClass.substring(
										pojoClass.indexOf("<") + 1,
										pojoClass.indexOf(">"));
								GraphResource instance;
								if (objects.containsKey(s.toString())) {
									instance = objects.get(s.toString());
								} else {
									instance = (GraphResource) Class
											.forName(pojoClass).newInstance();
									objects.put(s.toString(), instance);
									populate(instance, s);
								}
								resPojoList.add(instance);
							}

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

					}
				}
			}

		} catch (Exception e) {
			log.error("Exception during querying of a graph ", e);
		}
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
			Field[] fields = pojoResource.getClass().getDeclaredFields();
			for (Field f : fields) {
				if (f.isAnnotationPresent(OWLProperty.class)) {
					OWLProperty ta = f.getAnnotation(OWLProperty.class);
					if (ta.value().equals(predicate.toString())
							|| model.expandPrefix(ta.value())
									.equals(predicate.toString())) {
						if (f.getType().toString()
								.contains("org.dualist.ogm.pojo.URI")) {
							org.dualist.ogm.pojo.URI uri = new org.dualist.ogm.pojo.URI();
							uri.setUri(object.toString());
							Method setter;
							setter = pojoResource.getClass().getMethod(
									Constants.SET
											+ f.getName().substring(0, 1)
													.toUpperCase()
											+ f.getName().substring(1),
									org.dualist.ogm.pojo.URI.class);
							setter.invoke(pojoResource, uri); // invoke getXXX
						} else if (f.getType().toString().contains("String")) {
							Method setter;
							setter = pojoResource.getClass().getMethod(
									Constants.SET
											+ f.getName().substring(0, 1)
													.toUpperCase()
											+ f.getName().substring(1),
									String.class);
							setter.invoke(pojoResource, object.toString()); // getXXX 
						} else if (f.getType().toString()
								.contains("java.util.List")) {
							String pojoClass = f.getGenericType().toString();
							pojoClass = pojoClass.substring(
									pojoClass.indexOf("<") + 1,
									pojoClass.indexOf(">")); // TODO: not clean
							GraphResource instance;
							if (objects.containsKey(object.toString())) {
								instance = objects.get(object.toString());
							} else {
								instance = (GraphResource) Class
										.forName(pojoClass).newInstance();
								Resource subRes = model
										.getResource(object.toString());
								objects.put(object.toString(), instance);
								populate(instance, subRes);
							}
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
						} else {
							GraphResource instance;
							if (objects.containsKey(object.toString())) {
								instance = objects.get(object.toString());
							} else {
								instance = (GraphResource) Class
										.forName(f.getType().getName())
										.newInstance();
								objects.put(object.toString(), instance);
								Resource subRes = model
										.getResource(object.toString());
								populate(instance, subRes);
							}
							Method setter;
							setter = pojoResource.getClass().getMethod(
									Constants.SET
											+ f.getName().substring(0, 1)
													.toUpperCase()
											+ f.getName().substring(1),
									Class.forName(f.getType().getName()));
							setter.invoke(pojoResource, instance); // invoke getXXX 
						}
						break;
					}
				}
			}
		} catch (InvocationTargetException | IllegalAccessException
				| ClassNotFoundException | NoSuchMethodException
				| InstantiationException e) {
			log.error("Exception during creating a graph ", e);
		}

	}

	private void setPropertyValue(GraphResource pojoResource, Property predicate,
			RDFNode object) {
		try {
			Field[] fields = pojoResource.getClass().getDeclaredFields();
			for (Field f : fields) {
				if (f.isAnnotationPresent(OWLProperty.class)) {

					OWLProperty ta = f.getAnnotation(OWLProperty.class);
					// System.out.println("OWL prop: " + ta.value() + ",
					// expanded " + model.expandPrefix(ta.value()) + ",
					// predicate " + predicate.toString() );

					if (ta.value().equals(predicate.toString())
							|| model.expandPrefix(ta.value())
									.equals(predicate.toString())) {
						Method setter;
						boolean found = false;
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
									Integer.class);
								setter.invoke(pojoResource, new Integer(object.toString())); // invoke it
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
									Float.class);
								setter.invoke(pojoResource, new Float(object.toString())); // invoke it
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
									Double.class);
								setter.invoke(pojoResource, new Double(object.toString())); // invoke it
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
									Boolean.class);
								setter.invoke(pojoResource, new Boolean(object.toString())); // invoke it
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
	 * invoke getXXX method // System.out.println("set property " + ta.value() +
	 * " = " + object.toString()); } } }
	 * 
	 * 
	 * // System.out.println(soln.toString());
	 * 
	 * 
	 * // RDFNode s = soln.get("s") ; // Get a result variable by name. //
	 * Resource p = soln.getResource("p") ; // Get a result variable - must be a
	 * resource // Literal o = soln.getLiteral("o") ; // Get a result variable -
	 * must be a literal } } System.out.println("Query time: " + (new
	 * Date().getTime() - startTime) + " ms"); }
	 * 
	 */

	public Model getModel() {
		return model;
	}

}
