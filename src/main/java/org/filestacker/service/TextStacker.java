package org.filestacker.service;

import java.io.IOException;
import java.io.UTFDataFormatException;

import org.apache.log4j.Logger;

/**
 * A text-specialized FileStacker. 
 * 
 * 1) Always compress data
 * 2) Has the utility methods addText(String), searchText(String) and searchText(int)
 * 
 * @author daniel
 */
public class TextStacker extends Stacker {

	private static final Logger logger = Logger.getLogger(TextStacker.class);
	private static final boolean DEFAULT_SINGLEMODE = true;
	private static final boolean ALWAYS_COMPRESS = true;
	
	
	public TextStacker(String path) {
		this(path, DEFAULT_SINGLEMODE);
	}

	public TextStacker(String path, boolean threadSafe) {
		super(path, threadSafe, ALWAYS_COMPRESS);
	}
	
	private TextStacker(final String path, final LocalStack[] stacks, boolean threadSafe) throws IOException {
		super(path, stacks, threadSafe, ALWAYS_COMPRESS);
	}

	public static TextStacker loadStacker(final String path)  throws IOException {
		return TextStacker.loadStacker(path, DEFAULT_SINGLEMODE);
	}
	
	public static TextStacker loadStacker(final String path, boolean threadSafe)  throws IOException {
		return new TextStacker(path, stacks(path), threadSafe);
	}
	
	public String searchText(int stackid) throws IOException {
		return toStr(searchFile(stackid));
	}
	
	public String searchText(String name) throws IOException {
		return toStr(searchFile(name));
	}

	public int addText(String name, String text) {
		return addFile(name, toBytes(text));
	}
	
	/**
	 * Converte uma String para uma cadeia de bytes UTF-8, de forma otimizada.
	 * 
	 * Utilizado antes de métodos do tipo 'write' da Stack.
	 * 
	 * Método original de FastRandomIO e FastOutput, nas stacks 0.x
	 * 
	 * @param text
	 *            A string a ser convertida
	 * @return Um vetor de bytes representando a String em UTF8
	 */
	public static byte[] toBytes(final String text) {
		int strlen = text.length(), utflen = 0;
		int i = 0, c = 0, count = 0;

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

		byte[] bytearr = new byte[utflen];

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

		return bytearr;
	}

	/**
	 * Converte uma cadeia de bytes UTF-8 em uma String, de forma otimizada.
	 * 
	 * Utilizado após métodos do tipo 'read' da Stack.
	 * 
	 * Esse método não foi testado para ler outros tipos de entradas, exceto
	 * aquelas originadas por strToBytes. Caso contrário, é possÃ­vel que, em
	 * alguns casos, este método falhe.
	 * 
	 * Método original de FastRandomIO e FastInput, nas stacks 0.x
	 * 
	 * @param bytearr
	 *            A cadeia de bytes a ser convertida.
	 * @return A String UTF-8 daquela cadeia passada.
	 * @throws UTFDataFormatException
	 *             Em caso de uma cadeia de bytes UTF-8 incorreta
	 */
	public static String toStr(final byte[] bytearr)
			throws UTFDataFormatException {
		int c, count = 0;
		int chararr_count = 0;

		int utflen = bytearr.length;

		// Vetor de caracteres de resultado
		char[] chararr = new char[utflen];

		/*
		 * Enquanto a máscara for de "0xxxxxxx", fique neste caso. A partir do
		 * ponto em que encontrar outro caso, use o próximo bloco para chars de
		 * 1, 2 ou 3 bytes.
		 */
		while (count < utflen) {
			c = bytearr[count] & 0xff;
			if (c > 127) {
				break;
			}
			count++;
			chararr[chararr_count++] = (char) c;
		}

		// Bytes 2 e 3, em caso de caracter UTF não-ASCII
		int char2, char3;

		while (count < utflen) {

			c = bytearr[count] & 0xff;

			switch (c >> 4) {
			/* Char de 1 byte: 0xxxxxxx */
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
				count++;
				chararr[chararr_count++] = (char) c;
				break;

			/* Char de 2 bytes: 110x xxxx 10xx xxxx */
			case 12:
			case 13:
				count += 2;
				if (count > utflen) { 
					throw new UTFDataFormatException("2 byte-char: malformed input: partial character at end"); 
				}
				
				char2 = bytearr[count - 1];
				if ((char2 & 0xC0) != 0x80) { 
					throw new UTFDataFormatException("2 byte-char: malformed input around byte" + count + " -> value " + char2); 
				}
				
				chararr[chararr_count++] = (char) (((c & 0x1F) << 6) | (char2 & 0x3F));
				break;

			/* Char de 3 bytes: 1110 xxxx 10xx xxxx 10xx xxxx */
			case 14:
				count += 3;
				if (count > utflen) { throw new UTFDataFormatException(
						"3 byte-char: malformed input: partial character at end"); }
				char2 = bytearr[count - 2];
				char3 = bytearr[count - 1];
				if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) { 
					throw new UTFDataFormatException("3 byte-char: malformed input around byte "
								+ (count - 2) + "(value " + char2 + ") or "
								+ (count - 1) + "(value " + char3 + ")"); }
				chararr[chararr_count++] = (char) (((c & 0x0F) << 12)
						| ((char2 & 0x3F) << 6) | ((char3 & 0x3F) << 0));
				break;
			default:
				/* 10xx xxxx, 1111 xxxx */
				throw new UTFDataFormatException(
						"unknown char: malformed input around byte " + count
								+ " -> value " + c);
			}
		}

		return new String(chararr, 0, chararr_count);
	}
}
