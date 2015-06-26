package org.filestacker.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;

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

		File tmp1 = new File("/tmp/write.1"); 
		File tmp2 = StackUtils.getTempFile(tmp1);
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
		assertEquals(md5, StackUtils.toHexadecimal(StackUtils.calcMD5(file, 0)));
		if (!file.delete()) {
			System.err.println("Não conseguiu deletar o arquivo " + file);
		}
	}
}