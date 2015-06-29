package org.filestacker.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class TextStackerTest {

	TextStacker stacker;
	
	private static final int TEXT_SIZE = 256*1024; // 256kb
	private static final String STACK_PATH = "/tmp/stacker/";

	
	@BeforeClass
	public static void setUp() throws Exception {
		BasicConfigurator.configure();
	}

	@After
	public void tearDown() throws Exception {
		if (stacker != null) {
			stacker.close();
			FileUtils.cleanDirectory(new File(stacker.stacksPath));
		}
	}

	@Test
	public void testBasicAddAndSearch() throws IOException {
		stacker = new TextStacker(STACK_PATH);
		int ndocs = 50;

		List<String> strings = new ArrayList<String>();
		for (int i=0; i<ndocs * 2; i++) 
			strings.add(RandomStringUtils.randomAlphabetic(TEXT_SIZE));
		
		// ##############################
		// ### Criacao e testes basicos de seguranca
		for (int i = 0; i < ndocs; i++) {
			stacker.addText("file" + i, strings.get(i));
		}
		stacker.optimize();
		for (int i = 0; i < ndocs; i++) {
			assertEquals(strings.get(i), stacker.searchText(i));
		}
		
		assertEquals(ndocs, stacker.totalDocs);
		assertEquals(ndocs, stacker.nextStackId);
		// ##############################

		for (int i = ndocs; i < ndocs * 2; i++) {
			stacker.addText("file" + i, strings.get(i));
		}
		
		stacker.optimize();
		
		for (int i = 0; i < ndocs * 2; i++) {
			assertEquals(strings.get(i), stacker.searchText(i));
		}
		
		assertEquals(ndocs * 2, stacker.totalDocs);
		assertEquals(ndocs * 2, stacker.nextStackId);
	}

	@Test
	public void testReload() throws IOException {
		stacker = new TextStacker(STACK_PATH);
		int ndocs = 50;
		
		List<String> strings = new ArrayList<String>();
		for (int i=0; i<ndocs; i++) 
			strings.add(RandomStringUtils.randomAlphabetic(TEXT_SIZE));

		for (int i = 0; i < ndocs; i++) {
			String file = "file" + i;
			stacker.addText(file, strings.get(i));
		}

		stacker.optimize();
		stacker.close();

		stacker = TextStacker.loadStacker(STACK_PATH);
		
		for (int i = 0; i < ndocs; i++) {
			assertEquals(strings.get(i), stacker.searchText(i));
		}
		
		assertEquals(ndocs, stacker.totalDocs);
		assertEquals(ndocs, stacker.nextStackId);
	}
	
	final static String[] data = {
		"abcde fgh ij klmnop qrst uvwxyz \n",
		"s�o paulo corinthians atl�tico-mg am�rica-rj \t\n",
		"p� p� l� j� m� m� m� �o �e \n",
		"I can has cheezburger !? omgwtfbbq! \n",
		"As �rveres... somos nozes... \n",
		"Dolly, Dolly Guaran� Dolly... Dolly Guaran�, sabor diferente \n",
		"!@#$%*() -={}[]^~; :.,<> | \n"};
	
	@Test
	public void testNamespace() throws IOException {
		stacker = new TextStacker(STACK_PATH);
		int ndocs = 50;

		// ##############################
		// ### Criacao e testes basicos de seguranca
		for (int i = 0; i < ndocs; i++) {
			String text = data[i % data.length];
			stacker.addText("file" + i, text);
		}
		stacker.optimize();
		for (int i = 0; i < ndocs; i++) {
			String text = data[i % data.length];
			assertEquals(text, stacker.searchText("file" + i));
		}
		assertEquals(ndocs, stacker.totalDocs);
		assertEquals(ndocs, stacker.nextStackId);
		// ##############################

		for (int i = ndocs; i < ndocs * 2; i++) {
			String text = data[i % data.length];
			stacker.addText("file" + i, text);
		}
		stacker.optimize();
		for (int i = 0; i < ndocs * 2; i++) {
			String text = data[i % data.length];
			assertEquals(text, stacker.searchText("file" + i));
		}
		assertEquals(ndocs * 2, stacker.totalDocs);
		assertEquals(ndocs * 2, stacker.nextStackId);
	}
	
	// ESSE TESTE SO � VALIDO ENQUANTO NAO TIVER GC
	@Test
	public void testUpdates() throws IOException {
		stacker = new TextStacker(STACK_PATH);
		int ndocs = 50;

		// ##############################
		// ### Criacao e testes basicos de seguranca
		for (int i = 0; i < ndocs; i++) {
			String text = data[i % data.length];
			stacker.addText("file" + i, text);
		}

		stacker.optimize();

		for (int i = 0; i < ndocs; i++) {
			String text = data[i % data.length];
			assertEquals(text, stacker.searchText("file" + i));
		}

		assertEquals(ndocs, stacker.totalDocs);
		assertEquals(ndocs, stacker.nextStackId);
		// ##############################

		// Define o maior e o menor tamanho dos dados de teste
		// Maior+1: Util para garantir que haja apenas um update, sem replace
		// Menor : Util para garantir que haja um replace em qualquer posicao
		int greater_len = 0;
		for (String s : data) {
			if (s.length() > greater_len) {
				greater_len = s.length();
			}
		}

		// Garante que aconteca update+append
		String bizarra = RandomStringUtils.randomAscii(greater_len + 1);
		int[] dels = { 0, 10, 20, 30, 40 };

		for (int del : dels) {
			stacker.addText("file" + del, bizarra);
		}
		stacker.optimize();

		// A cadeia deve ser encontrada corretamente tanto pelo nome...
		for (int del : dels) {
			assertEquals(bizarra, stacker.searchText("file" + del));
		}

		// ...mas a busca por IDs deve continuar igual a como era antes...
		for (int i = 0; i < ndocs; i++) {
			String text = data[i % data.length];
			assertEquals(text, stacker.searchText(i));
		}

		// ...e com as posicoes novas sendo os adicionados...
		for (int i = ndocs; i < ndocs + dels.length; i++) {
			assertEquals(bizarra, stacker.searchText(i));
		}
	}
	
	/**
	 * Testa diversos casos de strings utf-8: com caracteres de 1, 2 e 3 bytes
	 */
	@Test
	public final void testStrBytesConversions() throws UTFDataFormatException {
		String[] data = {	"pàpêpípõpü",
							"\t \n \r", 
							"", 
							" ", 
							"ЊДОШПЦФ",
							"ดตญทธยษส", 
							"ヅテガシジツミポブ", 
							"สçヅยãテОガ;ธ§Д" };

		for (String str : data) {
			assertEquals(str, TextStacker.toStr(TextStacker.toBytes(str)));
		}
	}

	/**
	 * Testa em lote os casos que deveriam disparar UTFDataFormatException
	 * 
	 * O mais correto seria fazer um teste pra cada, mas dá muito trabalho e
	 * ficaria pouco organizado
	 */
	@Test
	public void testStrBytesConversionsException() {
		/*
		 * ### TABELA DE UTF-8 A) Char de 1 byte: 0xxx xxxx B) Char impossivel:
		 * 10xx xxxx (somente para continuidade) C) Char de 2 bytes: 110x xxxx
		 * 10xx xxxx D) Char de 3 bytes: 1110 xxxx 10xx xxxx 10xx xxxx E) Char
		 * impossivel: 1111 xxxx
		 * 
		 * Erros possíveis: 1) 1o byte do caracter ser da categoria B 2) 1o byte
		 * do caracter ser da categoria E 3) 2o byte da categoria C não ser 10xx
		 * xxxx 4) 2o byte da categoria D não ser 10xx xxxx 5) 3o byte da
		 * categoria D não ser 10xx xxxx 6) Sequencia de bytes acabar após o 1o
		 * byte do caso C 7) Sequencia de bytes acabar após o 2o byte do caso D
		 * 8) Sequencia de bytes acabar após o 2o byte do caso D
		 */

		byte[][] b = { { 65, 66, -128, 67, 68 }, // Erro 1: A, B, bizarro, C, E
				{ 65, 66, -16, 67, 68 }, // Erro 2: A, B, bizarro, C, E
				{ 65, 66, -64, 127, 68 }, // Erro 3: A, B, ok1, ERRO!, E
				{ 65, 66, -32, 127, 68 }, // Erro 4: A, B, ok1, ERRO!, E
				{ 65, 66, -32, -128, 127 },// Erro 5: A, B, ok1, ok2, ERRO!
				{ 65, 66, 67, 68, -64 }, // Erro 6: A, B, C, D, ok1
				{ 65, 66, 67, 68, -32 }, // Erro 7: A, B, C, D, ok1
				{ 65, 66, 67, -32, -128 }, // Erro 8: A, B, C, ok1, ok2
		};
		for (byte[] data : b) {
			try {
				TextStacker.toStr(data);
				fail("Sequencia deveria disparar uma UTFDataFormatException...");
			} catch (UTFDataFormatException e) {
				continue;
			}
		}
	}
}
