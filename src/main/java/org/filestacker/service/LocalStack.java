package org.filestacker.service;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;
import org.filestacker.utils.StackUtils;

public class LocalStack implements Stack {

	protected static final Logger logger = Logger.getLogger(LocalStack.class);

	// Estruturas de Dados de I/O
	/**
	 * O arquivo que representa esta stack em disco
	 */
	private File file;
	/**
	 * Permite leitura e 'replaces' na stack.
	 */
	private RandomAccessFile inout;
	/**
	 * Permite os 'appends' velozes num arquivo temporario. S„o 'merge'-ados
	 * depois ao arquivo da stack.
	 */
	private DataOutputStream out;

	// Estruturas de Dados do Header da stack: protected para serem visiveis
	// pelo stackerEntry
	/**
	 * Ponteiros com as posic√µes dos arquivos. 1 int para cada arquivo
	 */
	protected int[] offsets = null;
	/**
	 * Estado dos arquivos: deletados ou n„o. 1 bit para cada arquivo (1 int =
	 * 32 arquivos)
	 */
	protected int[] statusFiles = null;
	/**
	 * Vetorz„o com todos os nomes de arquivos hasheados. 16 bytes por arquivo.
	 */
	protected byte[][] hashedNames = null;
	/**
	 * Dados opcionais, escritos no cabecalho
	 */
	protected int optData;
	/**
	 * A pr√≥xima posic„o para append na stack.
	 */
	protected int nextPosition = 0;
	/**
	 * Quantidade de arquivos nesta stack.
	 */
	protected int numFiles = 0;
	/**
	 * Tamanho do arquivo da stack.
	 */
	protected long stackLength = 0;

	protected long creationTime = 0;
	/**
	 * O id do primeiro campo desta stack.
	 */
	protected int firstStackId;

	public final static byte FILL_CHAR = (byte) 32;
	
	/**
	 * Construtor utilizado para construir uma nova stack.
	 * 
	 * @param firstStackId
	 */
	public LocalStack(int firstId, String path) {
		this.firstStackId = firstId;

		// Gera o objeto que referencia o arquivo da stack
		file = StackUtils.generateStackFile(firstStackId, path);

		creationTime = System.currentTimeMillis();

		if (!file.exists()) {
			logger.debug("new(): " + file.getName());
		} else if (file.canWrite() && file.canRead()) {
			boolean deleted = file.delete();
			logger.debug("new(): arquivo " + file.getAbsolutePath() + " jÔøΩ existia. "
					+ (deleted ? "Vai ser sobreescrito." : "E nÔøΩo foi possÔøΩvel deletÔøΩ-lo"));
		} else {
			logger.error("new(): nÔøΩo pode ler ou escrever o arquivo.");
		}
	}

	public static LocalStack loadStack(String path) throws IOException {
		return loadStack(new File(path));
	}

	public static LocalStack loadStack(File file) throws IOException {
		if (!file.exists()) { 
			throw new FileNotFoundException("stack file not found"); 
		}
		
		if (!file.canWrite() || !file.canRead()) { 
			throw new IOException("stack file should have read and write permissions"); 
		}

		return new LocalStack(file);
	}

	/**
	 * Construtor utilizado somente para carregar stacks j√° existentes.
	 * 
	 * @param file
	 */
	private LocalStack(File file) {
		// Aponta o objeto que referencia o arquivo da stack
		this.file = file;
		loadStack();
	}

	public int getNumFiles() {
		return numFiles;
	}

	public int getFirstId() {
		return firstStackId;
	}

	public File getStackFile() {
		return file;
	}

