package org.filestacker.utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UTFDataFormatException;

/** Canal de leitura/escrita UTF-8 aleátoria mais rápida. */
public class FastRandomAccessFile extends RandomAccessFile {
	private byte[] bytearr = null;
	private char[] chararr = null;

	public FastRandomAccessFile(File file, String mode) throws IOException {
		super(file, mode);
	}

	public FastRandomAccessFile(String path, String mode) throws IOException {
		this(new File(path), mode);
	}

	/**
	 * Escreve uma string UTF-8 no canal.<br>
	 * 
	 * Método baseado em DataOutputStream.write0UTF(), mas utilizando UTF-8
	 * genérico (sem os dois bytes de tamanho do UTF-8 do Java), e cerca de 40%
	 * mais rápido que este método da JVM.
	 * 
	 * @param text
	 *            A string a ser escrita em disco
	 * @return <code>int</code> A quantidade de bytes escrita em disco
	 * @see RandomAccessFile#writeUTF(String)
	 */
	public int writeDoc(String text) throws IOException {
		int strlen, utflen;
		int i, c, count;

		count = 0;
		utflen = 0;
		strlen = text.length();

		/*
		 * TODO Talvez dê pra melhorar sem varrer a String (e o aparentemente
		 * lento String.charAt(i) 2x, fazendo somente uma vez e caso o buffer
		 * bytearr seja menor que o tamanho da String até agora, dobre o tamanho
		 * dele e jogue o vetor antigo para dentro do novo.
		 */

		/* use charAt instead of copying String to char array */
		for (i = 0; i < strlen; i++) {
			c = text.charAt(i);
			if ((c >= 0x0001) && (c <= 0x007F)) {
				utflen++;
			} else if (c > 0x07FF) {
				utflen += 3;
			} else {
				utflen += 2;
			}
		}

		if (bytearr == null || bytearr.length < utflen) {
			bytearr = new byte[utflen];
		}

		for (i = 0; i < strlen; i++) {
			c = text.charAt(i);
			if (!((c >= 0x0001) && (c <= 0x007F))) {
				break;
			}
			bytearr[count++] = (byte) c;
		}

		for (; i < strlen; i++) {
			c = text.charAt(i);
			if ((c >= 0x0001) && (c <= 0x007F)) {
				bytearr[count++] = (byte) c;
			} else if (c > 0x07FF) {
				bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
				bytearr[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
				bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
			} else {
				bytearr[count++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
				bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
			}
		}

		this.write(bytearr, 0, utflen);

		return utflen;
	}

	public String readDoc(final int utflen) throws IOException {
		int c, count;
		int char2, char3;
		int chararr_count = 0;

		count = 0;
		chararr_count = 0;

		long init_pos = this.getFilePointer();

		// Se o buffer não existe ou for muito pequeno,
		// crie do tamanho correto.
		if (chararr == null || chararr.length < utflen) {
			chararr = new char[utflen];
			bytearr = new byte[utflen];
		}

		// this.readFully(bytearr);
		this.readFully(bytearr, 0, utflen);
		/*
		 * Enquanto a máscara for de "0xxxxxxx", fique neste caso. A partir do
		 * ponto em que encontrar outro caso, use o próximo bloco para chars de
		 * 1, 2 ou 3 bytes.
		 */
		while (count < utflen) {
			// System.out.print(bytearr[count]+" ");
			// System.out.println("[next char]");
			c = bytearr[count] & 0xff;
			if (c > 127) {
				break;
			}
			count++;
			chararr[chararr_count++] = (char) c;
			// System.out.println("[readdoc new char1] = "+(char)c+" | "+Integer.toBinaryString(c));
		}

		while (count < utflen) {
			// System.out.println("[next char]");

			c = bytearr[count] & 0xff;
			// System.out.print(bytearr[count]+"");
			switch (c >> 4) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
				/* Char de 1 byte: 0xxxxxxx */
				count++;
				chararr[chararr_count++] = (char) c;
				// System.out.println("[readdoc new char11] = "+(char)c);
				break;
			case 12:
			case 13:
				/* Char de 2 bytes: 110x xxxx 10xx xxxx */
				count += 2;
				if (count > utflen) { throw new UTFDataFormatException(
						"2 byte-char: malformed input: partial character at end"); }
				char2 = bytearr[count - 1];
				if ((char2 & 0xC0) != 0x80) { throw new UTFDataFormatException(
						"2 byte-char: malformed input around byte"
								+ (init_pos + count) + " -> value " + char2); }
				chararr[chararr_count++] = (char) (((c & 0x1F) << 6) | (char2 & 0x3F));
				// System.out.println("[readdoc new char2] = "+(char)(((c &
				// 0x1F) << 6) | (char2 & 0x3F)));
				break;
			case 14:
				/* Char de 3 bytes: 1110 xxxx 10xx xxxx 10xx xxxx */
				count += 3;
				if (count > utflen) { throw new UTFDataFormatException(
						"3 byte-char: malformed input: partial character at end"); }
				char2 = bytearr[count - 2];
				char3 = bytearr[count - 1];
				if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) { throw new UTFDataFormatException(
						"3 byte-char: malformed input around byte "
								+ (init_pos + count - 2) + "(value " + char2
								+ ") or " + (init_pos + count - 1) + "(value "
								+ char3 + ")"); }
				chararr[chararr_count++] = (char) (((c & 0x0F) << 12)
						| ((char2 & 0x3F) << 6) | ((char3 & 0x3F) << 0));
				// System.out.println("[readdoc new char3] = "+(char)(((c &
				// 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F) <<
				// 0)));
				break;
			default:
				/* 10xx xxxx, 1111 xxxx */
				throw new UTFDataFormatException(
						"unknown char: malformed input around byte "
								+ (init_pos + count) + " -> value " + c);
			}
		}

		return new String(chararr, 0, chararr_count);

	}

