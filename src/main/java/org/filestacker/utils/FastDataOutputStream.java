package org.filestacker.utils;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Canal de escrita em UTF-8 mais rápido. */
public class FastDataOutputStream extends DataOutputStream {

    public FastDataOutputStream(OutputStream out) {
        super(out);        
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
	 * @see DataOutputStream#writeUTF(String)
	 */
	static int writeUTF(String text, DataOutput out) throws IOException {
		int i, c, count = 0, strlen = text.length(), utflen = 0;
		
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

		// Tente primeiro fazer o (mais simples) código para caracteres ASCII,
		// a menos que encontre alguém não-ASCII, aí vá para o próximo.
		for (i = 0; i < strlen; i++) {
			c = text.charAt(i);
			if (!((c >= 0x0001) && (c <= 0x007F))) {
				break;
			}
			bytearr[count++] = (byte) c;
		}

		// Somente será usado caso o bloco anterior encontre alguém não-ASCII
		// então este é capaz de entender caracteres de 2 e 3 bytes.
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

		out.write(bytearr, 0, utflen);

		return utflen;
	}
}
