package org.filestacker.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.filestacker.utils.StackUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LocalStackTest {

	LocalStack stack;
	final static byte[][] data = {
		TextStacker.toBytes("abcde fgh ij klmnop qrst uvwxyz \n"),
		TextStacker.toBytes("são paulo corinthians atlético-mg américa-rj \t\n"),
		TextStacker.toBytes("pé pá lá já má mé mó ão ãe \n"),
		TextStacker.toBytes("I can has cheezburger !? omgwtfbbq! \n"),
		TextStacker.toBytes("As árveres... somos nozes... \n"),
		TextStacker.toBytes("Dolly, Dolly Guaraná Dolly... Dolly Guaraná, sabor diferente \n"),
		TextStacker.toBytes("!@#$%*() -={}[]^~; :.,<> | \n") };
	public static final int APPEND_FILES = 100;

	@Before
	public void setUp() throws Exception {
		stack = new LocalStack(0, "/tmp");
	}

	@After
	public void tearDown() throws Exception {

	}

	// Testa insercãão até o limite de arquivos
	@Test
	public void testAppend1() throws IOException {
		byte[] data = "Dado teste padrão".getBytes();
		for (int i = 0; i < Stack.MAX_FILES; i++) {
			assertTrue(stack.append("file" + i, data));
		}
		for (int i = 0; i < 10; i++) {
			assertFalse(stack.append("file" + i, data));
		}
		assertEquals(Stack.MAX_FILES, stack.getNumFiles());

		assertEquals(stack.getStackFile().length(), stack.getIndex()[Stack.MAX_FILES]);
	}

	// Testa insercãão até o limite de tamanho
	@Test
	public void testAppend2() throws IOException {
		// Define o dado a ser colocado
		byte[] data = new byte[2 << 19]; // 2 << 19 = 2^20 = 1MB

		// Calcula o numero maximo de arquivos de tamanho data.length que dá pra
		// colocar
		int max = (Stack.MAX_SIZE - Stack.DATA_OFFSET) / data.length;

		for (int i = 0; i < max; i++) {
			assertTrue("Erro: " + i + " / Total: " + max, stack.append("file" + i, data));
		}
		for (int i = 0; i < 10; i++) {
			assertFalse("Erro em " + i, stack.append("file" + i, data));
		}

		assertEquals(max, stack.getNumFiles());
		assertEquals(stack.getStackFile().length(), stack.getIndex()[max]);
	}

	// Testa a busca inserindo textos
	@Test
	public void testSearch1() throws IOException {
		int first = 42;
		int docs = 1000;
		// Gera uma stack para testes
		stack = generateTestStack(first, docs);

		for (int i = 0; i < stack.getNumFiles(); i++) {
			byte[] actual = stack.get(i);
			byte[] expected = data[i % data.length];
			// Checa o tamanho
			assertEquals("Falhou no doc " + i, expected.length, actual.length);
			// Checa todos os bytes
			for (int k = 0; k < actual.length; k++) {
				assertEquals("Falhou no byte " + k, expected[k], actual[k]);
			}
		}

		assertEquals(stack.getStackFile().length(), stack.getIndex()[docs]);
	}

	// Testa a busca inserindo textos
	@Test
	public void testSearch2() throws IOException {
		int first = 13;
		int docs = 200;
		// Gera uma stack para testes
		stack = generateTestStack(first, docs);

		for (int i = 0; i < stack.getNumFiles(); i++) {
			byte[] actual = stack.get("file" + i);
			byte[] expected = data[i % data.length];
			// Checa o tamanho
			assertEquals("Falhou no doc " + i, expected.length, actual.length);
			// Checa todos os bytes
			for (int k = 0; k < actual.length; k++) {
				assertEquals("Falhou no byte " + k, expected[k], actual[k]);
			}
		}

		assertEquals(stack.getStackFile().length(), stack.getIndex()[docs]);
	}

	@Test
	public void testReload() throws IOException {
		int docs = Stack.MAX_FILES;
		int first = 14;
		stack = generateTestStack(first, docs);
		File stackFile = stack.getStackFile();
		int[] offsets1 = stack.getIndex();

		for (int i = 0; i < stack.getNumFiles(); i++) {
			byte[] actual = stack.get(i);
			// Checa o tamanho
			byte[] expected = data[i % data.length];
			int exp_size = expected.length;
			assertEquals("Falhou no doc " + i, exp_size, actual.length);
			// Checa todos os bytes
			for (int k = 0; k < actual.length; k++) {
				assertEquals("Falhou no byte " + k, expected[k], actual[k]);
			}
		}

		assertEquals(stack.getStackFile().length(), offsets1[docs]);

		stack.close();
		stack = LocalStack.loadStack(stackFile.getAbsolutePath());

		// Verifica quantidade de arquivos
		assertEquals(docs, stack.getNumFiles());
		assertEquals(first, stack.getFirstId());

		// Verifica indices
		int[] offsets2 = stack.getIndex();
		assertEquals(offsets1.length, offsets2.length);
		for (int i = 0; i < offsets1.length; i++) {
			assertEquals(offsets1[i], offsets2[i]);
		}

		for (int i = 0; i < stack.getNumFiles(); i++) {
			byte[] actual = stack.get(i);
			byte[] expected = data[i % data.length];
			// Checa o tamanho
			assertEquals("Falhou no doc " + i, expected.length, actual.length);
			// Checa todos os bytes
			for (int k = 0; k < actual.length; k++) {
				assertEquals("Falhou no byte " + k, expected[k], actual[k]);
			}
		}

		for (int i = 0; i < stack.getNumFiles(); i++) {
			byte[] actual = stack.get("file" + i);
			byte[] expected = data[i % data.length];
			// Checa o tamanho
			assertEquals("Falhou no doc " + i, expected.length, actual.length);
			// Checa todos os bytes
			for (int k = 0; k < actual.length; k++) {
				assertEquals("Falhou no byte " + k, expected[k], actual[k]);
			}
		}

		assertEquals(stack.getStackFile().length(), offsets2[docs]);
	}

	@Test
	public void testIncremental() throws IOException {
		int docs = 100;
		stack = generateTestStack(17, docs);

		assertEquals(docs, stack.getNumFiles());
		assertEquals(stack.getStackFile().length(), stack.getIndex()[docs]);
		assertTrue(stack.close());

		byte[] doc1 = TextStacker.toBytes("Yet another text in stacks\n");
		byte[] doc2 = TextStacker.toBytes("Yet yet yet another byte[] in the stack\n");
		stack.append("doc1", doc1);
		stack.append("doc2", doc2);
		stack.writeStack();

		assertEquals(docs + 2, stack.getNumFiles());

		byte[] back1 = stack.get(docs);
		byte[] back2 = stack.get(docs + 1);
		for (int i = 0; i < doc1.length || i < back1.length; i++) {
			assertEquals("Erro na posicãão " + i, doc1[i], back1[i]);
		}
		for (int i = 0; i < doc2.length || i < back2.length; i++) {
			assertEquals("Erro na posicãão " + i, doc2[i], back2[i]);
		}
		stack.close();

		assertEquals(stack.getStackFile().length(),
				stack.getIndex()[docs + 2]);
	}

	@Test
	public void testDeletes() throws IOException {
		int ndocs = 100;
		stack = generateTestStack(9, ndocs);

		// ### Testar deletados
		assertEquals(0, stack.getDeleteds().size());
		assertEquals(ndocs, stack.getNumFiles());
		
		// Se o arquivo nao existe na stack... entao não esta deletado! Â¬Â¬
		for (int i = -2; i < Stack.MAX_FILES + 10; i++) {
			assertFalse(stack.isDeleted(i));
		}

		// ### Deletar não deletáveis... não pode funcionar!
		assertFalse(stack.delete(-2)); // Aquém do range
		assertFalse(stack.delete(ndocs + 1)); // Slot vazio
		assertFalse(stack.delete(Stack.MAX_FILES + 1)); // Além do range

		// ### Deletar e checar quantidades de documentos
		int[] dfiles = { 0, 1, ndocs / 2, ndocs - 2, ndocs - 1 };
		for (int i = 0; i < dfiles.length; i++) {
			// Deleta
			assertTrue(stack.delete(dfiles[i])); 
			
			// Verifica se deletou +1 arquivo
			assertEquals(i + 1, stack.getDeleteds().size()); 
			
			// Verifica se tem -1 arquivo
			assertEquals(ndocs - (i + 1), stack.getNumFiles());
			
			// Verifica se o arquivo esta deletado
			assertTrue(stack.isDeleted(dfiles[i])); 
		}

		// ### Desdeletar e checar quantidades de documentos
		for (int i = 0; i < dfiles.length; i++) {
			// Deleta
			assertTrue(stack.undelete(dfiles[i])); 
			
			// Verifica se deletou -1 arquivo
			assertEquals(dfiles.length - (i + 1), stack.getDeleteds().size()); 
			
			// Verifica se tem +1 arquivo
			assertEquals(ndocs - dfiles.length + (i + 1), stack.getNumFiles()); 
			
			// Verifica se o arquivo nao esta deletado
			assertFalse(stack.isDeleted(dfiles[i])); 
		}
	}

	public static LocalStack generateTestStack(int first) {
		return generateTestStack(first, APPEND_FILES);
	}

	public static LocalStack generateTestStack(int first, int qntd) {
		try {
			LocalStack stack = new LocalStack(first, "/tmp");

			for (int i = 0; i < qntd; i++) {
				stack.append("file" + i, data[i % data.length]);
			}
			stack.writeStack();

			return stack;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
