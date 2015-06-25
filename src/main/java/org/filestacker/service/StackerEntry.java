package org.filestacker.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Encapsulamento para uma Stack, de forma que funcione da forma com que o
 * Stacker precisa: - thread-safe : StackerEntry possui um lock para
 * restringir acesso - tabis : converter os stackids para 'positions' internas da
 * stack. - outras necessidades
 * 
 * @author daniel
 */

public class StackerEntry {

	private final LocalStack stack;
	private final Lock lock;

	public final int firstId;
	public final boolean realLock;

	public StackerEntry(LocalStack stack, boolean threadSafe) {
		this.stack = stack;
		firstId = stack.firstStackId;
		if (threadSafe)	lock = new ReentrantLock();
		else			lock = new FakeLock();
		realLock = threadSafe;
	}
	
	public StackerEntry(LocalStack stack) {
		this(stack, true);
	}

	public int getLastId() {
		return firstId + stack.nextPosition - 1;
	}

	public int getNextId() {
		return firstId + stack.nextPosition;
	}

	public int getNumFiles() {
		return stack.numFiles;
	}

	public void writeStack() throws IOException {
		stack.writeStack();
	}

	public byte[] get(int stackid) throws IOException {
		try {
			lock.lock();
			if (stack.offsets == null) {
				stack.reloadHeader();
			}
			return stack.get(stackid - stack.firstStackId);
		} catch (IOException ioe) {
			throw ioe;
		} finally {
			lock.unlock();
		}
	}

	public byte[][] getNamespace() throws IOException {
		try {
			lock.lock();
			if (stack.hashedNames == null) {
				stack.reloadHeader();
			}
			return stack.hashedNames;
		} catch (IOException e) {
			throw e;
		} finally {
			lock.unlock();
		}
	}

	public void freeNamespace() {
		try {
			lock.lock();
			if (stack.hashedNames != null) {
				stack.hashedNames = null;
			}
		} finally {
			lock.unlock();
		}
	}

	public int getLength(int stackid) {
		int position = stackid - stack.firstStackId;
		return stack.offsets[position + 1] - stack.offsets[position];
	}

	public List<StackFreeSlot> getDeleteds() throws IOException {
		try {
			lock.lock();
			if (stack.statusFiles == null) {
				stack.reloadHeader();
			}

			List<StackFreeSlot> list = stack.getDeleteds();
			for (StackFreeSlot slot : list) {
				slot.setEntry(this);
			}

			return list;
		} catch (IOException e) {
			throw e;
		} finally {
			lock.unlock();
		}
	}

	public StackFreeSlot deleteFile(int stackid) throws IOException {
		try {
			lock.lock();
			if (stack.statusFiles == null || stack.offsets == null) {
				stack.reloadHeader();
			}

			int position = stackid - stack.firstStackId;
			if (stack.delete(position)) {
				return getDeletedSlot(position);
			} else {
				return null;
			}
		} catch (IOException e) {
			throw e;
		} finally {
			lock.unlock();
		}
	}

	public StackFreeSlot getDeletedSlot(int position) {
		return new StackFreeSlot(this, position, stack.offsets[position + 1]
				- stack.offsets[position]);
	}

	public boolean isDeleted(int stackid) throws IOException {
		try {
			lock.lock();
			if (stack.statusFiles == null) {
				stack.reloadHeader();
			}
			int position = stackid - stack.firstStackId;
			return stack.isDeleted(position);
		} catch (IOException e) {
			throw e;
		} finally {
			lock.unlock();
		}
	}

	public boolean append(String filename, byte[] filedata) throws IOException {
		try {
			lock.lock();
			if (stack.offsets == null) {
				stack.reloadHeader();
			}
			return stack.append(filename, filedata);
		} catch (IOException e) {
			throw e;
		} finally {
			lock.unlock();
		}
	}

	public boolean close() {
		return stack.close();
	}

	public boolean replace(int position, String filename, byte[] filedata)
			throws IOException {
		try {
			lock.lock();
			if (stack.statusFiles == null || stack.offsets == null
					|| stack.hashedNames == null) {
				stack.reloadHeader();
			}
			return stack.replace(position, filename, filedata);
		} catch (IOException e) {
			throw e;
		} finally {
			lock.unlock();
		}
	}
}

class FakeLock implements Lock {

	@Override
	public void lock() {
	}

	@Override
	public void lockInterruptibly() throws InterruptedException {
	}

	@Override
	public boolean tryLock() {
		return true;
	}

	@Override
	public boolean tryLock(long time, TimeUnit unit)
			throws InterruptedException {
		return true;
	}

	@Override
	public void unlock() {
	}

	@Override
	public Condition newCondition() {
		return null;
	}
	
}
