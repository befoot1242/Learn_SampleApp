# Vue 3 datepicker ライブラリの年/月単位選択サポート調査

調査日: 2026-11-01
調査範囲: Vue 3 向け主要datepickerライブラリの公式ドキュメント・公式ソースコード（GitHub）
調査目的: 「年のみ」「月のみ」選択を公式サポートするか、また1つのコンポーネントで年/月/日の精度を切り替えられるかの確認

## 結論（要約）

- **「年のみ」「月のみ」の値を返す選択モード**を公式サポートするライブラリは複数ある（Element Plus, PrimeVue, @vuepic/vue-datepicker, vue-datepicker-next, Ant Design Vue, Naive UI）。ただしいずれも**マウント時にprop/typeで固定するモード**であり、「ユーザー操作で実行時に年/月/日モードを切り替えるUI」を標準搭載しているわけではない（propを自分でreactiveに変更すれば切り替え自体は可能）。
- **1つのコンポーネントインスタンスがヘッダー操作だけで年ビュー/月ビュー/日ビューを行き来できる**（ナビゲーションとしての精度切替）のは **Vuetify (`v-date-picker`)** と **Quasar (`QDate`)**。ただしこの2つは、どのビューで操作しても最終的に**確定される値は常に「日付（日単位）」**であり、「年だけ」「月だけ」を値として確定する公式モードではない。
- よって「年/月/日の“値の精度”を1コンポーネントで切替できる」公式サポートは、今回調査した範囲では確認できなかった。年/月単位の値を確定するには専用のprop（`type="year"`など）で固定し、ビュー遷移（ナビゲーション）の精度切替とは別物として扱う必要がある。

## ライブラリ別詳細

### 1. Element Plus（`el-date-picker`）
- 公式: https://element-plus.org/en-US/component/date-picker.html
- `type` prop で選択粒度を切り替え: `year` / `month` / `date` / `week` / `quarter` / 複数選択版(`years`/`months`/`dates`/`quarters`) / 範囲版(`yearrange`/`monthrange`/`daterange`/`quarterrange`)
- `yearrange` は v2.8.0、`quarterrange` はv2.14.5で追加（ドキュメント内に明記）。
- `type`はコンポーネントのモードを決めるpropであり、実行時にUI操作だけで年⇔月⇔日を切り替える仕組みはない（`type`をreactiveに変更すれば挙動は変わる）。

### 2. PrimeVue（`DatePicker`、旧 `Calendar`）
- 公式: https://primevue.dev/datepicker/
- `view` prop: `date`（既定）/ `month` / `year`
  - "Month Picker: Month only picker is enabled by specifying `view` as `month`"
  - "Year Picker: Specifying `view` as `year` ... enables the year picker"
- `date`モードでもヘッダーの年/月ラベルをクリックすると月選択・年選択のパネルに遷移するナビゲーションはあるが、これは「日付を選ぶための補助ナビゲーション」であり、`view`自体をユーザー操作で永続的に切り替えるものではない。

### 3. @vuepic/vue-datepicker（`VueDatePicker`、vue3datepicker.com）
- 公式: https://vue3datepicker.com/ 、GitHub: https://github.com/Vuepic/vue-datepicker
- ソース: `packages/docs/docs/props/modes/index.md`（Vuepic/vue-datepicker, commit 7ea6253）
- boolean propとして `month-picker` / `year-picker` / `quarter-picker` / `week-picker` / `time-picker` を用意。
  - `month-picker`: 「Change datepicker mode to select only month and year」、`v-model`は`{ month, year }`形式
  - `year-picker`: 「Change datepicker mode to select only year」、`v-model`は年の数値
- いずれもモードは排他的なbooleanフラグであり、1つの`VueDatePicker`インスタンス内でUI操作により年/月/日モードを行き来する公式UIはない。

### 4. vue-datepicker-next（`mengxiong10/vue-datepicker-next`）
- 公式README: https://github.com/mengxiong10/vue-datepicker-next/blob/main/README.md
- `type` prop: `date | datetime | year | month | time | week`（既定 `date`）
- `type="year"`・`type="month"`で年単位・月単位の選択が可能。`type`はpropで固定するモードであり、動的な精度切替UIはドキュメント上確認できない。

### 5. Ant Design Vue（`a-date-picker`）
- 公式: https://github.com/vueComponent/ant-design-vue （`components/date-picker/index.zh-CN.md`）
- `picker` prop: `date | week | month | quarter | year`（既定 `date`）— 最終的に確定する値の精度を決める。
- 別途 `mode` prop: `time | date | month | year | decade` ＋ `panelChange` イベントがあり、パネル（表示中のビュー）がヘッダー操作で `date → month → year → decade` と遷移できる。ただし、これは「日付を選ぶためのナビゲーション」であり、`picker`で固定された最終確定値の粒度自体を変えるものではない（例: `picker="date"`のままヘッダーで年ビューに遷移しても、最終的に選択できるのは依然として「日」）。

### 6. Naive UI（`n-date-picker`）
- 公式: `tusen-ai/naive-ui` リポジトリ内 `src/date-picker/demos/enUS/index.demo-entry.md`
- `type` prop: `date | datetime | daterange | datetimerange | month | monthrange | year | yearrange | quarter | quarterrange | week`（既定 `date`）
- `type="year"` / `type="month"` で年単位・月単位選択が可能。`monthrange`はv2.28.3、`quarter`はv2.22.0で追加（CHANGELOGに明記）。
- `type`は固定モードであり、1インスタンス内でのランタイム切替UIは確認できない。

