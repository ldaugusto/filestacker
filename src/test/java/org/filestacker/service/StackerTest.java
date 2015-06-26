package org.filestacker.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.log4j.BasicConfigurator;
import org.filestacker.utils.StackUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class StackerTest {

	Stacker stacker;

	@Before
	public void setUp() throws Exception {
		BasicConfigurator.configure();
	}

	@After
	public void tearDown() throws Exception {
		stacker.close();
		//FileUtils.cleanDirectory(new File(stacker.stacksPath));
	}

	@Test
	public void testBasicAddAndSearch() throws IOException {
		stacker = new Stacker("/tmp/stacker/");
		int ndocs = 50;

		// ##############################
		// ### Criacao e testes basicos de seguranca
		for (int i = 0; i < ndocs; i++) {
			stacker.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		stacker.optimize();
		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals(stacker.searchFile(i), LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		assertEquals(ndocs, stacker.totalDocs);
		assertEquals(ndocs, stacker.nextStackId);
		// ##############################

		for (int i = ndocs; i < ndocs * 2; i++) {
			stacker.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		stacker.optimize();
		for (int i = 0; i < ndocs * 2; i++) {
			assertArrayEquals(stacker.searchFile(i), LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		assertEquals(ndocs * 2, stacker.totalDocs);
		assertEquals(ndocs * 2, stacker.nextStackId);
	}

	@Test
	public void testAddAndSearch() throws IOException {
		stacker = new Stacker("/tmp/stacker/");
		int ndocs = Stack.MAX_FILES * 7;

		byte[][] data = { { 'A', '\n' }, { 'B', '\n' }, { 'C', '\n' },
				{ 'D', '\n' }, { 'E', '\n' } };

		for (int i = 0; i < ndocs; i++) {
			stacker.addFile("file" + i, data[i % data.length]);
		}
		stacker.optimize();

		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals("Erro em " + i, stacker.searchFile(i), data[i
					% data.length]);
		}
		assertEquals(ndocs, stacker.totalDocs);
		assertEquals(ndocs, stacker.nextStackId);
	}

	@Test
	public void testReload() throws IOException {
		String path = "/tmp/stacker/";
		stacker = new Stacker(path);
		int ndocs = 50;

		for (int i = 0; i < ndocs; i++) {
			String file = "file" + i;
			byte[] data = LocalStackTest.data[i % LocalStackTest.data.length];
			stacker.addFile(file, data);
		}
		
		stacker.optimize();
		stacker.close();

		stacker = Stacker.loadStacker(path);
		for (int i = 0; i < ndocs; i++) {
			byte[] data = LocalStackTest.data[i % LocalStackTest.data.length];
			assertArrayEquals(data, stacker.searchFile(i));
		}
		assertEquals(ndocs, stacker.totalDocs);
		assertEquals(ndocs, stacker.nextStackId);
	}

	@Test
	public void testNamespace() throws IOException {
		stacker = new Stacker("/tmp/stacker/");
		int ndocs = 50;

		// ##############################
		// ### Criacao e testes basicos de seguranca
		for (int i = 0; i < ndocs; i++) {
			stacker.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		stacker.optimize();
		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals(stacker.searchFile("file" + i),
					LocalStackTest.data[i % LocalStackTest.data.length]);
		}
		assertEquals(ndocs, stacker.totalDocs);
		assertEquals(ndocs, stacker.nextStackId);
		// ##############################

		for (int i = ndocs; i < ndocs * 2; i++) {
			stacker.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		stacker.optimize();
		for (int i = 0; i < ndocs * 2; i++) {
			assertArrayEquals(stacker.searchFile("file" + i),
					LocalStackTest.data[i % LocalStackTest.data.length]);
		}
		assertEquals(ndocs * 2, stacker.totalDocs);
		assertEquals(ndocs * 2, stacker.nextStackId);
	}

	@Test
	public void testDeletes() throws IOException {
		stacker = new Stacker("/tmp/stacker/");
		int ndocs = 50;

		// ##############################
		// ### Criacao e testes basicos de seguranca
		for (int i = 0; i < ndocs; i++) {
			stacker.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		stacker.optimize();
		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals(stacker.searchFile("file" + i),
					LocalStackTest.data[i % LocalStackTest.data.length]);
		}
		assertEquals(ndocs, stacker.totalDocs);
		assertEquals(ndocs, stacker.nextStackId);
		// ##############################

		int[] dels = { 0, 1, stacker.nextStackId - 2, stacker.nextStackId - 1 };
		for (int del : dels) {
			assertTrue(stacker.deleteFile(del));
			assertArrayEquals(new byte[0], stacker.searchFile("file" + del));
		}
		assertEquals(dels.length, stacker.freeSlots.size());
	}

	// ESSE TESTE SO é VALIDO ENQUANTO NAO TIVER GC
	@Test
	public void testUpdates() throws IOException {
		stacker = new Stacker("/tmp/stacker/");
		int ndocs = 50;

		// ##############################
		// ### Criacao e testes basicos de seguranca
		for (int i = 0; i < ndocs; i++) {
			stacker.addFile("file" + i, LocalStackTest.data[i
					% LocalStackTest.data.length]);
		}
		stacker.optimize();
		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals(stacker.searchFile("file" + i),
					LocalStackTest.data[i % LocalStackTest.data.length]);
		}
		assertEquals(ndocs, stacker.totalDocs);
		assertEquals(ndocs, stacker.nextStackId);
		// ##############################

		// Define o maior e o menor tamanho dos dados de teste
		// Maior+1: Util para garantir que haja apenas um update, sem replace
		// Menor : Util para garantir que haja um replace em qualquer posicao
		int greater_len = 0;
		for (byte[] b : LocalStackTest.data) {
			if (b.length > greater_len) {
				greater_len = b.length;
			}
		}

		// Garante que aconteca update+append
		byte[] bizarra = StackUtils.toBytes(RandomStringUtils
				.randomAscii(greater_len + 1));
		int[] dels = { 0, 10, 20, 30, 40 };

		for (int del : dels) {
			stacker.addFile("file" + del, bizarra);
		}
		stacker.optimize();

		// A cadeia deve ser encontrada corretamente tanto pelo nome...
		for (int del : dels) {
			assertArrayEquals(bizarra, stacker.searchFile("file" + del));
		}

		// ...mas a busca por IDs deve continuar igual a como era antes...
		for (int i = 0; i < ndocs; i++) {
			assertArrayEquals(LocalStackTest.data[i
					% LocalStackTest.data.length], stacker.searchFile(i));
		}

		// ...e com as posicoes novas sendo os adicionados...
		for (int i = ndocs; i < ndocs + dels.length; i++) {
			assertArrayEquals(bizarra, stacker.searchFile(i));
		}
	}

	@Test
	public void testReplacesAndUpdates() throws IOException {
		stacker = new Stacker("/tmp/stacker/");
		int ndocs = 50;

		// ##############################
		// ### Criacao e testes basicos de seguranca
		for (int i = 0; i < ndocs; i++) {
			int pos = i % LocalStackTest.data.length;
			byte[] data = LocalStackTest.data[pos];
			String file = "file" + i;
			stacker.addFile(file, data);
		}
		stacker.optimize();
		for (int i = 0; i < ndocs; i++) {
			int pos = i % LocalStackTest.data.length;
			byte[] data = LocalStackTest.data[pos];
			String file = "file" + i;
			assertArrayEquals(data, stacker.searchFile(file));
		}
		
		assertEquals(ndocs, stacker.totalDocs);
		assertEquals(ndocs, stacker.nextStackId);
		
		// ##############################

		// Define o maior e o menor tamanho dos dados de teste
		// Maior+1: Util para garantir que haja apenas um update, sem replace
		// Menor : Util para garantir que haja um replace em qualquer posicao
		int lesser_len = Integer.MAX_VALUE;
		for (byte[] b : LocalStackTest.data) {
			if (b.length < lesser_len) {
				lesser_len = b.length;
			}
		}

		// Garante que str nova caiba em qualquer posicao para o replace
		byte[] bizarra = StackUtils.toBytes(RandomStringUtils.randomAscii(lesser_len));

		// Garante que vai tentar replace em 1 ocorrencia de cada strings e com
		// um offset:
		// Exemplo: LocalTabletTest.data.length = 4 -> dels = {0, 5, 10, 15}
		int[] dels = new int[LocalStackTest.data.length];
		dels[0] = 0;
		for (int k = 1; k < dels.length; k++) {
			dels[k] = dels[k - 1] + dels.length + 1;
		}

		for (int del : dels) {
			stacker.addFile("file" + del, bizarra);
		}
		stacker.optimize();

		// A cadeia deve ser encontrada corretamente tanto pelo nome...
		for (int del : dels) {
			// é necessario o String.trim() para ficar como esperado... então
			// byte[] -> String -> byte[]
			byte[] actual = StackUtils.toBytes(StackUtils.toStr(
					stacker.searchFile("file" + del)).trim());
			assertArrayEquals(bizarra, actual);
		}

		// ...quanto pelo ID
		EXTERNAL: for (int i = 0; i < ndocs; i++) {
			// se o ID for um dos trocados, espera-se a string bizarra
			for (int del : dels) {
				if (i == del) {
					// é necessario o String.trim() para ficar como esperado...
					// então byte[] -> String -> byte[]
					byte[] actual = StackUtils.toBytes(StackUtils
							.toStr(stacker.searchFile(del)).trim());
					assertArrayEquals("Erro para id " + del, bizarra, actual);
					continue EXTERNAL;
				}
			}
			// senão, segue o mesmo teste de antes
			assertArrayEquals(LocalStackTest.data[i
					% LocalStackTest.data.length], stacker.searchFile(i));
		}
	}
}