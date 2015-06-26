package org.filestacker.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UTFDataFormatException;

import junit.framework.JUnit4TestAdapter;

import org.junit.Test;

public class StackUtilsTest {

	byte[] arr1 = { 0x0F, 0x3A, 0x1B, 0x23 },
			arr2 = { 0x6F, 0x1C, 0x16, 0x0B }, 
			arr3 = { 0x0F, 0x3A, 0x1B, 0x23, 0x6F, 0x1C, 0x16, 0x0B, 
					0x0F, 0x23, 0x6F, 0x3A, 0x1B, 0x1C, 0x16, 0x0B };
	int[] ints = { 255466275, 1864111627, 253980474, 454825483 };

	@Test
	public final void testToHexadecimal() {
		String hex1 = "0f3a1b23", hex2 = "6f1c160b";

		assertEquals(hex2, StackUtils.toHexadecimal(arr2));
		assertEquals(hex1, StackUtils.toHexadecimal(arr1));
		assertFalse(hex2.equals(StackUtils.toHexadecimal(arr1)));
	}

	@Test
	public final void testMergeFiles() throws IOException {
		BufferedWriter writer;
		String msg1 = "Java - StackUtilsTest.java - EclipseSDK";
		String msg2 = "Problems | Javadoc | Declaration | Console";

		File tmp1 = new File("/tmp/write.1"), tmp2 = StackUtils
				.getTempFile(tmp1);
		tmp1.deleteOnExit();
		tmp2.deleteOnExit();

		writer = new BufferedWriter(new FileWriter(tmp1));
		writer.write(msg1);
		writer.close();

		writer = new BufferedWriter(new FileWriter(tmp2));
		writer.write(msg2);
		writer.close();

		StackUtils.mergeFiles(tmp1);

		BufferedReader reader = new BufferedReader(new FileReader(tmp1));

		String lido = reader.readLine();
		reader.close();

		assertEquals(msg1 + msg2, lido);
	}

	@Test
	public final void testByteArrayToIntArray() {
		int res[] = StackUtils.byteArrayToIntArray(arr3);

		for (int i = 0; i < res.length; i++) {
			// System.out.print(res[i]+" ");
			assertEquals(ints[i], res[i]);
		}
	}

	/**
	 * Testa diversos casos de strings utf-8: com caracteres de 1, 2 e 3 bytes
	 */
	@Test
	public final void testStrBytesConversions() throws UTFDataFormatException {
		String[] data = { "pàpêpípõpü", "\t \n \r", "", " ", "ЊДОШПЦФ",
				"ดตญทธยษส", "ヅテガシジツミポブ", "สçヅยãテОガ;ธ§Д" };

		for (String str : data) {
			assertEquals(str, StackUtils.toStr(StackUtils
					.toBytes(str)));
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
				StackUtils.toStr(data);
				fail("Sequencia deveria disparar uma UTFDataFormatException...");
			} catch (UTFDataFormatException e) {
				continue;
			}
		}
	}

	@Test
	public void testGenerateStackFile() {
		assertEquals("/tmp/stack00000000.stk", StackUtils.generateStackFile(0, "/tmp").getAbsolutePath());
		assertEquals("/tmp/hoho/stack00001000.stk", StackUtils.generateStackFile(1000, "/tmp/hoho").getAbsolutePath());
	}

	@Test
	public void testCalcMD5() throws IOException {
		String md5 = "d41d8cd98f00b204e9800998ecf8427e"; // Obtido via '>
		// /tmp/hahaha |
		// md5sum
		// /tmp/hahaha'
		File file = new File("/tmp/hahaha");
		RandomAccessFile rand = new RandomAccessFile(file, "rw");
		rand.close();
		assertEquals(md5, StackUtils.toHexadecimal(StackUtils
				.calcMD5(file, 0)));
		if (!file.delete()) {
			System.err.println("Não conseguiu deletar o arquivo " + file);
		}
	}
	
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(StackUtilsTest.class);
	}
}