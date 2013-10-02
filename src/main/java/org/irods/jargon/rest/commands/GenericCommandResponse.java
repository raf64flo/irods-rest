package org.irods.jargon.rest.commands;

/**
 * A general message in response to a command
 * 
 * @author Mike Conway - DICE (www.irods.org)
 *
 */
public class GenericCommandResponse {

	public enum Status {OK}
	private Status status = Status.OK;
	
	public GenericCommandResponse() {
	}

	/**
	 * @return the status
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * @param status the status to set
	 */
	public void setStatus(Status status) {
		this.status = status;
	}

}