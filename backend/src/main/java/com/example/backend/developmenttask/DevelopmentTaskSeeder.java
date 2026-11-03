package com.example.backend.developmenttask;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DevelopmentTaskSeeder implements CommandLineRunner {
	private static final Logger log = LoggerFactory.getLogger(DevelopmentTaskSeeder.class);
	private final DevelopmentTaskRepository repository;

	public DevelopmentTaskSeeder(DevelopmentTaskRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public void run(String... args) {
		if (repository.count() != 0) {
			return;
		}
		repository.saveAll(List.of(
				new DevelopmentTask("DT-001", "取引検索の改善", "進行中", LocalDate.of(2026, 10, 30),
						"取引を効率よく探せるようにする。",
						"検索条件を一覧画面に表示する。",
						"検索条件と一覧項目を定義する。",
						"一覧画面と検索APIを実装する。",
						"検索条件の確認\n画面の動作確認"),
				new DevelopmentTask("DT-002", "申請画面の見直し", "未着手", LocalDate.of(2026, 10, 28),
						"申請画面を利用しやすくする。", null, null, null, "表示項目の検討")));
		log.info("開発タスクのサンプルデータを作成しました");
	}
}
