# Екологічний інформаційний портал із заявками (`EcoPortal`)

Курсова робота з дисципліни «Веб-технології та веб-дизайн» (НУ «Одеська політехніка», спеціальність 122 «Комп’ютерні науки»).

Вебсистема реалізована як **модульний моноліт** на базі **Java**, **Spring Boot**, **PostgreSQL** та клієнтського інтерфейсу на **HTML5, CSS3 і TypeScript** (без Docker і сторонніх хмарних сервісів).

---

## Стек технологій

- **Серверна частина:** Java 21, Spring Boot 3.4 (Spring Web, Spring Data JPA, Spring Security, Bean Validation)
- **База даних:** PostgreSQL (ініціалізація через `schema.sql` та `data.sql`, `ddl-auto=validate`)
- **Клієнтська частина:** HTML5, CSS3, TypeScript (компіляція у статичні ресурси в `src/main/resources/static/`)
- **Тестування:** JUnit 5, Spring Boot Test (`MockMvc`) на реальній базі PostgreSQL

---

## Попередні вимоги

- **JDK** (встановлений у системі; перевірити можна через `java -version`)
- **Apache Maven** (`mvn` або Maven Wrapper `./mvnw`)
- **PostgreSQL** (запущений локальний сервер БД)
- **Node.js** (потрібен лише за умови перекомпіляції TypeScript-файлів клієнта; готовий JS уже лежить у `static/`)

---

## Покроковий запуск проєкту

### 1. Підготовка бази даних PostgreSQL

За замовчуванням у `application.properties` використовуються параметри:
- База даних (`DB_NAME`): `ecoportal`
- Користувач (`DB_USER`): `ecoportal`
- Пароль (`DB_PASSWORD`): `ecoportal`
- Хост і порт: `localhost:5432`

Створити користувача й базу даних через `psql` (у прикладі підключення виконується під локальним суперкористувачем `olgasevel` до бази `postgres`):

```bash
psql -h 127.0.0.1 -U olgasevel -d postgres -c "CREATE USER ecoportal WITH PASSWORD 'ecoportal';"
psql -h 127.0.0.1 -U olgasevel -d postgres -c "CREATE DATABASE ecoportal OWNER ecoportal;"
```

> Якщо потрібно пересоздати базу з нуля (наприклад, для чистої перевірки тестів або демонстрації):
> ```bash
> psql -h 127.0.0.1 -U olgasevel -d postgres -c "DROP DATABASE IF EXISTS ecoportal WITH (FORCE);"
> psql -h 127.0.0.1 -U olgasevel -d postgres -c "CREATE DATABASE ecoportal OWNER ecoportal;"
> ```
> Під час старту застосунку або тестів Spring Boot автоматично виконає `schema.sql` та ідемпотентний `data.sql` (`ON CONFLICT DO NOTHING`).

### 2. Налаштування змінних оточення (за потреби)

За потреби можна перевизначити змінні оточення перед запуском:

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=ecoportal
export DB_USER=ecoportal
export DB_PASSWORD=ecoportal
export SESSION_COOKIE_SECURE=false
```

### 3. Запуск інтеграційних тестів

У каталозі бекенду (`step1-backend-core`):

```bash
mvn test
```

*(або через `./mvnw test`, якщо в проєкті налаштовано Maven Wrapper).*

### 4. Запуск вебзастосунку

```bash
mvn spring-boot:run
```

Після запуску інтерфейс та REST API доступні за адресою:
- Вебінтерфейс: `http://localhost:8080`
- Перевірка стану: `http://localhost:8080/api/health`

---

## Тестові облікові записи (з `data.sql`)

Пароль для всіх демонстраційних акаунтів: `Password123`

| Роль | Ім’я | Email | Пароль |
| :--- | :--- | :--- | :--- |
| Координатор | Олена Коваленко | `coordinator1@ecoportal.test` | `Password123` |
| Координатор | Андрій Шевченко | `coordinator2@ecoportal.test` | `Password123` |
| Учасник | Марія Бондаренко | `participant1@ecoportal.test` | `Password123` |
| Учасник | Ігор Мельник | `participant2@ecoportal.test` | `Password123` |

---

## Примітка щодо тесту `participantApplicationLifecycle`

Оскільки скрипт `data.sql` використовує `ON CONFLICT DO NOTHING`, повторний запуск тестів на вже заповненій базі може зіткнутися зі станом заявок від попереднього прогону (наприклад, статусом `REJECTED` або активною/скасованою заявкою). Перед чистим прогоном `mvn test` рекомендується перестворити базу `ecoportal` за інструкцією з кроку 1.