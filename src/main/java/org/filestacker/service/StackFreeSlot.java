package org.filestacker.service;

/**
 * Note: this class has a natural ordering that is inconsistent with equals
 * 
 * @author daniel
 * 
 */
public class StackFreeSlot implements Comparable<StackFreeSlot> {
	int size;
	int position;
	StackerEntry stack;

	public StackFreeSlot(StackerEntry stack, int position, int size) {
		this.stack = stack;
		this.position = position;
		this.size = size;
	}

	protected void setEntry(StackerEntry stack) {
		this.stack = stack;
	}

	/**
	 * Note: this class has a natural ordering that is inconsistent with equals
	 * (x.compareTo(y)==0) != (x.equals(y)) compareTo é usado para a ordenação
	 * em ordem do tamanho, mas não serve at all como forma de identificar uma
	 * stack.
	 */
	@Override
	public int compareTo(StackFreeSlot arg0) {
		if (this.size > arg0.size) { return 1; }
		if (this.size < arg0.size) { return -1; }

		return 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + position;
		result = prime * result + size;
		result = prime * result + ((stack == null) ? 0 : stack.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) { return true; }
		if (obj == null) { return false; }
		if (getClass() != obj.getClass()) { return false; }
		StackFreeSlot other = (StackFreeSlot) obj;
		if (position != other.position) { return false; }
		if (size != other.size) { return false; }
		if (stack == null) {
			if (other.stack != null) { return false; }
		} else if (!stack.equals(other.stack)) { return false; }
		return true;
	}

	@Override
	public String toString() {
		// return
		// "Stack: "+stack.firstId+" / Position: "+position+" / Size: "+size;
		return "Stack: " + stack.firstId + " / Tabid: "
				+ (stack.firstId + position) + " / Size: " + size;
	}
}
