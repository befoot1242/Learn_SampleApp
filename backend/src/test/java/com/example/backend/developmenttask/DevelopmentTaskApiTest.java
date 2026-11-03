package com.example.backend.developmenttask;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:development-task-test;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class DevelopmentTaskApiTest {
	@Autowired
	private MockMvc mvc;

	@Test
	void requiresAuthentication() throws Exception {
		mvc.perform(get("/api/development-tasks")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/development-tasks/DT-001/tabs/requirements")).andExpect(status().isUnauthorized());
	}

	@Test
	void bothRolesCanReadListAndEveryTab() throws Exception {
		for (String role : new String[] { "USER", "ADMIN" }) {
			mvc.perform(get("/api/development-tasks").with(user("test").roles(role)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].id").value("DT-001"))
					.andExpect(jsonPath("$[0].name").value("取引検索の改善"));
			for (String tab : new String[] { "requirements", "basic", "detail", "implementation", "remaining" }) {
				mvc.perform(get("/api/development-tasks/DT-001/tabs/" + tab).with(user("test").roles(role)))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.taskId").value("DT-001"))
						.andExpect(jsonPath("$.tab").value(tab))
						.andExpect(jsonPath("$.content").isNotEmpty());
			}
		}
	}

	@Test
	void distinguishesEmptyContentMissingTaskAndInvalidTab() throws Exception {
		mvc.perform(get("/api/development-tasks/DT-002/tabs/basic").with(user("test").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value((Object) null));
		mvc.perform(get("/api/development-tasks/missing/tabs/basic").with(user("test").roles("USER")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
				.andExpect(jsonPath("$.retryable").value(false));
		mvc.perform(get("/api/development-tasks/DT-001/tabs/unknown").with(user("test").roles("USER")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TAB"));
	}
}
