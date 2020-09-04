package com.kero.security.core.lang.lexems;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.kero.security.core.TestObject;
import com.kero.security.lang.KsdlLexer;
import com.kero.security.lang.KsdlParser;
import com.kero.security.lang.nodes.TypeNode;
import com.kero.security.lang.tokens.KsdlToken;
import com.kero.security.managers.KeroAccessManager;
import com.kero.security.managers.KeroAccessManagerImpl;

public class KsdlLexerTest {

	@Test
	public void test() throws IOException, InterruptedException {
		
		KeroAccessManager manager = new KeroAccessManagerImpl();
		
		KsdlLexer lexer = new KsdlLexer();
		
		List<KsdlToken> tokens = lexer.tokenize(new String(Files.readAllBytes(new File("test_syntax_file.k-s").toPath())));
		
		for(KsdlToken token : tokens) {
			
			System.out.println(token);
		}
		
		Thread.sleep(5000);
		
		KsdlParser parser = new KsdlParser(manager);
	
		TypeNode node = (TypeNode) parser.parse(tokens).iterator().next();
		
		node.interpret(manager);
		
		manager.protect(new TestObject("test text"), "COMMON").getText();
	}
}
