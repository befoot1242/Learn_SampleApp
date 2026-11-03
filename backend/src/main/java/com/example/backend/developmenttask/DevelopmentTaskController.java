package com.example.backend.developmenttask;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/development-tasks")
public class DevelopmentTaskController {
	private static final Set<String> TABS = Set.of("requirements", "basic", "detail", "implementation", "remaining");
	private final DevelopmentTaskRepository repository;

	public DevelopmentTaskController(DevelopmentTaskRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	public List<TaskSummary> list() {
		return repository.findAll(org.springframework.data.domain.Sort.by("id")).stream()
				.map(task -> new TaskSummary(task.getId(), task.getName(), task.getStatus(), task.getUpdatedDate()))
				.toList();
	}

	@GetMapping("/{id}/tabs/{tab}")
	public ResponseEntity<?> tab(@PathVariable String id, @PathVariable String tab) {
		if (!TABS.contains(tab)) {
			return ResponseEntity.badRequest().body(Map.of(
					"code", "INVALID_TAB", "message", "タブ種別が不正です", "retryable", false));
		}
		return repository.findById(id)
				.<ResponseEntity<?>>map(task -> ResponseEntity.ok(new TabContent(task.getId(), tab, content(task, tab))))
				.orElseGet(() -> ResponseEntity.status(404).body(Map.of(
						"code", "TASK_NOT_FOUND", "message", "開発タスクが見つかりません", "retryable", false)));
	}

	private String content(DevelopmentTask task, String tab) {
		return switch (tab) {
			case "requirements" -> task.getRequirements();
			case "basic" -> task.getBasic();
			case "detail" -> task.getDetail();
			case "implementation" -> task.getImplementation();
			case "remaining" -> task.getRemaining();
			default -> throw new IllegalArgumentException("Invalid tab: " + tab);
		};
	}

	public record TaskSummary(String id, String name, String status, LocalDate updatedDate) {}
	public record TabContent(String taskId, String tab, String content) {}
}
