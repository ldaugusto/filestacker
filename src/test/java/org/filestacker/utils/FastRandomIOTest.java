package org.filestacker.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Test;

public class FastRandomIOTest {

	File tmp = new File("/tmp/fast.tmp");
	FastRandomAccessFile io;
	String txt1 = "abcde fgh ij klmnop qrst uvwxyz ";
	String txt2 = "s�o paulo corinthians atl�tico-mg am�rica-rj ";
	String txt3 = "p� p� l� j� m� m�o le �quele ";
	String txt4 = "!@#$%*() -={}[]^~; :.,<> ��\"?~'`";

	@After
	public final void finish() {
		tmp.deleteOnExit();
	}

	@Test
	public final void testReadWriteDoc() {
		try {
			io = new FastRandomAccessFile(tmp, "rw");
			int bytes1 = io.writeDoc(txt1);
			int bytes2 = io.writeDoc(txt2);
			int bytes3 = io.writeDoc(txt3);
			int bytes4 = io.writeDoc(txt4);
			io.close();

			io = new FastRandomAccessFile(tmp, "r");
			int pos = 0;
			String read1 = io.readDoc(pos, pos += bytes1);
			String read2 = io.readDoc(pos, pos += bytes2);
			String read3 = io.readDoc(pos, pos += bytes3);
			String read4 = io.readDoc(pos, pos += bytes4);
			io.close();

			assertEquals(txt1, read1);
			assertEquals(txt2, read2);
			assertEquals(txt3, read3);
			assertEquals(txt4, read4);

			assertFalse(txt1.equals(read2));
			assertFalse(txt2.equals(read3));
			assertFalse(txt3.equals(read4));
			assertFalse(txt4.equals(read1));

		} catch (IOException ioe) {
			fail("Falha de I/O");
		}

	}

}
