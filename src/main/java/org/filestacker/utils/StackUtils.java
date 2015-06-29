package org.filestacker.utils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.jpountz.lz4.LZ4Factory;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorInputStream;
import org.apache.commons.io.DirectoryWalker;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.log4j.Logger;

/** Diversos m�todos utilit�rios de I/O comuns a outras classes das Stacks. */

public final class StackUtils {
	// N�o pode ser instanciada.
	private StackUtils() {};

	private static final Logger logger = Logger.getLogger(StackUtils.class);

	public static byte[] uncompress(byte[] data) {
		return LZ4.safeDecompressor().decompress(data, 0, data.length, data.length*3);
	}
	
	public static byte[] compress(byte[] data) {
		return LZ4.fastCompressor().compress(data);
	}

	public static final LZ4Factory LZ4 = LZ4Factory.safeInstance();
	
	
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
	public static String toHexadecimal(byte[] in, int ini, int len) {
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

	public static DataOutputStream getDataStream(final File outputfile)
			throws IOException {
		OutputStream stream = new BufferedOutputStream(new FileOutputStream(outputfile, false));
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
		if (!file_in.exists()) {
			logger.warn("Arquivo de leitura "+ file_in.getAbsolutePath() + " n�o existe."
							+ "Merge n�o realizado (normal caso tenha sido apenas reaproveitado espaco)");
			return;
		}
		
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
