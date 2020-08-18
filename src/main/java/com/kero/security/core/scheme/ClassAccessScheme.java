package com.kero.security.core.scheme;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kero.security.core.config.PreparedAccessConfiguration;
import com.kero.security.core.config.PreparedAccessConfigurationImpl;
import com.kero.security.core.config.prepared.PreparedAction;
import com.kero.security.core.config.prepared.PreparedDenyRule;
import com.kero.security.core.config.prepared.PreparedGrantRule;
import com.kero.security.core.interceptor.DenyInterceptor;
import com.kero.security.core.managers.KeroAccessManager;
import com.kero.security.core.property.Property;
import com.kero.security.core.role.Role;
import com.kero.security.core.rules.AccessRule;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.matcher.ElementMatchers;

public class ClassAccessScheme extends AccessSchemeBase implements InvocationHandler {

	private static Logger LOGGER = LoggerFactory.getLogger("KeroSecurity");
	
	private Class<?> proxyClass = null;
	
	private Field originalField = null;
	private Field pacField = null;
	
	private Constructor proxyConstructor;
	
	private Map<Set<Role>, PreparedAccessConfiguration> configsCache = new HashMap<>();
	
	public ClassAccessScheme() {
		super();
	
	}
	
	public ClassAccessScheme(KeroAccessManager manager, Class<?> type) throws Exception {
		super(manager, type);
	
		if(!Modifier.isAbstract(type.getModifiers())) {
			
			this.proxyClass = new ByteBuddy()
					.subclass(type)
					.defineField("original", type, Visibility.PRIVATE)
					.defineField("pac", PreparedAccessConfiguration.class, Visibility.PRIVATE)
					.defineConstructor(Visibility.PUBLIC)
					.withParameters(type, PreparedAccessConfiguration.class)
					.intercept(MethodCall.invoke(type.getConstructor()).andThen(FieldAccessor.ofField("original").setsArgumentAt(0).andThen(FieldAccessor.ofField("pac").setsArgumentAt(1))))
					.method(ElementMatchers.isPublic())
					.intercept(InvocationHandlerAdapter.of(this))
					.make()
					.load(ClassLoader.getSystemClassLoader())
					.getLoaded();
			
			this.originalField = this.proxyClass.getDeclaredField("original");
			this.originalField.setAccessible(true);
	 
			this.pacField = this.proxyClass.getDeclaredField("pac");
			this.pacField.setAccessible(true);
			
			this.proxyConstructor = this.proxyClass.getConstructor(this.type, PreparedAccessConfiguration.class);
			this.proxyConstructor.setAccessible(true);
		}
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		
		Object original = originalField.get(proxy);
		PreparedAccessConfiguration pac = (PreparedAccessConfiguration) pacField.get(proxy);
	
		return pac.process(original, method, args);
	}
	
	public <T> T protect(T object, Set<Role> roles) throws Exception {
		
		PreparedAccessConfiguration config = configsCache.get(roles);
		
		if(config == null) {
	
			config = prepareAccessConfiguration(roles);
			configsCache.put(Collections.unmodifiableSet(new HashSet<>(roles)), config);
		}
		
		return (T) this.proxyConstructor.newInstance(object, config);
	}
	
	private PreparedAccessConfiguration prepareAccessConfiguration(Set<Role> roles) {
		
		String rolesList = "[";
		
		for(Role role : roles) {
			
			rolesList += role.getName()+" ";
		}
		
		rolesList = rolesList.trim()+"]";
		
		LOGGER.debug("Prepare access configuration for "+type.getCanonicalName()+" roles: "+rolesList);
		
		Map<String, PreparedAction> preparedActions = new HashMap<>();

		Set<Property> properties = getProperties();
		
		properties.forEach((property)-> {
			
			String propertyName = property.getName();
			
			Set<Role> significantRoles = new HashSet<>(roles);
			
			List<AccessRule> rules = property.getRules();
			 
			for(AccessRule rule : rules) {
 
				if(!rule.manage(significantRoles)) continue;
 
				if(rule.accessible(significantRoles)) {
 
					preparedActions.put(propertyName, new PreparedGrantRule(this, roles));
					return;
				}
				else if(rule.isDisallower()) {
 
					significantRoles.removeAll(rule.getRoles());
				}
			}
			
			DenyInterceptor interceptor = determineInterceptor(property, roles);
			
			if(interceptor != null) {
				
				preparedActions.put(propertyName, interceptor.prepare(roles));
				return;
			}
			
			if(significantRoles.isEmpty()) {
			
				preparedActions.put(propertyName, new PreparedDenyRule(this, roles));
				return;
			}

			if(property.hasDefaultRule()) {
			
				preparedActions.put(propertyName, property.getDefaultRule().prepare(this, roles));
				return;
			}
			else {
				
				AccessRule defaultRule = findDefaultRule();
				
				if(defaultRule != null) {
					
					preparedActions.put(propertyName, defaultRule.prepare(this, roles));
					return;
				}
				else {
					
					preparedActions.put(propertyName, this.manager.getDefaultRule().prepare(this, roles));
					return;
				}
			}
		});
		
		PreparedAction defaultTypeAction = findDefaultRule().prepare(this, roles);
		
		return new PreparedAccessConfigurationImpl(this, preparedActions, defaultTypeAction);
	}
	
	private DenyInterceptor determineInterceptor(Property property, Set<Role> roles) {
	
		int maxOverlap = 0;
		int minTrash = Integer.MAX_VALUE;
		DenyInterceptor result = null;
		
		for(DenyInterceptor interceptor : property.getInterceptors()) {
			
			Set<Role> interceptorRoles = interceptor.getRoles();
			
			int overlap = 0;
			int trash = 0;
			
			for(Role interceptorRole : interceptorRoles) {
				
				if(roles.contains(interceptorRole)) {
					
					overlap++;
				}
				else {
					
					trash++;
				}
			}
			
			if(overlap > maxOverlap) {
				
				maxOverlap = overlap;
				minTrash = trash;
				result = interceptor;
			}
			else if(overlap == maxOverlap && trash < minTrash) {
				
				maxOverlap = overlap;
				minTrash = trash;
				result = interceptor;
			}
		}
	
		if(maxOverlap == 0) {
			
			return property.getDefaultInterceptor();
		}
		
		return result;
	}
	
	private AccessRule findDefaultRule() {
		
		if(this.hasDefaultRule()) return this.getDefaultRule();
		
		if(this.inherit) {
			
			Class<?> superClass = this.type.getSuperclass();
			
			while(superClass != Object.class) {
				
				if(manager.hasScheme(superClass)) {
					
					AccessScheme scheme = manager.getScheme(superClass);
				
					if(scheme.hasDefaultRule()) {
						
						return scheme.getDefaultRule();
					}
				}
				
				superClass = superClass.getSuperclass();
			}
		}
		
		return manager.getDefaultRule();
	}
	
	public void collectProperties(Map<String, Property> complexProperties) {
		
		collectLocalProperties(complexProperties);
		
		if(this.inherit) {
			
			collectFromInterfaces(complexProperties);
			collectFromSuperclass(complexProperties);
		}
	}
	
	protected void collectFromSuperclass(Map<String, Property> complexProperties) {
		
		Class<?> superClass = type.getSuperclass();
		
		while(superClass != Object.class) {
			
			AccessScheme supeclassScheme = manager.getOrCreateScheme(superClass);

			supeclassScheme.collectProperties(complexProperties);

			superClass = superClass.getSuperclass();
		}
	}
}