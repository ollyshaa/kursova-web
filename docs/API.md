# Опис API

Базовий URL: `http://localhost:8080`. Формат даних: JSON (UTF-8), дати й час: ISO-8601 (`2026-12-01T10:00:00Z`),
дати фільтрів: `YYYY-MM-DD`. Автентифікація: cookie-сесія (`JSESSIONID`), яку встановлює `POST /api/auth/login`.

Усі помилки мають вигляд `{status, code, message, fieldErrors}` (див. README).

## Службові

| Метод | Шлях          | Доступ | Опис                                  |
|-------|---------------|--------|---------------------------------------|
| GET   | `/api/health` | всі    | Перевірка застосунку та `SELECT 1` у БД |

## Автентифікація

| Метод | Шлях                 | Доступ        | Відповідь | Опис |
|-------|----------------------|---------------|-----------|------|
| POST  | `/api/auth/register` | всі           | 201       | Реєстрація. Завжди створює `PARTICIPANT` |
| POST  | `/api/auth/login`    | всі           | 200       | Вхід, змінює ідентифікатор сесії |
| POST  | `/api/auth/logout`   | автентифіковані | 204     | Вихід |
| GET   | `/api/auth/me`       | автентифіковані | 200     | Поточний користувач |

Реєстрація: `{"fullName": "Марія", "email": "maria@example.com", "password": "Password123"}`
(пароль 8–72 символи). Помилки: `409 EMAIL_ALREADY_EXISTS`, `400 VALIDATION_ERROR`.
Вхід: `{"email": "...", "password": "..."}`; помилка: `401 INVALID_CREDENTIALS`.

## Каталог (публічний)

| Метод | Шлях               | Опис |
|-------|--------------------|------|
| GET   | `/api/events`      | Опубліковані події. Фільтри: `city` (без урахування регістру), `dateFrom`, `dateTo` (включно). `400 INVALID_DATE_RANGE`, якщо `dateFrom > dateTo` |
| GET   | `/api/events/{id}` | Подія. `404`, якщо вона чернетка, скасована або з неопублікованої ініціативи |

У відповіді є `freeSpots` та коротка інформація про ініціативу.

## Учасник (роль `PARTICIPANT`)

| Метод | Шлях                            | Відповідь | Опис |
|-------|---------------------------------|-----------|------|
| POST  | `/api/events/{id}/applications` | 201 / 200 | Подати заявку (тіло необов'язкове: `{"comment": "до 500 символів"}`). `200` — відновлено скасовану заявку |
| GET   | `/api/my/applications`          | 200       | Власні заявки, найновіші першими, з полем `canCancel` |
| POST  | `/api/applications/{id}/cancel` | 200       | Скасувати власну заявку (`PENDING` або `APPROVED` до початку події) |

Помилки подання (`409`): `REGISTRATION_CLOSED`, `APPLICATION_ALREADY_EXISTS`, `NO_FREE_SPOTS`.
Скасування: `404`, `403` (чужа заявка), `409 CANCELLATION_NOT_ALLOWED`.

## Координатор (роль `COORDINATOR`)

Для інших ролей усі ендпоінти повертають `403`; чужі об'єкти — `403`, неіснуючі — `404`.

| Метод | Шлях                                       | Відповідь | Опис |
|-------|--------------------------------------------|-----------|------|
| GET   | `/api/coordinator/initiatives`             | 200       | Власні ініціативи з подіями та кількістю активних заявок |
| POST  | `/api/coordinator/initiatives`             | 201       | Створити ініціативу |
| POST  | `/api/coordinator/events`                  | 201       | Створити подію (статус `DRAFT`) |
| PUT   | `/api/coordinator/events/{id}`             | 200       | Оновити подію |
| PATCH | `/api/coordinator/events/{id}/status`      | 200       | Опублікувати (`PUBLISHED`) або скасувати (`CANCELLED`) |
| GET   | `/api/coordinator/events/{id}/applications`| 200       | Заявки на подію із заявниками |
| PATCH | `/api/coordinator/applications/{id}`       | 200       | Рішення: `{"decision": "APPROVED"}` або `"REJECTED"` |
| PUT   | `/api/coordinator/events/{id}/summary`     | 200       | Внести або оновити підсумки завершеної події |
| GET   | `/api/coordinator/reports?from=&to=`       | 200       | Звіт за період по населених пунктах |

### Ініціатива

```json
{ "title": "Зелений двір", "description": "Опис", "category": "Озеленення", "status": "PUBLISHED" }
```

`status` необов'язковий: за замовчуванням `PUBLISHED` (подія стає видимою в каталозі лише тоді, коли
опубліковані і подія, і її ініціатива).

### Подія (створення та оновлення)

```json
{
  "initiativeId": "uuid (лише при створенні)",
  "title": "Прибирання парку",
  "description": "Опис",
  "city": "Одеса",
  "addressText": "вул. Приморська, 1",
  "latitude": 46.48,
  "longitude": 30.74,
  "startsAt": "2026-12-10T08:00:00Z",
  "endsAt": "2026-12-10T12:00:00Z",
  "registrationClosesAt": "2026-12-09T18:00:00Z",
  "participantsLimit": 30
}
```

Перевірки (`400 VALIDATION_ERROR` з `fieldErrors`): `startsAt` у майбутньому (під час оновлення — лише якщо дату
змінюють), `endsAt > startsAt`, `registrationClosesAt <= startsAt`, `participantsLimit > 0`, коректні координати.
Оновлення: `409 LIMIT_BELOW_ACTIVE_APPLICATIONS`, якщо новий ліміт менший за кількість активних заявок.
Зміна статусу: скасовану подію змінити не можна (`409 INVALID_STATUS_TRANSITION`); статуси заявок не змінюються.

### Рішення за заявкою

Дозволені переходи: `PENDING → APPROVED`, `PENDING → REJECTED`, `APPROVED → REJECTED` (до початку події).
Інші: `409 INVALID_APPLICATION_TRANSITION`. Записуються `decidedBy` та `decidedAt`.

### Підсумки події

```json
{ "actualParticipants": 15, "wasteKg": 40.25, "treesPlanted": 25, "hoursSpent": 42.5, "note": "Примітка" }
```

Усі числа ≥ 0. До завершення події: `409 EVENT_NOT_FINISHED`; для неопублікованої: `409 EVENT_NOT_PUBLISHED`.

### Звіт

`GET /api/coordinator/reports?from=2026-01-01&to=2026-12-31`

```json
{
  "from": "2026-01-01",
  "to": "2026-12-31",
  "cities": [
    { "city": "Одеса", "eventsCount": 1, "totalParticipants": 15,
      "totalWasteKg": 40.25, "totalTreesPlanted": 25, "totalHoursSpent": 42.50 }
  ],
  "totals": { "eventsCount": 1, "totalParticipants": 15,
              "totalWasteKg": 40.25, "totalTreesPlanted": 25, "totalHoursSpent": 42.50 },
  "syntheticData": true
}
```

До звіту потрапляють опубліковані події координатора з внесеними підсумками, що завершилися в межах періоду.
`from > to` — `400 INVALID_DATE_RANGE`.
