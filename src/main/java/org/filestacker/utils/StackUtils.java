package org.filestacker.utils;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.DirectoryWalker;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.log4j.Logger;

/** Diversos m�todos utilit�rios de I/O comuns a outras classes das Stacks. */

public final class StackUtils {
	// N�o pode ser instanciada.
	private StackUtils() {
	};

	private static final Logger logger = Logger.getLogger(StackUtils.class);

	/**
	 * Converte uma String para uma cadeia de bytes UTF-8, de forma otimizada.
	 * 
	 * Utilizado antes de m�todos do tipo 'write' da Stack.
	 * 
	 * M�todo original de FastRandomIO e FastOutput, nas stacks 0.x
	 * 
	 * @param text
	 *            A string a ser convertida
	 * @return Um vetor de bytes representando a String em UTF8
	 */
	public static byte[] toBytes(final String text) {
		int strlen = text.length(), utflen = 0;
		int i = 0, c = 0, count = 0;

		/*
		 * TODO Talvez d� pra melhorar sem varrer a String (e o aparentemente
		 * lento String.charAt(i) 2x, fazendo somente uma vez e caso o buffer
		 * bytearr seja menor que o tamanho da String at� agora, dobre o tamanho
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
	 * Utilizado ap�s m�todos do tipo 'read' da Stack.
	 * 
	 * Esse m�todo n�o foi testado para ler outros tipos de entradas, exceto
	 * aquelas originadas por strToBytes. Caso contr�rio, � possível que, em
	 * alguns casos, este m�todo falhe.
	 * 
	 * M�todo original de FastRandomIO e FastInput, nas stacks 0.x
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
		 * Enquanto a m�scara for de "0xxxxxxx", fique neste caso. A partir do
		 * ponto em que encontrar outro caso, use o pr�ximo bloco para chars de
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

		// Bytes 2 e 3, em caso de caracter UTF n�o-ASCII
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
				if (count > utflen) { throw new UTFDataFormatException(
						"2 byte-char: malformed input: partial character at end"); }
				char2 = bytearr[count - 1];
				if ((char2 & 0xC0) != 0x80) { throw new UTFDataFormatException(
						"2 byte-char: malformed input around byte" + count
								+ " -> value " + char2); }
				chararr[chararr_count++] = (char) (((c & 0x1F) << 6) | (char2 & 0x3F));
				break;

			/* Char de 3 bytes: 1110 xxxx 10xx xxxx 10xx xxxx */
			case 14:
				count += 3;
				if (count > utflen) { throw new UTFDataFormatException(
						"3 byte-char: malformed input: partial character at end"); }
				char2 = bytearr[count - 2];
				char3 = bytearr[count - 1];
				if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) { throw new UTFDataFormatException(
						"3 byte-char: malformed input around byte "
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

	/**
	 * Imprime uma cadeia de bytes como hexadecimal.<br>
	 * 
	 * Converte cada byte do array de entrada em dois dígitos hexadecimais.
	 * Utilidade da criac�o � a utilizac�o para visualizar melhor o MD5. Mas
	 * talvez possa servir pra outra coisa...
	 * 
	 * @param in
	 *            A cadeia de bytes a ser convertida
	 * @return <code>String</code> Valor em hexadecimal da cadeia de entrada
	 */
	public static String toHexadecimal(final byte[] in) {
		return toHexadecimal(in, 0, in.length);
	}

	/**
	 * Imprime uma cadeia de bytes como hexadecimal.<br>
	 * 
	 * Converte cada byte do array de entrada em dois dígitos hexadecimais.
	 * Utilidade da criac�o � a utilizac�o para visualizar melhor o MD5. Mas
	 * talvez possa servir pra outra coisa...
	 * 
	 * @param in
	 *            A cadeia de bytes a ser convertida
	 * @param ini
	 *            A posic�o inicial
	 * @param len
	 *            A quantidade de bytes a ser lida
	 * @return <code>String</code> Valor em hexadecimal da cadeia de entrada
	 */
	public static String toHexadecimal(final byte[] in, final int ini,
			final int len) {
		final StringBuffer sb = new StringBuffer();
		char c;
		final int limit = (ini + len < in.length ? ini + len : in.length);

		for (int i = ini; i < limit; i++) {
			// First nibble
			c = (char) ((in[i] >> 4) & 0xf);
			if (c > 9) {
				c = (char) ((c - 10) + 'a');
			} else {
				c = (char) (c + '0');
			}
			sb.append(c);

			// Second nibble
			c = (char) (in[i] & 0xf);
			if (c > 9) {
				c = (char) ((c - 10) + 'a');
			} else {
				c = (char) (c + '0');
			}
			sb.append(c);
		}

		return sb.toString();
	}

	/**
	 * Calcula o MD5 de um arquivo.<br>
	 * 
	 * Calcula o md5sum de um arquivo, geralmente para checar a integridade de
	 * uma Stack (com o offset de HEADER_SIZE) para que n�o se carregue uma
	 * Stack ruim.<br>
	 * 
	 * Um MD5 s�o valores de 128-bits.
	 * 
	 * @param file
	 *            O arquivo onde se quer calcular o MD5
	 * @param offset
	 *            A partir de que ponto calcular o MD5
	 * @return <code>byte[]</code> O MD5 da Stack
	 * @see MessageDigest
	 */
	public static byte[] calcMD5(final File file, final long offset) {
		try {
			DataInputStream data = new DataInputStream(
					new FileInputStream(file));
			int skiped = data.skipBytes((int) offset);
			logger.warn("Pulou apenas " + skiped + " bytes, e n�o " + offset);

			byte[] bufferzao = new byte[(int) (file.length() - offset)];
			data.readFully(bufferzao);
			data.close();

			return DigestUtils.md5(bufferzao);
			
			
		} catch (IOException e) {
			logger.error("Error: " + e.getMessage());
			return null;
		}
		
		
	}

	/**
	 * Calcula o MD5 de um arquivo.<br>
	 * 
	 * Calcula o md5sum de um arquivo, geralmente para checar a integridade de
	 * uma Stack (com o offset de HEADER_SIZE) para que n�o se carregue uma
	 * Stack ruim.<br>
	 * 
	 * Um MD5 s�o valores de 128-bits.
	 * 
	 * @param file
	 *            O arquivo onde se quer calcular o MD5
	 * @param offset
	 *            A partir de que ponto calcular o MD5
	 * @return <code>byte[]</code> O MD5 da Stack
	 * @see MessageDigest
	 */
	public static byte[] strToMD5(final String str) {
		return DigestUtils.md5(str);
	}

	/**
	 * Devolve o MD5 da String passada, em forma de hexadecimal
	 * 
	 * @param str
	 *            A string.
	 * @return Uma string com o valor hexadecimal da string passada
	 */
	public static String strToHexaMD5(final String str) {
		return toHexadecimal(strToMD5(str));
	}

	/**
	 * Faz o merge dos dados ao arquivo da Stack.
	 * 
	 * Id�ntico a mergeFiles(File, File), exceto que o primeiro arquivo (o de
	 * origem) � convencionado em func�o do segundo.
	 * 
	 * Convenc�o sobre customizac�o FTW!!1!
	 * 
	 * @param file_out
	 * @throws IOException
	 */
	public static void mergeFiles(final File file_out) throws IOException {
		mergeFiles(getTempFile(file_out), file_out);
	}

	/**
	 * Converte um array de byte em array de int.<br>
	 * 
	 * � uma func�o bem simples, onde a �nica peculiaridade � o parâmetro
	 * <code>long</code>, que � o valor adicionado ao final do array de int
	 * retornado. Útil para a convers�o de dados lidos de um arquivo em índice
	 * de uma stack.
	 * 
	 * @param bytearr
	 *            O array a ser convertido
	 * @return <code>int[]</code> O array j� convertido
	 */
	public static int[] byteArrayToIntArray(final byte[] bytearr) {
		int[] intarr = new int[(bytearr.length / 4)];

		// Converte o array de bytes num array de inteiros (os ponteiros),
		// aglomerando blocos de 4 bytes para cada inteiro
		for (int i = 0; i < intarr.length; i++) {
			intarr[i] = ((bytearr[(4 * i) + 0] & 0xFF) << 24)
					| ((bytearr[(4 * i) + 1] & 0xFF) << 16)
					| ((bytearr[(4 * i) + 2] & 0xFF) << 8)
					| (bytearr[(4 * i) + 3] & 0xFF);
		}

		return intarr;
	}

	/**
	 * Gera o arquivo da stack de forma padronizada.
	 * 
	 * Se necess�rio, cria o path at� esse arquivo tamb�m.
	 * 
	 * @param first
	 *            O id do primeiro documento
	 * @param path
	 *            O path onde estar�o as stacks
	 * @return A referencia para onde ser� criado o arquivo de stack.
	 */
	public static File generateStackFile(final int first, final String path) {
		int len = String.valueOf(first).length();

		String preffix = "stack", suffix = ".stk";

		// Para dezenas de milhões de documentos - 10^7
		for (int i = 0; i < 8 - len; i++) {
			preffix = preffix.concat("0");
		}

		File pathFile = new File(path);
		if (!pathFile.exists()) {
			boolean created = pathFile.mkdirs();
			if (!created) 
				logger.debug("N�o foi possível criar estrutura de diret�rios para "
							+ pathFile.getAbsolutePath());
		}

		String filename = path + File.separatorChar + preffix + first + suffix;

		return new File(filename);
	}

	public static File getTempFile(final File stackFile) {
		return new File("/tmp/" + stackFile.getName() + ".tmp");
	}

	public static DataOutputStream getDataStream(final File outputfile, final boolean experimental)
			throws IOException {
		OutputStream stream = new BufferedOutputStream(new FileOutputStream(outputfile, false));

		if (experimental)
			return new FastDataOutputStream(stream);
					
		return new DataOutputStream(stream);
	}

	/**
	 * Calcula o MD5 de um arquivo.<br>
	 * 
	 * Calcula o md5sum de um arquivo, geralmente para checar a integridade de
	 * uma Tablet (com o offset de HEADER_SIZE) para que n�o se carregue uma
	 * Tablet ruim.<br>
	 * 
	 * Um MD5 s�o valores de 128-bits.
	 * 
	 * @param file
	 *            O arquivo onde se quer calcular o MD5
	 * @param offset
	 *            A partir de que ponto calcular o MD5
	 * @return <code>byte[]</code> O MD5 da Tablet
	 * @see MessageDigest
	 */
	public static byte[] calcStringMD5(String str) {
		return DigestUtils.md5(str);
	}

	public static String stringToStringMd5(String str) {
		return toHexadecimal(calcStringMD5(str));
	}

	/**
	 * Adiciona o primeiro arquivo ao final do segundo.<br>
	 * 
	 * Faz um merge de altíssima velocidade dos dois arquivos apontados. O
	 * segundo arquivo � aberto em 'modo append' e tem todo o conte�do do
	 * primeiro arquivo adicionado.<br>
	 * 
	 * A grande velocidade se deve a utilizac�o dos m�todos de Java NIO, que
	 * fazem essa operac�o em baixo nível e n�o bloqueante.<br>
	 * 
	 * Por padr�o, esse m�todo ser� utilizado na construc�o das stacks, onde a
	 * colec�o � escrita num arquivo tempor�rio que ao final � 'colado' ao final
	 * do arquivo da stack, nos m�todos populate() e writeTablet().
	 * 
	 * @param file_in
	 *            O arquivo de onde os dados vem
	 * @param file_out
	 *            O arquivo onde os dados estar�o unidos
	 */
	public static void mergeFiles(File file_in, File file_out)
			throws IOException {
		/*
		 * Cria a stream para ler o arquivo original Cria a stream para gravar o
		 * arquivo de c�pia Usa as streams para criar os canais correspondentes
		 * Stream de escrita est� no modo "append" (true) para n�o sobreescrever
		 * o cabecalho e o índice
		 */
		FileInputStream fis = new FileInputStream(file_in);
		FileOutputStream fos = new FileOutputStream(file_out, true);
		FileChannel r_channel = fis.getChannel();
		FileChannel w_channel = fos.getChannel();

		// Faz a transfer�ncia
		long lenght = r_channel.size();
		r_channel.transferTo(0, lenght, w_channel);

		// Fecha os canais
		w_channel.close();
		r_channel.close();
		fis.close();
		fos.close();
	}

	/**
	 * Converte um array de byte em array de int.<br>
	 * 
	 * � uma func�o bem simples, onde a �nica peculiaridade � o parâmetro
	 * <code>long</code>, que � o valor adicionado ao final do array de int
	 * retornado. Útil para a convers�o de dados lidos de um arquivo em índice
	 * de uma stack.
	 * 
	 * @param bytearr
	 *            O array a ser convertido
	 * @param size
	 *            O valor a ser colocado na �ltima posic�o
	 * @return <code>int[]</code> O array j� convertido
	 */
	public static int[] byteArrayToIntArray(byte[] bytearr, long size) {
		// +1 para colocar no final a posic�o final do arquivo de stack, �til
		// para saber onde termina o ultimo doc

		int[] intarr = new int[(bytearr.length / 4) + 1];

		for (int i = 0; i < intarr.length - 1; i++) {
			intarr[i] = ((bytearr[(4 * i) + 0] & 0xFF) << 24)
					| ((bytearr[(4 * i) + 1] & 0xFF) << 16)
					| ((bytearr[(4 * i) + 2] & 0xFF) << 8)
					| (bytearr[(4 * i) + 3] & 0xFF);
		}

		// Anexa a posic�o final do documento
		intarr[intarr.length - 1] = (int) size;

		return intarr;
	}
	/**
	 * Move o conte�do de uma pasta para outra, sem deletar a pasta de origem
	 * (mas seu conte�do sim)
	 * 
	 * Se estiverem em diferentes particões, o 'move' falha, e ent�o se torna um
	 * copy + delete
	 * 
	 * Cuidado ao utilizar esse m�todo em pastas com MUITOS arquivos
	 * (dezenas-centenas de milhares), n�o � otimizado para esse caso.
	 * 
	 * @param fromPath
	 *            Path de onde o conte�do ser� removido
	 * @param toPath
	 *            Path para onde o conte�do ser� movido
	 * @return o tempo gasto na operac�o
	 * @throws IOException
	 */
	public static long moveDirectoryContents(String fromPath, String toPath)
			throws IOException {
		return moveDirectoryContents(new File(fromPath), new File(toPath));
	}

	/**
	 * Move o conte�do de uma pasta para outra, sem deletar a pasta de origem
	 * (mas seu conte�do sim)
	 * 
	 * Se estiverem em diferentes particões, o 'move' falha, e ent�o se torna um
	 * copy + delete
	 * 
	 * Cuidado ao utilizar esse m�todo em pastas com MUITOS arquivos
	 * (dezenas-centenas de milhares), n�o � otimizado para esse caso.
	 * 
	 * @param fromPath
	 *            Path de onde o conte�do ser� removido
	 * @param toPath
	 *            Path para onde o conte�do ser� movido
	 * @return o tempo gasto na operac�o
	 * @throws IOException
	 */
	public static long moveDirectoryContents(File fromPath, File toPath)
			throws IOException {
		/*
		 * TODO � possível melhorar este m�todo. Se o File.renameTo � possível,
		 * ent�o o ideal � mover logo os sub-diret�rios 'raiz', ao inv�s de
		 * realizar m�ltiplos mkdirs() + moves individuais. Entretanto essa
		 * otimizac�o � inferior a obtida com este m�todo atual, ent�o s�
		 * precisa ser feita em caso de extrema necessidade de mais desempenho.
		 * 
		 * Foi feito dessa forma pela simplicidade de se utilizar o
		 * DummyDirectoryWalker
		 */

		final long start_time = System.currentTimeMillis();
		boolean shallTryCopy = false;

		// Get EVERY file (but not directories) from given path (recursive)
		List<File> fileList = listFilesRecursive(fromPath);

		// Tenta mover os arquivos
		for (File f : fileList) {
			String newFilePath = toPath
					+ "/"
					+ relativePath(f.getAbsolutePath(), fromPath
							.getAbsolutePath()) + f.getName();
			File newFile = new File(newFilePath);

			final boolean created = newFile.getParentFile().mkdirs();
			if (!created && logger.isDebugEnabled()) logger
					.debug("N�o foi possível criar os diret�rios para "
							+ newFile.getParentFile().getAbsolutePath());

			// res vira true se algum rename falhar
			shallTryCopy |= f.renameTo(newFile);
		}

		// Se por acaso algum dos renames falhou, copia os arquivos mesmo
		if (shallTryCopy) FileUtils.copyDirectory(fromPath, toPath);

		// Limpa o diret�rio
		FileUtils.cleanDirectory(fromPath);

		final long end_time = System.currentTimeMillis();

		return (end_time - start_time);
	}

	/**
	 * Retorna o relativePath de absolutePath a partir de startDir
	 * 
	 * M�todo bob�o, compara os dois paths passados (tokens s�o "/") e elimina
	 * at� onde forem iguais.
	 * 
	 * @param absolutePath
	 *            O arquivo a se obter o relativepath
	 * @param startDir
	 *            De onde se quer considerar o comeco
	 * @return O path relativo
	 */
	public static String relativePath(String absolutePath, String startDir) {
		String[] absolute = absolutePath.split("/");
		String[] remove = startDir.split("/");

		// Varre os dois vetores at� o ponto em que diferem
		int start = 0;
		while (absolute.length > start && remove.length > start
				&& absolute[start].equals(remove[start])) {
			start++;
		}

		// Monta de volta o caminho
		String return_path = "";
		for (int i = start; i < absolute.length - 1; i++)
			return_path += absolute[i] + "/";
		// return_path += absolute[absolute.length-1];

		return return_path;
	}

	/**
	 * Retorna todas as refer�ncias a arquivos (mas n�o diret�rios) dentro da
	 * pasta passada
	 * 
	 * @param path
	 * @return
	 * @throws IOException
	 */
	public static List<File> listFilesRecursive(String path) throws IOException {
		return listFilesRecursive(new File(path));
	}

	public static List<File> listFilesRecursive(File path) throws IOException {
		if (!path.isDirectory()) 
			throw new IOException(path + " is not a directory.");

		return DummyDirectoryWalker.search(path);
	}
}

class DummyDirectoryWalker extends DirectoryWalker<File> {

	private final static IOFileFilter DIR_FILTER = FileFilterUtils.and(FileFilterUtils.directoryFileFilter(), HiddenFileFilter.VISIBLE);
	private final static IOFileFilter FILE_FILTER = FileFilterUtils.fileFileFilter();
	private final static FileFilter FILE_OR_DIR_FILTER = FileFilterUtils.or(DIR_FILTER, FILE_FILTER);
	private final static DummyDirectoryWalker INSTANCE = new DummyDirectoryWalker();

	private DummyDirectoryWalker() {
		super(FILE_OR_DIR_FILTER, -1);
	}

	public static List<File> search(File startDirectory) throws IOException {
		List<File> results = new ArrayList<File>();
		INSTANCE.walk(startDirectory, results);
		return results;
	}

	@Override
	protected void handleFile(File file, int depth, Collection<File> results) {
		results.add(file);
	}
}
