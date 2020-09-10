package com.kero.security.lang.provider.resource;

public interface KsdlTextResource {

	public String getRawText();
	
	public static KsdlTextResource addCacheWrap(KsdlTextResource resource) {
		
		return new CachedTextResource(resource);
	}
}