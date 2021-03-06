package org.filestacker.service;

public abstract class StackerException extends Exception {

	private static final String default_msg = "GeneralStackerException";

	public StackerException() {
		super(default_msg);
	}

	public StackerException(String message) {
		super(message);
	}
}