### 7. Vuetify（`v-date-picker`）※年/月/日の「ビュー」を切替できる数少ない例
- 公式: https://vuetifyjs.com/en/components/date-pickers/
- ソース: `packages/vuetify/src/components/VDatePicker/VDatePicker.tsx`（vuetifyjs/vuetify, commit f28878b）
- 内部に `viewMode`（`useProxiedModel(props, 'viewMode')`、`update:viewMode`としてv-model可能）があり、値は `'month'`（日グリッド、既定）/ `'months'`（月グリッド）/ `'year'`（年グリッド）。
- ヘッダーの「月」ラベルをクリックすると `onClickMonth()` が `viewMode` を `'month' ⇔ 'months'` に切り替え、「年」ラベルをクリックすると `onClickYear()` が `'month' ⇔ 'year'` に切り替える実装（同ファイル内 `onClickMonth`/`onClickYear`関数）。
- つまり **1つの`v-date-picker`コンポーネントインスタンスだけで、日グリッド・月グリッド・年グリッドの3つのビューをUI操作（ヘッダークリック）で行き来できる**。
- ただし、月グリッドで月を選ぶと `onUpdateMonth()` が呼ばれて `viewMode` が `'month'`（日グリッド）に戻り、年グリッドで年を選んでも同様に日グリッドに戻る設計になっており、**最終的に`v-model`へ確定（emit）される値は常に日付（Dateオブジェクト、日単位）**。年だけ・月だけを値として確定する専用モードではない。

### 8. Quasar（`QDate`）※Vuetifyと同種の「ビュー」切替例
- 公式: https://v2.quasar.dev/vue-components/date （QDate API）
- ソース: `ui/src/components/date/QDate.js`（quasarframework/quasar, commit 231c2b5）、`ui/src/components/date/QDate.json`
- `default-view` prop: `'Calendar'`（既定）/ `'Months'` / `'Years'` — 開くときに表示する初期ビューを指定。
- ソース内 `setYear()` 等の実装で、年を選ぶと `view.value` が `'Months'` または `'Calendar'` に遷移する処理があり、ヘッダー操作でCalendar⇔Months⇔Yearsビューを行き来できる。
- 公式ドキュメントの`model-value`説明より、`QDate`の`v-model`は常に`mask`（既定`'YYYY/MM/DD'`）に従った**日付文字列（日単位）**であり、Vuetify同様「ビューの切替」はナビゲーションに過ぎず、年のみ・月のみを値として確定するモードはない。

## まとめ表

| ライブラリ | 年のみ選択 | 月のみ選択 | 1コンポーネントでビュー(年/月/日)を切替 | 備考（出典） |
|---|---|---|---|---|
| Element Plus | ○ (`type="year"`) | ○ (`type="month"`) | ×（typeは固定prop） | element-plus.org |
| PrimeVue DatePicker | ○ (`view="year"`) | ○ (`view="month"`) | ×（viewは固定prop） | primevue.dev |
| @vuepic/vue-datepicker | ○ (`year-picker`) | ○ (`month-picker`) | ×（boolean propで固定） | vue3datepicker.com / GitHub Vuepic |
| vue-datepicker-next | ○ (`type="year"`) | ○ (`type="month"`) | ×（typeは固定prop） | GitHub mengxiong10 |
| Ant Design Vue | ○ (`picker="year"`) | ○ (`picker="month"`) | △（`mode`+`panelChange`でパネル遷移は可能だが確定粒度は`picker`固定） | GitHub vueComponent/ant-design-vue |
| Naive UI | ○ (`type="year"`) | ○ (`type="month"`) | ×（typeは固定prop） | GitHub tusen-ai/naive-ui |
| Vuetify v-date-picker | ×（値としては常に日付） | ×（値としては常に日付） | ○（`viewMode`をヘッダークリックで `month`/`months`/`year`に切替、ただし確定値は常に日付） | GitHub vuetifyjs/vuetify |
| Quasar QDate | ×（値としては常に日付） | ×（値としては常に日付） | ○（`default-view`/ヘッダー操作で`Calendar`/`Months`/`Years`を切替、ただし確定値は常に日付） | v2.quasar.dev |

## 注意点・限界

- 本調査は各ライブラリの公式ドキュメントおよび公式GitHubリポジトリのソース/ドキュメントファイルのみを根拠にしている。実機での挙動確認（ブラウザでの実行）は行っていない。
- 「年/月/日の精度を1コンポーネントで切替できるか」という問いには2つの意味があり得る：
  1. **確定する値の精度**（年だけ/月だけ/日だけの値をv-modelとして返すか）→ 今回調査した範囲ではランタイムに切替可能な公式UIは確認できなかった（propを固定するモデルが主流）。
  2. **表示ビューの精度**（年グリッド/月グリッド/日グリッドのどれを表示するか、ナビゲーションとして）→ Vuetify, Quasarで標準サポートを確認。
- 本ドキュメントはアプリケーションコードを変更せず、調査結果のみを記録したものである。
