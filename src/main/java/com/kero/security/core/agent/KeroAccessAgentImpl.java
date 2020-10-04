package com.kero.security.core.agent;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kero.security.core.access.annotations.Access;
import com.kero.security.core.configurator.KeroAccessConfigurator;
import com.kero.security.core.role.Role;
import com.kero.security.core.role.storage.RoleStorage;
import com.kero.security.core.scheme.AccessScheme;
import com.kero.security.core.scheme.ClassAccessScheme;
import com.kero.security.core.scheme.configurator.AccessSchemeConfigurator;
import com.kero.security.core.scheme.storage.AccessSchemeStorage;

public class KeroAccessAgentImpl implements KeroAccessAgent {
	
	protected static Logger LOGGER = LoggerFactory.getLogger("Kero-Security");
	
	protected RoleStorage roleStorage = RoleStorage.create();
	protected AccessSchemeStorage schemeStorage = AccessSchemeStorage.create();
	protected KeroAccessConfigurator configurator = new KeroAccessConfigurator(this);
		
	protected Access defaultAccess = Access.GRANT;
	
	protected ClassLoader proxiesClassLoader = ClassLoader.getSystemClassLoader();
	
	protected Set<Class> ignoreList = new HashSet<>();

	protected Map<Class, String> aliasesMap = new HashMap<>();

	protected Set<AccessSchemeConfigurator> autoConfigurators = new HashSet<>();
	
	KeroAccessAgentImpl() {
		
		ignoreType(String.class);
		
		ignoreType(Integer.class);
		ignoreType(int.class);
		
		ignoreType(Long.class);
		ignoreType(long.class);
		
		ignoreType(Float.class);
		ignoreType(float.class);
		
		ignoreType(Double.class);
		ignoreType(double.class);
		
		ignoreType(Character.class);
		ignoreType(char.class);
		
		ignoreType(Boolean.class);
		ignoreType(boolean.class);
	}
	
	public void addConfigurator(AccessSchemeConfigurator configurator) {
		
		this.autoConfigurators.add(configurator);
	}
	
	public void setTypeAliase(String aliase, Class<?> type) {
		
		this.aliasesMap.put(type, aliase);
	}
	
	public void ignoreType(Class<?> type) {
		
		ignoreList.add(type);
	}
	
	@Override
	public boolean hasScheme(Class<?> rawType) {
		
		return schemeStorage.has(rawType);
	}

	@Override
	public AccessScheme getScheme(Class<?> rawType) {
		
		return schemeStorage.getOrDefault(rawType, AccessScheme.EMPTY);
	}
	
	@Override
	public AccessScheme getSchemeByAlise(String aliase) {
		
		return schemeStorage.getByAliase(aliase);
	}

	public AccessScheme getOrCreateScheme(Class<?> rawType){
		
		return hasScheme(rawType) ? getScheme(rawType) : createScheme(rawType);
	}
	
	public AccessScheme createScheme(Class<?> rawType) {
		
		if(rawType == null) return AccessScheme.EMPTY;
		
		if(rawType.isInterface()) throw new RuntimeException("Can't create scheme for interface!");
		
		AccessScheme scheme = null;
		
		String aliase = rawType.getSimpleName();
		
		if(aliasesMap.containsKey(rawType)) {
			
			aliase = aliasesMap.get(rawType);
		}

		LOGGER.debug("Creating access scheme for class: "+rawType.getCanonicalName());
		scheme = new ClassAccessScheme(this, aliase, rawType);
		
		for(AccessSchemeConfigurator ac : autoConfigurators) {
			
			ac.configure(scheme);
		}
		
		schemeStorage.add(scheme);
		
		return scheme;
	}

	@Override
	public <T> T protect(T object, Collection<Role> roles) {
		
		if(object == null) return null;
		
		if(this.ignoreList.contains(object.getClass())) return object;

		try {
			
			ClassAccessScheme scheme = (ClassAccessScheme) getOrCreateScheme(object.getClass());
				
			return scheme.protect(object, roles);
		}
		catch(Exception e) {
			
			throw new RuntimeException(e);
		}
	}
	
	public String extractPropertyName(String rawName) {
		
		if(rawName.startsWith("get")) {
			
			rawName = rawName.replaceFirst("get", "");
		}
		
		rawName = rawName.toLowerCase();
	
		return rawName;
	}
	
	@Override
	public Access getDefaultAccess() {
		
		return this.defaultAccess;
	}

	@Override
	public ClassLoader getClassLoader() {
		
		return this.proxiesClassLoader;
	}
	
	@Override
	public KeroAccessConfigurator getKeroAccessConfigurator() {
		
		return this.configurator;
	}

	@Override
	public Role createRole(String name) {
		
		return this.roleStorage.create(name);
	}

	@Override
	public Role getRole(String name) {
		
		return this.roleStorage.get(name);
	}

	@Override
	public Role hasRole(String name) {
		
		return this.roleStorage.create(name);
	}

	@Override
	public Role getOrCreateRole(String name) {
		
		return this.roleStorage.getOrCreate(name);
	}

	@Override
	public Set<Role> getOrCreateRole(Collection<String> names) {
		
		return this.roleStorage.getOrCreate(names);
	}

	@Override
	public Set<Role> getOrCreateRole(String[] names) {
		
		return this.roleStorage.getOrCreate(names);
	}

	@Override
	public RoleStorage getRoleStorage() {
		
		return this.roleStorage;
	}

	@Override
	public AccessSchemeStorage getSchemeStorage() {
		
		return this.schemeStorage;
	}
}
