package com.kero.security.lang.nodes;

import java.util.List;

import com.kero.security.core.KeroAccessManager;
import com.kero.security.core.scheme.AccessScheme;

public class SchemeNode extends KsdlNodeBase implements KsdlRootNode {

	private String typeAliase;
	
	private DefaultRuleNode defaultRule;
	
	private List<PropertyNode> properties;
	
	public SchemeNode(String typeAliase, DefaultRuleNode defaultRule, List<PropertyNode> properties) {
		
		this.typeAliase = typeAliase;
		this.defaultRule = defaultRule;
		this.properties = properties;
	}
	
	public void interpret(KeroAccessManager manager) {
		
		AccessScheme scheme = manager.getSchemeByAlise(this.typeAliase);
	
		this.interpret(scheme);
	}
	
	public void interpret(AccessScheme scheme) {
		
		defaultRule.interpret(scheme.getManager(), scheme);
		
		properties.forEach((prop)-> prop.interpret(scheme));
	}
	
	public String getTypeAlise() {
		
		return this.typeAliase;
	}
}