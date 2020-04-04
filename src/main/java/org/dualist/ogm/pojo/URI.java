package org.dualist.ogm.pojo;

public class URI {

	public final String uri;


	public URI(String uri){
		this.uri = uri;
	}

	/**
	 * @return the uri
	 */
	public String getUri() {
		return uri;
	}

	public String toString() {
		return uri;
	}
	
	  @Override
	    public boolean equals(Object object)
	    {
		  if( ((URI)object).uri.equals(uri)) {
			   return true;
		  }
		  return false;
	    }

}