	/**
	 * Carrega index, status e namespace
	 */
	protected void reloadHeader() throws IOException {
		open();

		// Se o arquivo da stack exister e tiver dados, recarregue os dados
		if (inout != null && nextPosition > 0) {
			// ### LOAD OFFSETS
			// O tamanho exato do indice
			byte[] buffer = new byte[INDEX_SIZE];
			// Salta para a posic„o do indice
			inout.seek(HEADER_SIZE);
			// Carrega o indice para um vetor de bytes
			inout.read(buffer);
			// Converte em um vetor de inteiros
			offsets = StackUtils.byteArrayToIntArray(buffer);

			if (offsets.length != MAX_FILES + 1) {
				logger.warn("offsets.length = " + offsets.length
						+ " / MAX_FILES+1 = " + MAX_FILES + 1);
			}

			// ### LOAD STATUS
			// Defensiva...
			inout.seek(STATUS_OFFSET);
			statusFiles = new int[STATUS_SIZE / 4];
			for (int i = 0; i < STATUS_SIZE / 4; i++) {
				statusFiles[i] = inout.readInt();
			}
			numFiles = nextPosition - getDeleteds().size();

			// ### LOAD NAMESPACE
			inout.seek(NAMESPACE_OFFSET);
			hashedNames = new byte[MAX_FILES][HASHEDNAME_SIZE];
			for (int i = 0; i < nextPosition; i++) {
				int readed = inout.read(hashedNames[i]);
				if (readed != hashedNames[i].length) 
					logger.warn("Esperava ler " + hashedNames[i].length + "bytes, mas sÔøΩ leu " + readed);
			}
		} else {
			stackLength = DATA_OFFSET;
			offsets = new int[MAX_FILES + 1];
			offsets[0] = DATA_OFFSET;
			statusFiles = new int[STATUS_SIZE / 4];
			hashedNames = new byte[MAX_FILES][HASHEDNAME_SIZE];
		}

		// TODO carregar valores em LOAD
	}

	public List<StackFreeSlot> getDeleteds() {
		List<StackFreeSlot> list = new ArrayList<StackFreeSlot>();

		for (int i = 0; i < nextPosition; i++) {
			if (isDeleted(i)) {
				list.add(new StackFreeSlot(null, i, offsets[i + 1]
						- offsets[i]));
			}
		}

		return list;
	}

	/**
	 * Limpa as estruturas para o garbage collector atuar
	 */
	public void clearMemory() {

	}

	protected int[] getIndex() throws IOException {
		if (offsets == null && nextPosition > 0) {
			reloadHeader();
		}
		return offsets;
	}

	public boolean append(String filename, byte[] filedata) {
		try {
			// Carrega estruturas e abre o arquivo de escrita, caso n„o estejam
			// abertos
			if (offsets == null || out == null) {
				reloadHeader();
				out = StackUtils.getDataStream(StackUtils.getTempFile(file));
			}

			// Se for poss√≠vel adicionar outro arquivo e com este dado
			// tamanho... adicione!
			if (nextPosition + 1 <= MAX_FILES
					&& stackLength + filedata.length <= MAX_SIZE) {
				// Escreve os dados no arquivo
				out.write(filedata);

				// Armazena a posic„o final deste arquivo no vetor de ponteiros
				// Relembrando: 
				// offsets.length = stack.MAXFILES+1 and 
				// offsets[0] = DATA_OFFSET
				offsets[nextPosition + 1] = offsets[nextPosition] + filedata.length;

				// Armazena o nome do arquivo hasheado
				hashedNames[nextPosition] = StackUtils.strToMD5(filename);

				// Atualiza as vari√°veis do cabecalho
				stackLength += filedata.length;
				nextPosition++;
				numFiles++;

				// TODO Adicionar nome do arquivo
				return true;
			} else {
				writeStack();

				return false;
			}

		} catch (IOException ioe) {
			logger.error("stack.append error", ioe);
			return false;
		}
	}

	public void writeStack() throws IOException {
		if (out != null) {
			out.close();
		}

		// o arquivo stack ainda n„o existe, crie-o do zero da forma mais
		// rapida
		if (!file.exists()) {
			out = StackUtils.getDataStream(file);
			writeHeaders(out);
			out.close();
		} else {
			open();
			inout.seek(0);
			writeHeaders(inout);
			close();
		}
		
		// Adiciona a colecao apos o indice no arquivo file
		StackUtils.mergeFiles(file);

		if (!StackUtils.getTempFile(file).delete()) {
			logger.warn("N„o conseguiu deletar o arquivo " + file);
		}
		// TODO LastWrite, MD5 e cia

		out = null;
	}

