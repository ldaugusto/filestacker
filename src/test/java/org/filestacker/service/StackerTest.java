package org.filestacker.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.filestacker.utils.StackUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StackerTest {

	Stacker tableter;

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
		tableter.close();
		FileUtils.cleanDirectory(new File(tableter.stacksPath));
	}

	@Test
	public void testBasicAddAndSearch() throws IOException {
		tableter = new Stacker("/tmp/tableter/");
		int ndocs = 50;

		// ##############################
		// ### Criação e testes básicos de segurança
		for (int i = 0; i < ndocs; i++) {
			tableter.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		tableter.optimize();
		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals(tableter.searchFile(i), LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		assertEquals(ndocs, tableter.totalDocs);
		assertEquals(ndocs, tableter.nextStackId);
		// ##############################

		for (int i = ndocs; i < ndocs * 2; i++) {
			tableter.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		tableter.optimize();
		for (int i = 0; i < ndocs * 2; i++) {
			assertArrayEquals(tableter.searchFile(i), LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		assertEquals(ndocs * 2, tableter.totalDocs);
		assertEquals(ndocs * 2, tableter.nextStackId);
	}

	@Test
	public void testAddAndSearch() throws IOException {
		tableter = new Stacker("/tmp/tableter/");
		int ndocs = Stack.MAX_FILES * 7;

		byte[][] data = { { 'A', '\n' }, { 'B', '\n' }, { 'C', '\n' },
				{ 'D', '\n' }, { 'E', '\n' } };

		for (int i = 0; i < ndocs; i++) {
			tableter.addFile("file" + i, data[i % data.length]);
		}
		tableter.optimize();

		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals("Erro em " + i, tableter.searchFile(i), data[i
					% data.length]);
		}
		assertEquals(ndocs, tableter.totalDocs);
		assertEquals(ndocs, tableter.nextStackId);
	}

	@Test
	public void testReload() throws IOException {
		String path = "/tmp/tableter/";
		tableter = new Stacker(path);
		int ndocs = 50;

		for (int i = 0; i < ndocs; i++) {
			tableter.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		tableter.optimize();
		tableter.close();

		tableter = Stacker.loadStacker(path);
		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals(tableter.searchFile(i), LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		assertEquals(ndocs, tableter.totalDocs);
		assertEquals(ndocs, tableter.nextStackId);
	}

	@Test
	public void testNamespace() throws IOException {
		tableter = new Stacker("/tmp/tableter/");
		int ndocs = 50;

		// ##############################
		// ### Criação e testes básicos de segurança
		for (int i = 0; i < ndocs; i++) {
			tableter.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		tableter.optimize();
		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals(tableter.searchFile("file" + i),
					LocalStackTest.data[i % LocalStackTest.data.length]);
		}
		assertEquals(ndocs, tableter.totalDocs);
		assertEquals(ndocs, tableter.nextStackId);
		// ##############################

		for (int i = ndocs; i < ndocs * 2; i++) {
			tableter.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		tableter.optimize();
		for (int i = 0; i < ndocs * 2; i++) {
			assertArrayEquals(tableter.searchFile("file" + i),
					LocalStackTest.data[i % LocalStackTest.data.length]);
		}
		assertEquals(ndocs * 2, tableter.totalDocs);
		assertEquals(ndocs * 2, tableter.nextStackId);
	}

	@Test
	public void testDeletes() throws IOException {
		tableter = new Stacker("/tmp/tableter/");
		int ndocs = 50;

		// ##############################
		// ### Criação e testes básicos de segurança
		for (int i = 0; i < ndocs; i++) {
			tableter.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		tableter.optimize();
		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals(tableter.searchFile("file" + i),
					LocalStackTest.data[i % LocalStackTest.data.length]);
		}
		assertEquals(ndocs, tableter.totalDocs);
		assertEquals(ndocs, tableter.nextStackId);
		// ##############################

		int[] dels = { 0, 1, tableter.nextStackId - 2, tableter.nextStackId - 1 };
		for (int del : dels) {
			assertTrue(tableter.deleteFile(del));
			assertArrayEquals(new byte[0], tableter.searchFile("file" + del));
		}
		assertEquals(dels.length, tableter.freeSlots.size());
	}

	// ESSE TESTE SO É VALIDO ENQUANTO NAO TIVER GC
	@Test
	public void testUpdates() throws IOException {
		tableter = new Stacker("/tmp/tableter/");
		int ndocs = 50;

		// ##############################
		// ### Criação e testes básicos de segurança
		for (int i = 0; i < ndocs; i++) {
			tableter.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		tableter.optimize();
		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals(tableter.searchFile("file" + i),
					LocalStackTest.data[i % LocalStackTest.data.length]);
		}
		assertEquals(ndocs, tableter.totalDocs);
		assertEquals(ndocs, tableter.nextStackId);
		// ##############################

		// Define o maior e o menor tamanho dos dados de teste
		// Maior+1: Util para garantir que haja apenas um update, sem replace
		// Menor : Util para garantir que haja um replace em qualquer posição
		int greater_len = 0;
		for (byte[] b : LocalStackTest.data) {
			if (b.length > greater_len) {
				greater_len = b.length;
			}
		}

		// Garante que aconteça update+append
		byte[] bizarra = StackUtils.strToBytes(RandomStringUtils
				.randomAscii(greater_len + 1));
		int[] dels = { 0, 10, 20, 30, 40 };

		for (int del : dels) {
			tableter.addFile("file" + del, bizarra);
		}
		tableter.optimize();

		// A cadeia deve ser encontrada corretamente tanto pelo nome...
		for (int del : dels) {
			assertArrayEquals(bizarra, tableter.searchFile("file" + del));
		}

		// ...mas a busca por IDs deve continuar igual a como era antes...
		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals(LocalStackTest.data[i
					% LocalStackTest.data.length], tableter.searchFile(i));
		}

		// ...e com as posicoes novas sendo os adicionados...
		for (int i = ndocs; i < ndocs + dels.length; i++) {
			assertArrayEquals(bizarra, tableter.searchFile(i));
		}
	}

	@Test
	public void testReplacesAndUpdates() throws IOException {
		tableter = new Stacker("/tmp/tableter/");
		int ndocs = 50;

		// ##############################
		// ### Criação e testes básicos de segurança
		for (int i = 0; i < ndocs; i++) {
			tableter.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		tableter.optimize();
		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals(tableter.searchFile("file" + i),
					LocalStackTest.data[i % LocalStackTest.data.length]);
		}
		assertEquals(ndocs, tableter.totalDocs);
		assertEquals(ndocs, tableter.nextStackId);
		// ##############################

		// Define o maior e o menor tamanho dos dados de teste
		// Maior+1: Util para garantir que haja apenas um update, sem replace
		// Menor : Util para garantir que haja um replace em qualquer posição
		int lesser_len = Integer.MAX_VALUE;
		for (byte[] b : LocalStackTest.data) {
			if (b.length < lesser_len) {
				lesser_len = b.length;
			}
		}

		// Garante que str nova caiba em qualquer posição para o replace
		byte[] bizarra = StackUtils.strToBytes(RandomStringUtils
				.randomAscii(lesser_len));

		// Garante que vai tentar replace em 1 ocorrencia de cada strings e com
		// um offset:
		// Exemplo: LocalTabletTest.data.length = 4 -> dels = {0, 5, 10, 15}
		int[] dels = new int[LocalStackTest.data.length];
		dels[0] = 0;
		for (int k = 1; k < dels.length; k++) {
			dels[k] = dels[k - 1] + dels.length + 1;
		}

		for (int del : dels) {
			tableter.addFile("file" + del, bizarra);
		}
		tableter.optimize();

		// A cadeia deve ser encontrada corretamente tanto pelo nome...
		for (int del : dels) {
			// É necessário o String.trim() para ficar como esperado... então
			// byte[] -> String -> byte[]
			byte[] actual = StackUtils.strToBytes(StackUtils.bytesToStr(
					tableter.searchFile("file" + del)).trim());
			assertArrayEquals(bizarra, actual);
		}

		// ...quanto pelo ID
		EXTERNAL: for (int i = 0; i < ndocs; i++) {
			// se o ID for um dos trocados, espera-se a string bizarra
			for (int del : dels) {
				if (i == del) {
					// É necessário o String.trim() para ficar como esperado...
					// então byte[] -> String -> byte[]
					byte[] actual = StackUtils.strToBytes(StackUtils
							.bytesToStr(tableter.searchFile(del)).trim());
					assertArrayEquals("Erro para id " + del, bizarra, actual);
					continue EXTERNAL;
				}
			}
			// senão, segue o mesmo teste de antes
			assertArrayEquals(LocalStackTest.data[i
					% LocalStackTest.data.length], tableter.searchFile(i));
		}
	}
}