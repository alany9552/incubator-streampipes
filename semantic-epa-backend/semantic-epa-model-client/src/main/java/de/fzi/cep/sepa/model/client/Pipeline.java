package de.fzi.cep.sepa.model.client;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

@Entity
public class Pipeline {

	@OneToMany(cascade=CascadeType.ALL)
	private List<SEPAClient> sepas;
	
	@OneToMany(cascade=CascadeType.ALL)
	private List<StreamClient> streams;
	
	@OneToOne(cascade=CascadeType.ALL)
	private ActionClient action;
	
	@OneToOne(cascade=CascadeType.ALL)
	private ExecutionStatus pipelineStatus;
	
	private String name;
	private String description;

	public List<SEPAClient> getSepas() {
		return sepas;
	}

	public void setSepas(List<SEPAClient> sepas) {
		this.sepas = sepas;
	}

	public List<StreamClient> getStreams() {
		return streams;
	}

	public void setStreams(List<StreamClient> streams) {
		this.streams = streams;
	}

	public ActionClient getAction() {
		return action;
	}

	public void setAction(ActionClient action) {
		this.action = action;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public ExecutionStatus getPipelineStatus() {
		return pipelineStatus;
	}

	public void setPipelineStatus(ExecutionStatus pipelineStatus) {
		this.pipelineStatus = pipelineStatus;
	}
	
	
	
	
}