	private void writeHeaders(DataOutput out) throws IOException {
		// Escreve o HEADER
		out.writeInt(firstStackId); // FIRSTID
		out.writeInt(nextPosition); // NUMDOCS
		out.writeLong(creationTime); // CREATION TIME
		out.writeLong(System.currentTimeMillis()); // UPDATE TIME

		// MD5 ser√° adicionado depois
		out.write(new byte[Stack.HASHEDNAME_SIZE]); // MD5

		updateHeaderStructs(out);
	}

	private void updateHeaderStructs(DataOutput out) throws IOException {
		// TODO tirar a programac„o defensiva se ao final da programac„o,
		// n„o houver outra forma de serem criados os vetores exceto com tamanho
		// m√°ximo.

		// Descarregar o vetor dos offsets guardados
		for (int offset : offsets) {
			out.writeInt(offset);
		}
		// Defensiva: Se por algum motivo n„o estiver do tamanho certo, compense
		for (int i = 0; i < (MAX_FILES + 1) - offsets.length; i++) {
			out.writeInt(0);
		}

		// Descarregar o vetor de deletados
		for (int statusFile : statusFiles) {
			out.writeInt(statusFile);
		}
		// Defensiva: Se por algum motivo n„o estiver do tamanho certo, compense
		for (int i = 0; i < STATUS_SIZE / 4 - statusFiles.length; i++) {
			out.writeInt(0);
		}

		// Descarregar os nomes dos arquivos
		for (byte[] hashedName : hashedNames) {
			out.write(hashedName);
		}
		// Defensiva: Se por algum motivo n„o estiver do tamanho certo, compense
		for (int i = 0; i < MAX_FILES - hashedNames.length; i++) {
			out.write(new byte[HASHEDNAME_SIZE]);
		}
	}

	public byte[] get(int position) throws IOException {
		if (position < 0 || position >= nextPosition) { return new byte[0]; }

		int size = offsets[position + 1] - offsets[position];

		open();

		inout.seek(offsets[position]);

		byte bytes[] = new byte[size];

		inout.readFully(bytes);

		return bytes;
	}

	public byte[] get(String filename) throws IOException {
		byte[] queryname = StackUtils.strToMD5(filename);
		MAIN: 
		for (int i = 0; i < hashedNames.length; i++) {
			for (int k = 0; k < hashedNames[i].length; k++) {
				if (hashedNames[i][k] != queryname[k]) {
					continue MAIN;
				}
			}
			return get(i);
		}
		return new byte[0];
	}

	public void dumpNames() {
		for (byte[] name : hashedNames) {
			if (name[0] != 0) {
				System.err.println(StackUtils.toHexadecimal(name));
			}
		}
	}

	public boolean open() {
		try {
			if (inout == null) {
				inout = new RandomAccessFile(file, "rw");
			}
			return true;
		} catch (IOException e) {
			logger.error("Erro durante operac„o stack "+file+" open()", e);
			inout = null;
			return false;
		}
	}

	public boolean close() {
		try {
			if (inout != null) {
				inout.close();
			}
			inout = null;
			return true;
		} catch (IOException e) {
			inout = null;
			logger.error("Erro durante operac„o stack "+file+" close()\n"
					+ e.getMessage());
			return false;
		}
	}

	private boolean loadStack() {
		try {
			open();
			inout.seek(0);

			firstStackId = inout.readInt();
			nextPosition = inout.readInt();
			creationTime = inout.readLong();
			stackLength = file.length();

			reloadHeader();
			return true;
		} catch (IOException e) {
			logger.error("Exception loading stack "+file, e);
			return false;
		}
	}

