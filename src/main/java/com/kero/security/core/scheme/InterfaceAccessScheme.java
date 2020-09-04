package com.kero.security.core.scheme;

import java.util.Map;

import com.kero.security.core.property.Property;
import com.kero.security.managers.KeroAccessManager;

public class InterfaceAccessScheme extends AccessSchemeBase {

	public InterfaceAccessScheme() {
		super();
		
	}
	
	public InterfaceAccessScheme(KeroAccessManager manager, Class<?> type) {
		super(manager, type);
		
	}
	
	public void collectProperties(Map<String, Property> complexProperties) {
		
		collectLocalProperties(complexProperties);
		
		if(this.inherit) {
			
			collectFromInterfaces(complexProperties);
		}
	}
}