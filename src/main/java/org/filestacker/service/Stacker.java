package org.filestacker.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.filestacker.utils.HashBiMap;
import org.filestacker.utils.StackUtils;

public class Stacker {

	private static final Logger logger = Logger.getLogger(Stacker.class);

	protected HashBiMap<String, Integer> namespace = new HashBiMap<String, Integer>();
	protected List<StackFreeSlot> freeSlots = new ArrayList<StackFreeSlot>();
	protected List<Integer> deleted_stackids = new ArrayList<Integer>();

	protected StackerEntry[] entries = new StackerEntry[0];
	protected StackerEntry lastEntry = null;

	protected int nextStackId = 0;
	protected int totalDocs = 0;

	protected final String stacksPath;
	protected final boolean singleMode;
	protected final boolean useCompression;

	private static final boolean DEFAULT_SINGLEMODE = true;
	private static final boolean DEFAULT_COMPRESSION = false;

	public Stacker(final String path) {
		this(path, DEFAULT_SINGLEMODE, DEFAULT_COMPRESSION);
	}

	public Stacker(final String path, boolean threadSafe, boolean compression) {
		stacksPath = path;
		singleMode = threadSafe;
		useCompression = compression;

		boolean created = new File(stacksPath).mkdirs();
		if (!created && logger.isDebugEnabled()) 
			logger.debug("Não foi possivel criar o diretorio " + path + " para as stacks");
	}

	protected Stacker(final String path, final LocalStack[] stacks, boolean threadSafe, boolean compression) throws IOException {
		this(path, threadSafe, compression);

		entries = new StackerEntry[stacks.length];

		for (int i = 0; i < stacks.length; i++) {
			entries[i] = new StackerEntry(stacks[i], singleMode);
			totalDocs += entries[i].getNumFiles();

			// Carregue o local namespace de cada stack juntando no stacker
			byte[][] localspace = entries[i].getNamespace();
			// Carregue os nomes dos arquivos não deletados
			for (int k = 0; k < localspace.length
					&& k < entries[i].getNumFiles(); k++) {
				if (!entries[i].isDeleted(entries[i].firstId + k)) {
					namespace.put(StackUtils.toHexadecimal(localspace[k]),
							entries[i].firstId + k);
					// else
					// freeSlots.add(entries[i].getDeletedSlot(k));
				}
			}

			if (!useCompression)
				freeSlots.addAll(entries[i].getDeleteds());
		}

		if (entries.length > 0) {
			lastEntry = entries[entries.length - 1];
			nextStackId = lastEntry.getNextId();
		} else {
			lastEntry = null;
			nextStackId = 0;
		}
	}

	public static Stacker loadStacker(final String path) throws IOException {
		return loadStacker(path, DEFAULT_SINGLEMODE, DEFAULT_COMPRESSION);
	}

	protected static LocalStack[] stacks(String path) throws IOException {
		String[] extensions = { "stk" };
		Collection<File> files = FileUtils.listFiles(new File(path), extensions, true);
		Collections.sort((List<File>) files);
		LocalStack[] stacks = new LocalStack[files.size()];

		int count = 0;
		for (File stackFile : files) {
			stacks[count++] = LocalStack.loadStack(stackFile);
		}

		return stacks;
	}

	public static Stacker loadStacker(final String path, boolean threadSafe, boolean compression)  throws IOException {
		LocalStack[] stacks = stacks(path);
		return new Stacker(path, stacks, threadSafe, compression);
	}

	public int addFile(final String filename, byte[] filedata) {
		/*
		 * se current é nulo crie nova stack se o nome ja existe, delete o
		 * arquivo current.append se append retorna false, current = null
		 * recursivo se foi ok atualiza namespace
		 */

		if (useCompression)
			filedata = StackUtils.compress(filedata);

		try {
			int result;
			if ((result = nameToId(filename)) != -1) {
				deleteFile(result);
			}

			// TODO tenta replaceSlot
			int replace_id = tryToReplace(filename, filedata);
			if (replace_id >= 0) { return replace_id; }

			if (lastEntry == null) {
				createNewStack();
			}

			if (lastEntry.append(filename, filedata)) {
				namespace.put(StackUtils.strToHexaMD5(filename), nextStackId);
				int return_stackid = nextStackId;
				totalDocs++;
				nextStackId = lastEntry.getNextId();
				return return_stackid;
			} else {
				lastEntry = null;
				return addFile(filename, filedata);
			}
		} catch (IOException ioe) {
			logger.warn("Não foi possível adicionar o doc " + filename + " na stack", ioe);
			return -1;
		}
	}

