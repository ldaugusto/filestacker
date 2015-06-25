package org.filestacker.service;

public interface Stack {
	public final int MAX_SIZE = 64 * 1024 * 1024; // 64MB
	public final int MAX_FILES = 32 * 1024; // 32768 arquivos
	public final int HEADER_SIZE = (2 * Integer.SIZE + 2 * Long.SIZE) / 8 + 16; // 40
	// bytes

	// MAX_FILES +1 para marcar começo e fim de todos os arquivos
	public final int INDEX_SIZE = (MAX_FILES + 1) * Integer.SIZE / 8; //

	// Sequencia: HEADER - INDEX - STATUS - NAMESPACE
	// Tamanho do hash de cada filename: 16 bytes via MD5
	public final int HASHEDNAME_SIZE = 16;

	// Tamanho do flag de status por arquivo: 1 bit
	public final int STATUSFIELD_SIZE = 1; // 1/8 byte

	// Estrutura onde são armazenados os status de cada arquivo da tablet
	public final int STATUS_SIZE = MAX_FILES / (STATUSFIELD_SIZE * 8);

	// Estrutura onde são armazenados os 'nomes' de cada arquivo da tablet
	public final int NAMESPACE_SIZE = MAX_FILES * HASHEDNAME_SIZE;

	// Marca as posições do começo dessas estruturas
	public final int STATUS_OFFSET = HEADER_SIZE + INDEX_SIZE;
	public final int NAMESPACE_OFFSET = STATUS_OFFSET + STATUS_SIZE;

	// Ponto onde começam os arquivos armazenados
	public final int DATA_OFFSET = HEADER_SIZE + INDEX_SIZE + STATUS_SIZE
			+ NAMESPACE_SIZE;
}