	public boolean isDeleted(int position) {
		if (position < 0 || position >= nextPosition) { return false; }

		// Descobre em que bloco (conjunto de Integer.SIZE arquivos) est√° esse
		// arquivo
		int block = position / Integer.SIZE;
		// Cria uma m√°scara com bit setado na posic„o dentro do bloco.
		// Exemplos: position 0 = ...00001 | position 3 = ...01000 | position 65
		// = ...00010
		int bitmask = 1 << (position % Integer.SIZE);

		// Faz AND do bloco com a m√°scara. Se o resultado for a m√°scara, ent„o
		// esta deletado.
		if ((statusFiles[block] & bitmask) == bitmask) {
			return true;
		} else {
			return false;
		}
	}

	public boolean delete(int position) throws IOException {
		if (position < 0 || position >= nextPosition) { return false; }

		// Descobre em que bloco (conjunto de Integer.SIZE arquivos) est√° esse
		// arquivo
		int block = position / Integer.SIZE;

		// Cria uma m√°scara com bit setado na posic„o dentro do bloco.
		// Exemplos: position 0 = ...00001 | position 3 = ...01000 | position 65
		// = ...00010
		int bitmask = 1 << (position % Integer.SIZE);

		// Salva o valor da variavel
		int backup = statusFiles[block];

		// Faz OR do bloco com a m√°scara.
		// Se o bit n„o estava setado, agora vai estar. Se j√° estava, nada
		// acontece.
		statusFiles[block] = statusFiles[block] | bitmask;

		// Se o valor de statusFiles n„o foi alterado, n„o escreva nada em
		// disco, c√°spita!
		if (statusFiles[block] == backup) { return true; }

		// Defensiva FTW
		open();

		// Vai para a posic„o e escreve no disco
		inout.seek(STATUS_OFFSET + (Integer.SIZE / 8) * block);
		inout.writeInt(statusFiles[block]);
		numFiles--;

		return true;
	}

	public boolean undelete(int position) throws IOException {
		if (position < 0 || position >= nextPosition) { return false; }

		// Descobre em que bloco (conjunto de Integer.SIZE arquivos) est√° esse
		// arquivo
		int block = position / Integer.SIZE;

		// Cria uma m√°scara com bit setado na posic„o dentro do bloco.
		// Exemplos: position 0 = ...00001 | position 3 = ...01000 | position 65
		// = ...00010
		int bitmask = 1 << (position % Integer.SIZE);

		// Salva o valor da variavel
		int backup = statusFiles[block];

		// Faz AND com complemento da m√°scara.
		// Se o bit estava setado, agora n„o vai estar. Se j√° n„o estava, nada
		// acontece.
		statusFiles[block] = statusFiles[block] & ~bitmask;

		// Se o valor de statusFiles n„o foi alterado, n„o escreva nada em
		// disco, c√°spita!
		if (statusFiles[block] == backup) { return true; }

		// Defensiva FTW
		open();

		// Vai para a posic„o e escreve no disco
		inout.seek(STATUS_OFFSET + (Integer.SIZE / 8) * block);
		inout.writeInt(statusFiles[block]);
		numFiles++;

		return true;
	}

	public boolean replace(int position, String filename, byte[] filedata)
			throws IOException {
		// Dupla Defensiva FTW
		int slotspace = (offsets[position + 1] - offsets[position]);
		if (filedata.length > slotspace) {
			return false;
		} else {
			open();
		}

		// Faz um byte[] do tamanho exato do slot, inicia com o dado...
		byte[] datafilled = new byte[slotspace];
		System.arraycopy(filedata, 0, datafilled, 0, filedata.length);

		// ... e enche o restante de whitespaces quase precise completar
		if (filedata.length < slotspace) {
			Arrays.fill(datafilled, filedata.length, slotspace, FILL_CHAR);
		}

		// Desdeleta a posic„o
		undelete(position);

		// Coloca no disco o novo dado
		inout.seek(offsets[position]);
		inout.write(datafilled);

		// Atualiza o nome
		hashedNames[position] = StackUtils.strToMD5(filename);
		inout.seek(NAMESPACE_OFFSET + HASHEDNAME_SIZE * position);
		inout.write(hashedNames[position]);

		return true;
	}
}