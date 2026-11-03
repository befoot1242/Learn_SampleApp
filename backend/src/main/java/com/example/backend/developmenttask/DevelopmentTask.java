package com.example.backend.developmenttask;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "development_task")
public class DevelopmentTask {
	@Id
	@Column(length = 40)
	private String id;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false, length = 30)
	private String status;

	@Column(nullable = false)
	private LocalDate updatedDate;

	@Column(length = 10000)
	private String requirements;

	@Column(length = 10000)
	private String basic;

	@Column(length = 10000)
	private String detail;

	@Column(length = 10000)
	private String implementation;

	@Column(length = 10000)
	private String remaining;

	protected DevelopmentTask() {
	}

	public DevelopmentTask(String id, String name, String status, LocalDate updatedDate,
			String requirements, String basic, String detail, String implementation, String remaining) {
		this.id = id;
		this.name = name;
		this.status = status;
		this.updatedDate = updatedDate;
		this.requirements = requirements;
		this.basic = basic;
		this.detail = detail;
		this.implementation = implementation;
		this.remaining = remaining;
	}

	public String getId() { return id; }
	public String getName() { return name; }
	public String getStatus() { return status; }
	public LocalDate getUpdatedDate() { return updatedDate; }
	public String getRequirements() { return requirements; }
	public String getBasic() { return basic; }
	public String getDetail() { return detail; }
	public String getImplementation() { return implementation; }
	public String getRemaining() { return remaining; }
}