	private int tryToReplace(final String filename, final byte[] filedata)
			throws IOException {
		int datasize = filedata.length;

		if (freeSlots.size() == 0) { 
			return -1; 
		}

		if (datasize > freeSlots.get(freeSlots.size() - 1).size) {
			// System.err.println("É, "+datasize+" não cabe em nenhum slot! Maior slot: "+freeSlots.get(freeSlots.size()-1).size);
			return -2;
		}

		StackFreeSlot slot = searchSlot(0, freeSlots.size() - 1, datasize);

		if (slot.stack.replace(slot.position, filename, filedata)) {
			logger.debug("Utilizando slot vago " + slot + " para " + filename);
			namespace.put(StackUtils.strToHexaMD5(filename), (slot.stack.firstId + slot.position));
			freeSlots.remove(slot);
			// Collections.sort(freeSlots);
			return (slot.stack.firstId + slot.position);
		} else {
			return -3;
		}
	}

	public boolean deleteFile(String stackFile) throws IOException {
		int stackid = nameToId(stackFile);
		if (stackid >= 0)
			return deleteFile(stackid);
		else
			return false;
	}
	
	public boolean deleteFile(int stackid) throws IOException {
		logger.debug("Vou tentar deletar " + stackid);
		StackerEntry entry = searchEntry(stackid);

		StackFreeSlot slot = entry.deleteFile(stackid);

		if (slot == null)
			return false;

		// totalDocs--;
		if (!useCompression) {
			freeSlots.add(slot);
			Collections.sort(freeSlots);
		}
		// printSlotList();

		deleted_stackids.add(stackid);
		String name_to_remove = namespace.inverse().get(stackid);
		logger.debug("Adicionando " + name_to_remove + "(" + stackid
				+ ")	na lista de deletados (before: "
				+ deleted_stackids.size() + ")");
		namespace.remove(name_to_remove);

		return true;
	}

	/**
	 * Procura pelo melhor slot // [ first , last ]
	 * 
	 * @param first
	 * @param last
	 * @param data_size
	 * @return
	 */
	private StackFreeSlot searchSlot(final int first, final int last,
			final int data_size) {
		int pivot = (first + last) / 2;

		if (first == last) { return freeSlots.get(first); }

		if (freeSlots.get(pivot).size >= data_size) {
			return searchSlot(first, pivot, data_size);
		} else {
			// if (freeSlots.get(pivot).size < data_size )
			return searchSlot(pivot + 1, last, data_size);
		}
	}

	/**
	 * 
	 */
	private final void createNewStack() {
		lastEntry = new StackerEntry(new LocalStack(nextStackId, stacksPath), singleMode);

		StackerEntry[] backup = entries;
		entries = new StackerEntry[backup.length + 1];
		System.arraycopy(backup, 0, entries, 0, backup.length);
		entries[entries.length - 1] = lastEntry;
	}

	public boolean contains(String filename) {
		return nameToId(filename) > -1;
	}

	public int nameToId(final String filename) {
		String hash = StackUtils.strToHexaMD5(filename);
		Integer i = namespace.get(hash);
		if (i == null) { 
			return -1; 
		}
		return i;
	}

	public byte[] searchFile(final String filename) throws IOException {
		int stackid = nameToId(filename);
		if (stackid == -1) { 
			return new byte[0]; 
		}

		return searchFile(stackid);
	}

	public byte[] searchFile(final int stackid) throws IOException {
		// XXX byte[0], null ou exceptions?
		if (stackid >= nextStackId) { 
			return new byte[0]; 
		}

		StackerEntry entry = searchEntry(stackid);

		byte[] data = entry.get(stackid);

		if (useCompression)
			data = StackUtils.uncompress(data);

		return data;
	}

	public StackerEntry searchEntry(final int stackid) {
		return searchEntry(0, entries.length, stackid);
	}

	public StackerEntry searchEntry(int first, int last, int stackid) {
		// Defensiva: evita overflow
		int pivot = first / 2 + last / 2;

		if (stackid < entries[pivot].firstId) { 
			return searchEntry(first, pivot, stackid); 
		}

		if (stackid > entries[pivot].getLastId()) { 
			return searchEntry(pivot + 1, last, stackid); 
		}

		return entries[pivot];
	}

	public void optimize() throws IOException {
		lastEntry.writeStack();
	}

	public void close() {
		for (StackerEntry entry : entries) {
			entry.close();
		}
		entries = null;
	}

	public void freeEntriesNamespaces() {
		for (StackerEntry entry : entries) {
			entry.freeNamespace();
		}
	}

	public boolean isDeleted(int stackid) throws IOException {
		StackerEntry entry = searchEntry(stackid);
		return entry.isDeleted(stackid);
	}

	public void printSlotList() {
		for (StackFreeSlot slot : freeSlots) {
			System.err.println(slot);
		}
	}

	public final int getTotalDocs() {
		return totalDocs;
	}

	public List<Integer> getDeletedStackIds() {
		return deleted_stackids;
	}

	public StackerEntry[] getEntries() {
		return entries;
	}

	public int getLastId() {
		if (entries.length == 0) { 
			return -1; 
		}

		return entries[entries.length - 1].getLastId();
	}
}