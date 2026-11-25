package com.ecoportal;

import com.ecoportal.domain.Event;
import com.ecoportal.domain.EventStatus;
import com.ecoportal.domain.Initiative;
import com.ecoportal.repository.EventRepository;
import com.ecoportal.repository.InitiativeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Інтеграційні тести на реальній PostgreSQL (без H2). Потрібна порожня БД ecoportal:
 * схему та демонстраційні дані створюють schema.sql і data.sql при старті контексту.
 * Кожен тест виконується в транзакції, яка відкочується, тому БД після тестів не змінюється.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EcoPortalIntegrationTest {

    private static final String PASSWORD = "Password123";
    private static final String COORDINATOR_1 = "coordinator1@ecoportal.test";
    private static final String COORDINATOR_2 = "coordinator2@ecoportal.test";
    private static final String PARTICIPANT_1 = "participant1@ecoportal.test";
    private static final String PARTICIPANT_2 = "participant2@ecoportal.test";
    private static final String INITIATIVE_ODESA = "10000000-0000-4000-8000-000000000001";
    private static final String EVENT_LANZHERON = "20000000-0000-4000-8000-000000000001";
    private static final String EVENT_NATALKA = "20000000-0000-4000-8000-000000000003";
    private static final String EVENT_DRAFT = "20000000-0000-4000-8000-000000000004";
    private static final String APPLICATION_OF_PARTICIPANT_2 = "30000000-0000-4000-8000-000000000004";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private InitiativeRepository initiativeRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Дати подій у data.sql задаються відносно моменту першого запуску. Щоб тести не залежали від
     * того, коли заповнили БД, оновлюємо їх у межах тестової транзакції (вона відкочується).
     */
    @BeforeEach
    void refreshSeedEventDates() {
        jdbcTemplate.update("""
                UPDATE events
                SET starts_at = now() + interval '7 days',
                    ends_at = now() + interval '7 days 3 hours',
                    registration_closes_at = now() + interval '6 days'
                WHERE id IN ('20000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000002',
                             '20000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000004')
                """);
    }

    // ------------------------------------------------------------------ тести

    @Test
    void healthAndPublicCatalog() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mvc.perform(get("/api/events").param("city", "ОДЕСА"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].city").value("Одеса"));

        // 30 місць, 2 активні заявки (PENDING + APPROVED)
        mvc.perform(get("/api/events/" + EVENT_LANZHERON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeSpots").value(28));

        mvc.perform(get("/api/events/" + EVENT_DRAFT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/events").param("dateFrom", "2030-02-01").param("dateTo", "2030-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void authRegistrationLoginAndSessions() throws Exception {
        String email = uniqueEmail();
        Map<String, Object> registration = Map.of(
                "fullName", "Новий Учасник", "email", email, "password", PASSWORD, "role", "COORDINATOR");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(registration)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PARTICIPANT"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(registration)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "X", "email", "не-email", "password", "1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        MockHttpSession session = login(email);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isNoContent());
    }

    @Test
    void participantApplicationLifecycle() throws Exception {
        MockHttpSession participant1 = login(PARTICIPANT_1);

        // Дані з data.sql: заявка на подію вже існує (PENDING)
        mvc.perform(post("/api/events/" + EVENT_LANZHERON + "/applications").session(participant1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_ALREADY_EXISTS"));

        // Скасована заявка відновлюється: 200 OK, статус знову PENDING
        mvc.perform(post("/api/events/" + EVENT_NATALKA + "/applications").session(participant1)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("comment", "Повертаюсь!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.comment").value("Повертаюсь!"));

        mvc.perform(post("/api/events/" + EVENT_NATALKA + "/applications").session(participant1)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("comment", "x".repeat(501)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.comment").exists());

        mvc.perform(get("/api/my/applications").session(participant1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // Нова заявка створюється зі статусом 201, а чужу заявку скасувати не можна
        MockHttpSession newParticipant = newParticipant();
        MvcResult created = mvc.perform(post("/api/events/" + EVENT_LANZHERON + "/applications").session(newParticipant))
                .andExpect(status().isCreated())
                .andReturn();
        String ownApplicationId = read(created, "$.id");
        mvc.perform(post("/api/applications/" + APPLICATION_OF_PARTICIPANT_2 + "/cancel").session(newParticipant))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/applications/" + UUID.randomUUID() + "/cancel").session(newParticipant))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/applications/" + ownApplicationId + "/cancel").session(newParticipant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/applications/" + ownApplicationId + "/cancel").session(newParticipant))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCELLATION_NOT_ALLOWED"));
    }

    @Test
    void coordinatorManagesEventAndApplications() throws Exception {
        MockHttpSession coordinator = login(COORDINATOR_1);

        MvcResult initiative = mvc.perform(post("/api/coordinator/initiatives").session(coordinator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Тестова ініціатива", "description", "Опис", "category", "Озеленення"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andReturn();
        String initiativeId = read(initiative, "$.id");

        mvc.perform(post("/api/coordinator/events").session(coordinator).contentType(MediaType.APPLICATION_JSON)
                        .content(json(eventBody(initiativeId, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.participantsLimit").exists());

        MvcResult created = mvc.perform(post("/api/coordinator/events").session(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(eventBody(initiativeId, 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        String eventId = read(created, "$.id");

        mvc.perform(get("/api/events/" + eventId)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/coordinator/events/" + eventId + "/status").session(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "PUBLISHED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeSpots").value(2));

        // Ліміт 2: два учасники подають заявки, третій отримує 409
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/events/" + eventId + "/applications").session(newParticipant()))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/events/" + eventId + "/applications").session(newParticipant()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_FREE_SPOTS"));

        // Ліміт не може бути меншим за кількість активних заявок
        mvc.perform(put("/api/coordinator/events/" + eventId).session(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(eventBody(initiativeId, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIMIT_BELOW_ACTIVE_APPLICATIONS"));

        MvcResult applications = mvc.perform(get("/api/coordinator/events/" + eventId + "/applications")
                        .session(coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].applicantEmail").exists())
                .andReturn();
        String applicationId = read(applications, "$[0].id");

        // Матриця переходів: PENDING -> APPROVED -> REJECTED, далі з REJECTED переходів немає
        decide(coordinator, applicationId, "APPROVED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        decide(coordinator, applicationId, "REJECTED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        decide(coordinator, applicationId, "APPROVED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_APPLICATION_TRANSITION"));
        decide(coordinator, applicationId, "CANCELLED").andExpect(status().isBadRequest());

        // Одна активна заявка, тож ліміт 1 тепер допустимий
        mvc.perform(put("/api/coordinator/events/" + eventId).session(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(eventBody(initiativeId, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantsLimit").value(1));

        // Чужий координатор і учасник не мають доступу
        MockHttpSession otherCoordinator = login(COORDINATOR_2);
        mvc.perform(patch("/api/coordinator/events/" + eventId + "/status").session(otherCoordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "CANCELLED"))))
                .andExpect(status().isForbidden());
        decide(otherCoordinator, applicationId, "APPROVED").andExpect(status().isForbidden());
        mvc.perform(get("/api/coordinator/initiatives").session(newParticipant()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/coordinator/initiatives")).andExpect(status().isUnauthorized());

        // Скасування події не змінює статуси заявок, але подія зникає з каталогу
        mvc.perform(patch("/api/coordinator/events/" + eventId + "/status").session(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "CANCELLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/coordinator/events/" + eventId + "/applications").session(coordinator))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[1].status").value("PENDING"));
        mvc.perform(get("/api/events/" + eventId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/coordinator/initiatives").session(coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].events").exists());
    }

    @Test
    void eventSummaryAndPeriodReport() throws Exception {
        MockHttpSession coordinator = login(COORDINATOR_1);

        // Завершена подія створюється напряму в БД: через API початок у минулому заборонений
        Initiative initiative = initiativeRepository.findById(UUID.fromString(INITIATIVE_ODESA)).orElseThrow();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Event finished = new Event();
        finished.setInitiative(initiative);
        finished.setTitle("Завершена толока");
        finished.setDescription("Подія для перевірки підсумків");
        finished.setCity("Одеса");
        finished.setAddressText("пляж Ланжерон");
        finished.setStartsAt(now.minus(3, ChronoUnit.DAYS));
        finished.setEndsAt(now.minus(2, ChronoUnit.DAYS));
        finished.setRegistrationClosesAt(now.minus(4, ChronoUnit.DAYS));
        finished.setParticipantsLimit(10);
        finished.setStatus(EventStatus.PUBLISHED);
        String finishedId = eventRepository.saveAndFlush(finished).getId().toString();

        // Подія ще не завершилась: 409
        mvc.perform(put("/api/coordinator/events/" + EVENT_LANZHERON + "/summary").session(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(summaryBody(12, "35.5", 0, "40"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FINISHED"));

        mvc.perform(put("/api/coordinator/events/" + finishedId + "/summary").session(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(summaryBody(12, "35.5", -1, "40"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.treesPlanted").exists());

        mvc.perform(put("/api/coordinator/events/" + finishedId + "/summary").session(login(COORDINATOR_2))
                        .contentType(MediaType.APPLICATION_JSON).content(json(summaryBody(12, "35.5", 0, "40"))))
                .andExpect(status().isForbidden());

        // Повторний виклик оновлює наявний запис
        mvc.perform(put("/api/coordinator/events/" + finishedId + "/summary").session(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(summaryBody(12, "35.5", 0, "40"))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/coordinator/events/" + finishedId + "/summary").session(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(summaryBody(15, "40.25", 25, "42.5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasteKg").value(40.25))
                .andExpect(jsonPath("$.actualParticipants").value(15));

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Kyiv"));
        mvc.perform(get("/api/coordinator/reports").session(coordinator)
                        .param("from", today.minusDays(7).toString()).param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syntheticData").value(true))
                .andExpect(jsonPath("$.cities", hasSize(1)))
                .andExpect(jsonPath("$.cities[0].city").value("Одеса"))
                .andExpect(jsonPath("$.cities[0].eventsCount").value(1))
                .andExpect(jsonPath("$.totals.totalParticipants").value(15))
                .andExpect(jsonPath("$.totals.totalTreesPlanted").value(25))
                .andExpect(jsonPath("$.totals.totalWasteKg").value(40.25));

        mvc.perform(get("/api/coordinator/reports").session(coordinator)
                        .param("from", today.toString()).param("to", today.minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    // ------------------------------------------------------------------ допоміжні методи

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession newParticipant() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Тестовий Учасник", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
        return login(email);
    }

    private ResultActions decide(MockHttpSession session, String applicationId, String decision) throws Exception {
        MockHttpServletRequestBuilder request = patch("/api/coordinator/applications/" + applicationId)
                .session(session).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("decision", decision)));
        return mvc.perform(request);
    }

    private Map<String, Object> eventBody(String initiativeId, int limit) {
        Instant startsAt = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("initiativeId", initiativeId);
        body.put("title", "Тестова подія");
        body.put("description", "Опис тестової події");
        body.put("city", "Одеса");
        body.put("addressText", "вул. Приморська, 1");
        body.put("startsAt", startsAt.toString());
        body.put("endsAt", startsAt.plus(3, ChronoUnit.HOURS).toString());
        body.put("registrationClosesAt", startsAt.minus(1, ChronoUnit.HOURS).toString());
        body.put("participantsLimit", limit);
        return body;
    }

    private Map<String, Object> summaryBody(int participants, String wasteKg, int trees, String hours) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actualParticipants", participants);
        body.put("wasteKg", new java.math.BigDecimal(wasteKg));
        body.put("treesPlanted", trees);
        body.put("hoursSpent", new java.math.BigDecimal(hours));
        body.put("note", "Тестовий підсумок");
        return body;
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }
}