	/**
	 * Lê uma string UTF-8 na posição demarcada.<br>
	 * 
	 * Método baseado em DataInputStream.readUTF(), mas utilizando UTF-8
	 * genérico (sem os dois bytes de tamanho do UTF-8 do Java), e cerca de 40%
	 * mais rápido que este método da JVM.
	 * 
	 * @param init_pos
	 *            Posição inicial de leitura
	 * @param end_pos
	 *            Posição final de leitura
	 * @return <code>String</code> String lida do arquivo
	 * @see RandomAccessFile#readUTF()
	 */
	public final String readDoc(int init_pos, int end_pos) throws IOException {
		return readDoc(end_pos - init_pos);
	}

	/**
	 * Lê do arquivo e retorna a um vetor de bytes do tamanho pedido, a partir
	 * da posição passada
	 * 
	 * @param offset
	 *            O offset para inicio da leitura
	 * @param bytes
	 *            A quantidade de bytes para ser lida
	 * @return Um array de bytes com o conteudo
	 * @throws IOException
	 *             Provavelmente devido a leitura em posições incorretas (acima
	 *             ou abaixo do tamanho da fonte)
	 */
	public byte[] readBytes(int offset, int bytes) throws IOException {
		this.seek(offset);
		return readBytes(bytes);
	}

	/**
	 * Lê do arquivo e retorna a um vetor de bytes do tamanho pedido, a partir
	 * da posição atual
	 * 
	 * @param bytes
	 *            A quantidade de bytes para ser lida
	 * @return Um array de bytes com o conteudo
	 * @throws IOException
	 *             Provavelmente devido a leitura em posições incorretas (acima
	 *             ou abaixo do tamanho da fonte)
	 */
	public final byte[] readBytes(int bytes) throws IOException {
		// O tamanho exato do indice
		byte[] buf = new byte[bytes];
		readFully(buf);

		return buf;
	}

	@Override
	public void seek(long pos) throws IOException {
		super.seek(pos);
	}
}
