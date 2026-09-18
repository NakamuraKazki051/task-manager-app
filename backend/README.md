# task-manager-api

タスク管理アプリ用のSpring Boot REST API（既存の `index.html`/`app.js`（localStorage版）とは独立したバックエンドで、既存のフロントエンドとは接続していません）。

## 技術構成

- Java 17 / Spring Boot 4.1.1
- Spring Web, Spring Data JPA, PostgreSQL（本番/開発用途、Dockerコンテナで起動）
- テスト実行時はH2インメモリDBを使用（`src/test/resources/application.yml`）し、Dockerなしでも `mvn test` が通る

## 起動方法

事前にリポジトリ直下の `docker-compose.yml` でDBコンテナを起動しておく必要があります。

```
docker compose up -d   # リポジトリ直下で実行、taskmanagerデータベースを起動（localhost:5432）
cd backend
mvn package
java -Xmx256m -Xss512k -XX:MaxMetaspaceSize=128m -jar target/task-manager-api-0.1.0.jar
```

- API: http://localhost:8080/api/...
- DB接続先: `jdbc:postgresql://localhost:5432/taskmanager`（ユーザー/パスワード: `taskmanager`、`../docker-compose.yml` 参照）

このマシンはメモリが少ないため、`mvn spring-boot:run` を常駐させず、`mvn package` でビルドしたjarをヒープ制限付きで起動することを推奨します。

## エンドポイント

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/boards` | ボード一覧 |
| POST | `/api/boards` | ボード作成 `{name}` |
| PUT | `/api/boards/{id}` | ボード名変更 `{name}` |
| DELETE | `/api/boards/{id}` | ボード削除（最後の1件は不可） |
| GET | `/api/boards/{boardId}/columns` | 列一覧 |
| POST | `/api/boards/{boardId}/columns` | 列作成 `{name, done}` |
| PUT | `/api/columns/{id}` | 列更新 `{name?, done?}` |
| DELETE | `/api/columns/{id}` | 列削除（タスクが残っている列・最後の1列は不可） |
| GET | `/api/boards/{boardId}/tasks?category=&priority=&q=&sort=manual\|due\|priority` | タスク一覧（フィルタ・検索・並び替え） |
| POST | `/api/boards/{boardId}/tasks` | タスク作成 |
| GET | `/api/tasks/{id}` | タスク取得 |
| PUT | `/api/tasks/{id}` | タスク更新（チェックリスト・カテゴリ含む全体更新） |
| PATCH | `/api/tasks/{id}/move` | 列移動・並び順変更 `{columnId, displayOrder}` |
| DELETE | `/api/tasks/{id}` | タスク削除 |

タスクのリクエスト/レスポンス例:

```json
{
  "title": "デザインカンプ確認",
  "description": "クライアントに提出する最終版を確認する",
  "columnId": "<column-id>",
  "dueDate": "2026-09-19",
  "priority": "MID",
  "categories": ["仕事", "デザイン"],
  "checklist": [
    { "text": "ラフ案を共有", "done": true },
    { "text": "最終確認", "done": false }
  ]
}
```
