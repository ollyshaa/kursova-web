"use strict";
class ApiError extends Error {
    constructor(status, code, message, fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
    }
}
const TZ = "Europe/Kyiv";
const STATUS_UK = {
    PENDING: "Очікує розгляду", APPROVED: "Схвалено", REJECTED: "Відхилено", CANCELLED: "Скасовано",
    DRAFT: "Чернетка", PUBLISHED: "Опубліковано",
};
const ERROR_UK = {
    INVALID_CREDENTIALS: "Невірна електронна пошта або пароль.",
    EMAIL_ALREADY_EXISTS: "Користувач із такою поштою вже існує.",
    VALIDATION_ERROR: "Перевірте правильність заповнення полів.",
    INVALID_DATE_RANGE: "Дата «від» не може бути пізнішою за дату «до».",
    REGISTRATION_CLOSED: "Реєстрацію на подію закрито.",
    APPLICATION_ALREADY_EXISTS: "Ви вже подали заявку на цю подію.",
    NO_FREE_SPOTS: "Вільних місць більше немає.",
    CANCELLATION_NOT_ALLOWED: "Цю заявку вже не можна скасувати.",
    LIMIT_BELOW_ACTIVE_APPLICATIONS: "Новий ліміт менший за кількість активних заявок.",
    INVALID_STATUS_TRANSITION: "Скасовану подію змінити неможливо.",
    INVALID_APPLICATION_TRANSITION: "Такий перехід статусу заявки недозволений.",
    EVENT_NOT_FINISHED: "Підсумки можна внести лише після завершення події.",
    EVENT_NOT_PUBLISHED: "Підсумки можна внести лише для опублікованої події.",
};
let currentUser = null;
const catalogFilters = { city: "", dateFrom: "", dateTo: "" };
let coordTab = 0;
function h(tag, props = {}, ...kids) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
        if (v === undefined || v === null || v === false)
            continue;
        if (k === "class")
            el.className = String(v);
        else if (k.startsWith("on") && typeof v === "function")
            el.addEventListener(k.slice(2), v);
        else if (k === "value" || k === "checked" || k === "disabled")
            el[k] = v;
        else
            el.setAttribute(k, v === true ? "" : String(v));
    }
    for (const c of kids)
        if (c !== null && c !== undefined && c !== false)
            el.append(c instanceof Node ? c : document.createTextNode(String(c)));
    return el;
}
const $ = (sel, root = document) => root.querySelector(sel);
const mainEl = () => document.getElementById("main");
function notify(msg, kind = "ok") {
    const box = document.getElementById("notices");
    const n = h("p", { class: "notice" + (kind === "error" ? " error" : "") }, msg);
    box.replaceChildren(n);
    window.setTimeout(() => { if (n.parentNode === box)
        n.remove(); }, 8000);
}
function clearNotices() { document.getElementById("notices").replaceChildren(); }
function errText(e) {
    if (e instanceof ApiError)
        return ERROR_UK[e.code] || e.message || "Сталася помилка. Спробуйте пізніше.";
    return "Не вдалося зʼєднатися із сервером.";
}
function fail(e) { notify(errText(e), "error"); }
const dtFmt = new Intl.DateTimeFormat("uk-UA", { dateStyle: "long", timeStyle: "short", timeZone: TZ });
const dFmt = new Intl.DateTimeFormat("uk-UA", { dateStyle: "long", timeZone: TZ });
const fmtDateTime = (iso) => (iso ? dtFmt.format(new Date(iso)) : "—");
const fmtDate = (iso) => (iso ? dFmt.format(new Date(iso)) : "—");
function kyivOffsetMs(utcMs) {
    const p = new Intl.DateTimeFormat("en-US", { timeZone: TZ, hourCycle: "h23", year: "numeric", month: "numeric", day: "numeric", hour: "numeric", minute: "numeric", second: "numeric" })
        .formatToParts(new Date(utcMs));
    const g = (t) => Number(p.find(x => x.type === t).value);
    return Date.UTC(g("year"), g("month") - 1, g("day"), g("hour"), g("minute"), g("second")) - Math.floor(utcMs / 1000) * 1000;
}
function kyivLocalToIso(v) {
    const m = v.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
    const guess = Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5]);
    let t = guess - kyivOffsetMs(guess);
    t = guess - kyivOffsetMs(t);
    return new Date(t).toISOString().replace(".000Z", "Z");
}
function isoToKyivLocal(iso) {
    if (!iso)
        return "";
    const ms = Date.parse(iso);
    return new Date(ms + kyivOffsetMs(ms)).toISOString().slice(0, 16);
}
async function api(method, path, body) {
    const init = { method, credentials: "same-origin", headers: { Accept: "application/json" } };
    if (body !== undefined) {
        init.headers["Content-Type"] = "application/json";
        init.body = JSON.stringify(body);
    }
    const res = await fetch(path, init);
    if (res.status === 204)
        return null;
    let data = null;
    try {
        data = await res.json();
    }
    catch {
        data = null;
    }
    if (!res.ok) {
        const fe = {};
        const raw = data && data.fieldErrors;
        if (Array.isArray(raw))
            raw.forEach((x) => { if (x && x.field)
                fe[x.field] = x.message; });
        else if (raw && typeof raw === "object")
            Object.assign(fe, raw);
        throw new ApiError(res.status, (data && data.code) || "", (data && data.message) || "", fe);
    }
    return data;
}
let uid = 0;
function field(name, label, control, hint) {
    const id = "f" + ++uid;
    control.id = id;
    control.setAttribute("name", name);
    const errId = id + "-err";
    control.setAttribute("aria-describedby", errId + (hint ? " " + id + "-hint" : ""));
    return h("div", { class: "field" }, h("label", { for: id }, label), control, hint ? h("span", { class: "hint", id: id + "-hint" }, hint) : null, h("span", { class: "field-error", id: errId, "data-err": name }));
}
const input = (type, extra = {}) => h("input", { type, ...extra });
function clearErrors(form) {
    form.querySelectorAll("[data-err]").forEach(e => (e.textContent = ""));
    form.querySelectorAll("[aria-invalid]").forEach(e => e.removeAttribute("aria-invalid"));
}
function showErrors(form, e) {
    clearErrors(form);
    if (!(e instanceof ApiError)) {
        fail(e);
        return;
    }
    const left = [];
    for (const [f, msg] of Object.entries(e.fieldErrors)) {
        const span = form.querySelector(`[data-err="${CSS.escape(f)}"]`);
        if (span) {
            span.textContent = msg;
            span.parentElement?.querySelector("input,select,textarea")?.setAttribute("aria-invalid", "true");
        }
        else
            left.push(`${f}: ${msg}`);
    }
    const top = form.querySelector('[data-err="_form"]');
    const general = errText(e);
    if (top)
        top.textContent = [general, ...left].join(" ");
    else
        notify(general, "error");
    const first = form.querySelector('[aria-invalid="true"]');
    if (first)
        first.focus();
}
const formError = () => h("p", { class: "form-error", "data-err": "_form", role: "alert" });
const val = (f, n) => (f.elements.namedItem(n)?.value ?? "").trim();
const num = (f, n) => (val(f, n) === "" ? null : Number(val(f, n)));
function badge(status) { return h("span", { class: "badge " + status }, STATUS_UK[status] || status); }
function empty(text) { return h("p", { class: "empty" }, text); }
function renderHeader(route) {
    const nav = document.getElementById("main-nav");
    const link = (href, text, key) => h("li", {}, h("a", { href, "aria-current": route === key ? "page" : false }, text));
    const items = [link("#/", "Каталог", "catalog")];
    if (currentUser?.role === "PARTICIPANT")
        items.push(link("#/my", "Мої заявки", "my"));
    if (currentUser?.role === "COORDINATOR")
        items.push(link("#/coordinator", "Кабінет координатора", "coordinator"));
    if (currentUser)
        items.push(h("li", {}, h("button", { type: "button", onclick: logout }, "Вихід")));
    else
        items.push(link("#/login", "Вхід", "login"));
    nav.replaceChildren(h("ul", {}, ...items));
    document.getElementById("user-name").textContent = currentUser ? `Користувач: ${currentUser.fullName}` : "Гість";
}
async function loadUser() {
    try {
        currentUser = await api("GET", "/api/auth/me");
    }
    catch {
        currentUser = null;
    }
}
async function logout() {
    try {
        await api("POST", "/api/auth/logout");
    }
    catch { }
    currentUser = null;
    location.hash = "#/";
    await route();
    notify("Ви вийшли з акаунта.");
}
function show(route, title, ...content) {
    renderHeader(route);
    const m = mainEl();
    const h1 = h("h1", { tabindex: "-1" }, title);
    m.replaceChildren(h1, ...content.filter(Boolean));
    document.title = `${title} — ЕкоПортал`;
    h1.focus({ preventScroll: false });
    return m;
}
function goLogin() {
    location.hash = "#/login?return=" + encodeURIComponent(location.hash || "#/");
}
async function route() {
    const raw = location.hash || "#/";
    const [path, query = ""] = raw.split("?");
    try {
        if (path.startsWith("#/event/"))
            return await pageEvent(decodeURIComponent(path.slice(8)));
        if (path === "#/login")
            return pageAuth(new URLSearchParams(query).get("return") || "");
        if (path === "#/my") {
            if (!currentUser)
                return goLogin();
            if (currentUser.role !== "PARTICIPANT")
                return forbidden();
            return await pageMy();
        }
        if (path === "#/coordinator") {
            if (!currentUser)
                return goLogin();
            if (currentUser.role !== "COORDINATOR")
                return forbidden();
            return await pageCoordinator();
        }
        return await pageCatalog();
    }
    catch (e) {
        show("", "Помилка", h("p", {}, errText(e)), h("a", { class: "btn", href: "#/" }, "До каталогу"));
    }
}
function forbidden() {
    show("", "Доступ заборонено", h("p", {}, "У вас немає прав для перегляду цієї сторінки."), h("a", { class: "btn", href: "#/" }, "До каталогу"));
}
async function pageCatalog() {
    const list = h("div", { id: "event-list" });
    const form = h("form", { class: "panel", "aria-label": "Фільтри подій", novalidate: true }, h("div", { class: "row" }, field("city", "Місто", input("text", { value: catalogFilters.city, autocomplete: "off" })), field("dateFrom", "Дата від", input("date", { value: catalogFilters.dateFrom })), field("dateTo", "Дата до", input("date", { value: catalogFilters.dateTo }))), formError(), h("div", { class: "actions" }, h("button", { class: "btn", type: "submit" }, "Знайти"), h("button", { class: "btn secondary", type: "button", id: "reset" }, "Скинути")));
    show("catalog", "Каталог подій", form, h("section", { "aria-label": "Список подій" }, list));
    const load = async () => {
        list.replaceChildren(h("p", { class: "muted" }, "Завантаження…"));
        const q = new URLSearchParams();
        Object.keys(catalogFilters).forEach(k => { if (catalogFilters[k])
            q.set(k, catalogFilters[k]); });
        try {
            const events = await api("GET", "/api/events" + (q.toString() ? "?" + q : ""));
            clearErrors(form);
            if (!events.length) {
                list.replaceChildren(empty("Подій не знайдено"));
                return;
            }
            list.replaceChildren(h("div", { class: "grid" }, ...events.map(eventCard)));
        }
        catch (e) {
            list.replaceChildren();
            showErrors(form, e);
        }
    };
    form.addEventListener("submit", ev => {
        ev.preventDefault();
        catalogFilters.city = val(form, "city");
        catalogFilters.dateFrom = val(form, "dateFrom");
        catalogFilters.dateTo = val(form, "dateTo");
        load();
    });
    $("#reset", form).addEventListener("click", () => {
        form.reset();
        form.elements.namedItem("city").value = "";
        catalogFilters.city = catalogFilters.dateFrom = catalogFilters.dateTo = "";
        load();
    });
    await load();
}
function eventCard(e) {
    return h("article", { class: "card" }, h("h2", {}, e.title), h("p", {}, "📍 ", e.city), h("p", {}, "🗓 ", fmtDateTime(e.startsAt)), h("p", {}, "Вільних місць: ", h("strong", {}, e.freeSpots)), h("a", { class: "btn", href: "#/event/" + encodeURIComponent(e.id), "aria-label": `Детальніше: ${e.title}` }, "Детальніше"));
}
async function pageEvent(id) {
    let ev;
    try {
        ev = await api("GET", "/api/events/" + encodeURIComponent(id));
    }
    catch (e) {
        if (e instanceof ApiError && e.status === 404) {
            show("", "Подію не знайдено", h("p", {}, "Такої події не існує або вона недоступна."), h("a", { class: "btn", href: "#/" }, "До каталогу"));
            return;
        }
        throw e;
    }
    const hasCoords = typeof ev.latitude === "number" && typeof ev.longitude === "number";
    const meta = h("dl", { class: "meta" }, h("dt", {}, "Ініціатива"), h("dd", {}, ev.initiative?.title ?? "—"), h("dt", {}, "Місто"), h("dd", {}, ev.city), h("dt", {}, "Адреса"), h("dd", {}, ev.addressText || "—", hasCoords ? h("span", {}, " · ", h("a", { href: `https://www.openstreetmap.org/?mlat=${ev.latitude}&mlon=${ev.longitude}#map=16/${ev.latitude}/${ev.longitude}`, target: "_blank", rel: "noopener noreferrer" }, "Відкрити на карті")) : null), h("dt", {}, "Початок"), h("dd", {}, fmtDateTime(ev.startsAt)), h("dt", {}, "Завершення"), h("dd", {}, fmtDateTime(ev.endsAt)), h("dt", {}, "Реєстрація до"), h("dd", {}, fmtDateTime(ev.registrationClosesAt)), h("dt", {}, "Ліміт учасників"), h("dd", {}, ev.participantsLimit), h("dt", {}, "Вільних місць"), h("dd", {}, h("strong", {}, ev.freeSpots)));
    const article = h("article", { class: "panel" }, meta, h("h2", {}, "Опис"), h("p", { class: "description" }, ev.description || "Опис відсутній."));
    let apply;
    if (!currentUser) {
        apply = h("section", { class: "panel" }, h("h2", {}, "Участь у події"), h("button", { class: "btn", type: "button", onclick: goLogin }, "Увійти, щоб подати заявку"));
    }
    else if (currentUser.role === "PARTICIPANT") {
        const ta = h("textarea", { maxlength: "500" });
        const counter = h("span", { class: "hint", "aria-live": "off" }, "0 / 500");
        ta.addEventListener("input", () => (counter.textContent = `${ta.value.length} / 500`));
        const form = h("form", { novalidate: true }, field("comment", "Коментар (необовʼязково)", ta), counter, formError(), h("div", { class: "actions" }, h("button", { class: "btn", type: "submit" }, "Подати заявку")));
        form.addEventListener("submit", async (evt) => {
            evt.preventDefault();
            const btn = $("button[type=submit]", form);
            btn.disabled = true;
            try {
                const c = ta.value.trim();
                await api("POST", `/api/events/${encodeURIComponent(id)}/applications`, c ? { comment: c } : {});
                location.hash = "#/my";
                notify("Заявку подано. Статус можна переглянути в розділі «Мої заявки».");
            }
            catch (e) {
                showErrors(form, e);
                btn.disabled = false;
            }
        });
        apply = h("section", { class: "panel" }, h("h2", {}, "Подати заявку"), form);
    }
    else {
        apply = h("section", { class: "panel" }, h("p", { class: "muted" }, "Подавати заявки можуть лише учасники."));
    }
    show("", ev.title, h("p", {}, h("a", { href: "#/" }, "← До каталогу")), article, apply);
}
function pageAuth(returnTo) {
    if (currentUser) {
        location.hash = "#/";
        return;
    }
    const loginForm = h("form", { novalidate: true, "aria-label": "Вхід" }, field("email", "Електронна пошта", input("email", { autocomplete: "username", required: true })), field("password", "Пароль", input("password", { autocomplete: "current-password", required: true })), formError(), h("button", { class: "btn", type: "submit" }, "Увійти"));
    const regForm = h("form", { novalidate: true, "aria-label": "Реєстрація", hidden: true }, field("fullName", "Повне імʼя", input("text", { autocomplete: "name", required: true })), field("email", "Електронна пошта", input("email", { autocomplete: "email", required: true })), field("password", "Пароль", input("password", { autocomplete: "new-password", required: true, minlength: "8", maxlength: "72" }), "Від 8 до 72 символів"), formError(), h("button", { class: "btn", type: "submit" }, "Зареєструватися"));
    const tabs = [h("button", { type: "button", role: "tab", "aria-selected": "true", id: "tab-login" }, "Вхід"),
        h("button", { type: "button", role: "tab", "aria-selected": "false", id: "tab-reg", tabindex: "-1" }, "Реєстрація")];
    const forms = [loginForm, regForm];
    const select = (i) => {
        tabs.forEach((t, k) => { t.setAttribute("aria-selected", String(k === i)); t.tabIndex = k === i ? 0 : -1; forms[k].hidden = k !== i; });
    };
    wireTabs(tabs, select);
    const finish = async (form, call) => {
        try {
            await call();
            await loadUser();
            const target = returnTo && returnTo.startsWith("#/") ? returnTo : currentUser?.role === "COORDINATOR" ? "#/coordinator" : "#/";
            location.hash = target;
            if (location.hash === target)
                await route();
            notify(`Вітаємо, ${currentUser?.fullName ?? ""}!`);
        }
        catch (e) {
            showErrors(form, e);
        }
    };
    loginForm.addEventListener("submit", ev => {
        ev.preventDefault();
        finish(loginForm, () => api("POST", "/api/auth/login", { email: val(loginForm, "email"), password: loginForm.elements.namedItem("password").value }));
    });
    regForm.addEventListener("submit", ev => {
        ev.preventDefault();
        const body = { fullName: val(regForm, "fullName"), email: val(regForm, "email"), password: regForm.elements.namedItem("password").value };
        finish(regForm, async () => {
            await api("POST", "/api/auth/register", body);
            await api("POST", "/api/auth/login", { email: body.email, password: body.password });
        });
    });
    const help = h("aside", { class: "help", "aria-label": "Тестові акаунти" }, h("strong", {}, "Тестові акаунти"), h("p", {}, "Учасник: ", h("code", {}, "participant1@ecoportal.test")), h("p", {}, "Координатор: ", h("code", {}, "coordinator1@ecoportal.test")), h("p", {}, "Пароль: ", h("code", {}, "Password123")));
    show("login", "Вхід та реєстрація", help, h("section", { class: "panel" }, h("div", { class: "tabs", role: "tablist", "aria-label": "Вхід або реєстрація" }, ...tabs), loginForm, regForm));
}
function wireTabs(tabs, select) {
    tabs.forEach((t, i) => {
        t.addEventListener("click", () => select(i));
        t.addEventListener("keydown", (ev) => {
            let n = -1;
            if (ev.key === "ArrowRight")
                n = (i + 1) % tabs.length;
            else if (ev.key === "ArrowLeft")
                n = (i + tabs.length - 1) % tabs.length;
            if (n >= 0) {
                ev.preventDefault();
                select(n);
                tabs[n].focus();
            }
        });
    });
}
async function pageMy() {
    const apps = await api("GET", "/api/my/applications");
    const box = h("div", {});
    const draw = (items) => {
        if (!items.length) {
            box.replaceChildren(empty("У вас ще немає заявок."));
            return;
        }
        box.replaceChildren(h("div", { class: "grid cols-2" }, ...items.map(a => {
            const e = a.event || {};
            const title = e.title ?? a.eventTitle ?? "Подія";
            const btn = h("button", { class: "btn danger small", type: "button", disabled: !a.canCancel, "aria-label": `Скасувати заявку: ${title}` }, "Скасувати");
            btn.addEventListener("click", async () => {
                btn.disabled = true;
                try {
                    await api("POST", `/api/applications/${encodeURIComponent(a.id)}/cancel`);
                    notify("Заявку скасовано.");
                    draw(await api("GET", "/api/my/applications"));
                }
                catch (err) {
                    fail(err);
                    btn.disabled = !a.canCancel;
                }
            });
            return h("article", { class: "card" }, h("h2", {}, title), h("p", {}, "📍 ", e.city ?? a.city ?? "—"), h("p", {}, "🗓 ", fmtDateTime(e.startsAt ?? a.startsAt)), h("p", {}, "Статус: ", badge(a.status)), a.comment ? h("p", { class: "muted" }, "Коментар: ", a.comment) : null, h("div", { class: "actions" }, btn));
        })));
    };
    show("my", "Мої заявки", h("section", { "aria-label": "Список заявок" }, box));
    draw(apps);
}
async function pageCoordinator() {
    const labels = ["Ініціативи та події", "Заявки на події", "Підсумки та звіт"];
    const tabs = labels.map((l, i) => h("button", { type: "button", role: "tab", id: "ctab" + i, "aria-controls": "cpanel", "aria-selected": String(i === coordTab), tabindex: i === coordTab ? "0" : "-1" }, l));
    const panel = h("div", { id: "cpanel", role: "tabpanel", tabindex: "-1" });
    show("coordinator", "Кабінет координатора", h("div", { class: "tabs", role: "tablist", "aria-label": "Розділи кабінету" }, ...tabs), panel);
    const select = async (i) => {
        coordTab = i;
        tabs.forEach((t, k) => { t.setAttribute("aria-selected", String(k === i)); t.tabIndex = k === i ? 0 : -1; });
        panel.setAttribute("aria-labelledby", "ctab" + i);
        panel.replaceChildren(h("p", { class: "muted" }, "Завантаження…"));
        try {
            const initiatives = await api("GET", "/api/coordinator/initiatives");
            if (i === 0)
                tabInitiatives(panel, initiatives, () => select(0));
            else if (i === 1)
                tabApplications(panel, initiatives);
            else
                tabSummary(panel, initiatives);
        }
        catch (e) {
            panel.replaceChildren(h("p", { class: "form-error" }, errText(e)));
        }
    };
    wireTabs(tabs, i => { select(i); });
    await select(coordTab);
}
const allEvents = (ini) => ini.flatMap(i => (i.events || []).map((e) => ({ ...e, initiativeTitle: i.title })));
const eventSelect = (events) => h("select", {}, h("option", { value: "" }, "— оберіть подію —"), ...events.map(e => h("option", { value: e.id }, `${e.title} (${fmtDate(e.startsAt)})`)));
function tabInitiatives(panel, initiatives, reload) {
    const iForm = h("form", { novalidate: true, "aria-label": "Нова ініціатива" }, field("title", "Назва", input("text", { required: true })), field("description", "Опис", h("textarea", {})), field("category", "Категорія", input("text", { placeholder: "Наприклад: Озеленення" })), field("status", "Статус", h("select", {}, h("option", { value: "PUBLISHED" }, "Опубліковано"), h("option", { value: "DRAFT" }, "Чернетка"))), formError(), h("button", { class: "btn", type: "submit" }, "Створити ініціативу"));
    iForm.addEventListener("submit", async (ev) => {
        ev.preventDefault();
        try {
            await api("POST", "/api/coordinator/initiatives", { title: val(iForm, "title"), description: val(iForm, "description"), category: val(iForm, "category"), status: val(iForm, "status") });
            notify("Ініціативу створено.");
            reload();
        }
        catch (e) {
            showErrors(iForm, e);
        }
    });
    let editId = null;
    const iniSel = h("select", { required: true }, h("option", { value: "" }, "— оберіть ініціативу —"), ...initiatives.map(i => h("option", { value: i.id }, i.title)));
    const eHead = h("h3", {}, "Нова подія");
    const submitBtn = h("button", { class: "btn", type: "submit" }, "Створити подію");
    const cancelEdit = h("button", { class: "btn secondary", type: "button", hidden: true }, "Скасувати редагування");
    const tz = "Час вказується за київським часом (Europe/Kyiv)";
    const eForm = h("form", { novalidate: true, "aria-label": "Форма події" }, field("initiativeId", "Ініціатива", iniSel), field("title", "Назва", input("text", { required: true })), field("description", "Опис", h("textarea", {})), h("div", { class: "row" }, field("city", "Місто", input("text", { required: true })), field("addressText", "Адреса", input("text"))), h("div", { class: "row" }, field("latitude", "Широта", input("number", { step: "any", min: "-90", max: "90" })), field("longitude", "Довгота", input("number", { step: "any", min: "-180", max: "180" }))), h("div", { class: "row" }, field("startsAt", "Початок", input("datetime-local", { required: true }), tz), field("endsAt", "Завершення", input("datetime-local", { required: true }), tz), field("registrationClosesAt", "Реєстрація до", input("datetime-local"), tz)), field("participantsLimit", "Ліміт учасників", input("number", { min: "1", step: "1", required: true })), formError(), h("div", { class: "actions" }, submitBtn, cancelEdit));
    const setForm = (ev) => {
        eForm.reset();
        editId = ev ? ev.id : null;
        eHead.textContent = ev ? "Редагування події" : "Нова подія";
        submitBtn.textContent = ev ? "Зберегти зміни" : "Створити подію";
        cancelEdit.hidden = !ev;
        iniSel.disabled = !!ev;
        clearErrors(eForm);
        if (!ev)
            return;
        const set = (n, v) => (eForm.elements.namedItem(n).value = v == null ? "" : String(v));
        set("initiativeId", ev.initiativeId ?? ev.initiative?.id);
        ["title", "description", "city", "addressText", "latitude", "longitude", "participantsLimit"].forEach(n => set(n, ev[n]));
        set("startsAt", isoToKyivLocal(ev.startsAt));
        set("endsAt", isoToKyivLocal(ev.endsAt));
        set("registrationClosesAt", isoToKyivLocal(ev.registrationClosesAt));
        eForm.scrollIntoView({ block: "center" });
        eForm.elements.namedItem("title").focus();
    };
    cancelEdit.addEventListener("click", () => setForm(null));
    eForm.addEventListener("submit", async (ev) => {
        ev.preventDefault();
        const dt = (n) => (val(eForm, n) ? kyivLocalToIso(val(eForm, n)) : null);
        const body = {
            title: val(eForm, "title"), description: val(eForm, "description"), city: val(eForm, "city"), addressText: val(eForm, "addressText"),
            latitude: num(eForm, "latitude"), longitude: num(eForm, "longitude"),
            startsAt: dt("startsAt"), endsAt: dt("endsAt"), registrationClosesAt: dt("registrationClosesAt"), participantsLimit: num(eForm, "participantsLimit"),
        };
        try {
            if (editId)
                await api("PUT", `/api/coordinator/events/${encodeURIComponent(editId)}`, body);
            else {
                body.initiativeId = val(eForm, "initiativeId");
                await api("POST", "/api/coordinator/events", body);
            }
            notify(editId ? "Подію оновлено." : "Подію створено (чернетка).");
            reload();
        }
        catch (e) {
            showErrors(eForm, e);
        }
    });
    const setStatus = async (e, status) => {
        try {
            await api("PATCH", `/api/coordinator/events/${encodeURIComponent(e.id)}/status`, { status });
            notify(status === "PUBLISHED" ? "Подію опубліковано." : "Подію скасовано.");
            reload();
        }
        catch (err) {
            fail(err);
        }
    };
    const evItem = (e) => h("li", {}, h("strong", {}, e.title), " ", badge(e.status), h("p", { class: "muted" }, `${e.city ?? ""} · ${fmtDateTime(e.startsAt)} · активних заявок: ${e.activeApplicationsCount ?? e.activeApplications ?? 0} / ${e.participantsLimit ?? "—"}`), h("div", { class: "actions" }, h("button", { class: "btn secondary small", type: "button", onclick: () => setForm({ ...e }) }, "Редагувати"), e.status === "DRAFT" ? h("button", { class: "btn small", type: "button", onclick: () => setStatus(e, "PUBLISHED") }, "Опублікувати") : null, e.status !== "CANCELLED" ? h("button", { class: "btn danger small", type: "button", onclick: () => setStatus(e, "CANCELLED") }, "Скасувати подію") : null));
    const list = initiatives.length
        ? h("div", { class: "grid cols-2" }, ...initiatives.map(i => h("article", { class: "card" }, h("h3", {}, i.title, " ", badge(i.status || "PUBLISHED")), i.category ? h("p", { class: "muted" }, "Категорія: ", i.category) : null, (i.events || []).length ? h("ul", { class: "sub-list" }, ...i.events.map(evItem)) : h("p", { class: "muted" }, "Подій ще немає."))))
        : empty("Ініціатив ще немає. Створіть першу.");
    panel.replaceChildren(h("section", { class: "panel", "aria-label": "Ініціативи та події" }, h("h2", {}, "Мої ініціативи та події"), list), h("div", { class: "grid cols-2" }, h("section", { class: "panel" }, h("h2", {}, "Нова ініціатива"), iForm), h("section", { class: "panel" }, eHead, eForm)));
}
function tabApplications(panel, initiatives) {
    const sel = eventSelect(allEvents(initiatives));
    const out = h("div", { "aria-live": "off" });
    const load = async (id) => {
        if (!id) {
            out.replaceChildren(empty("Оберіть подію, щоб побачити заявки."));
            return;
        }
        try {
            const apps = await api("GET", `/api/coordinator/events/${encodeURIComponent(id)}/applications`);
            if (!apps.length) {
                out.replaceChildren(empty("Заявок на цю подію немає."));
                return;
            }
            const decide = async (a, decision) => {
                try {
                    await api("PATCH", `/api/coordinator/applications/${encodeURIComponent(a.id)}`, { decision });
                    notify(decision === "APPROVED" ? "Заявку схвалено." : "Заявку відхилено.");
                    load(id);
                }
                catch (e) {
                    fail(e);
                }
            };
            const rows = apps.map(a => {
                const who = a.applicant || a.participant || a.user || {};
                const name = who.fullName ?? a.applicantName ?? a.fullName ?? "—";
                const email = who.email ?? a.applicantEmail ?? a.email ?? "";
                return h("tr", {}, h("td", { "data-label": "Заявник" }, name, email ? h("br") : null, email ? h("span", { class: "muted" }, email) : null), h("td", { "data-label": "Коментар" }, a.comment || "—"), h("td", { "data-label": "Статус" }, badge(a.status)), h("td", { class: "no-label" }, h("div", { class: "actions" }, a.status === "PENDING" ? h("button", { class: "btn small", type: "button", onclick: () => decide(a, "APPROVED"), "aria-label": `Схвалити заявку: ${name}` }, "Схвалити") : null, a.status === "PENDING" || a.status === "APPROVED" ? h("button", { class: "btn danger small", type: "button", onclick: () => decide(a, "REJECTED"), "aria-label": `Відхилити заявку: ${name}` }, "Відхилити") : null)));
            });
            out.replaceChildren(h("div", { class: "table-wrap" }, h("table", { class: "stack" }, h("caption", { class: "muted" }, "Заявки на обрану подію"), h("thead", {}, h("tr", {}, ...["Заявник", "Коментар", "Статус", "Дії"].map(t => h("th", { scope: "col" }, t)))), h("tbody", {}, ...rows))));
        }
        catch (e) {
            out.replaceChildren();
            fail(e);
        }
    };
    sel.addEventListener("change", () => load(sel.value));
    panel.replaceChildren(h("section", { class: "panel" }, h("h2", {}, "Заявки на події"), field("event", "Подія", sel), out));
    load("");
}
function tabSummary(panel, initiatives) {
    const sel = eventSelect(allEvents(initiatives));
    const nonNeg = { min: "0", step: "any", required: true };
    const sForm = h("form", { novalidate: true, "aria-label": "Підсумки події" }, field("event", "Завершена подія", sel), h("div", { class: "row" }, field("actualParticipants", "Учасників (осіб)", input("number", { min: "0", step: "1", required: true })), field("wasteKg", "Зібрано відходів (кг)", input("number", nonNeg)), field("treesPlanted", "Посаджено дерев", input("number", { min: "0", step: "1", required: true })), field("hoursSpent", "Витрачено годин", input("number", nonNeg))), field("note", "Примітка", h("textarea", {})), formError(), h("button", { class: "btn", type: "submit" }, "Зберегти підсумки"));
    sForm.addEventListener("submit", async (ev) => {
        ev.preventDefault();
        if (!sel.value) {
            showErrors(sForm, new ApiError(400, "", "Оберіть подію.", { event: "Оберіть подію" }));
            return;
        }
        const body = { actualParticipants: num(sForm, "actualParticipants"), wasteKg: num(sForm, "wasteKg"), treesPlanted: num(sForm, "treesPlanted"), hoursSpent: num(sForm, "hoursSpent"), note: val(sForm, "note") };
        try {
            await api("PUT", `/api/coordinator/events/${encodeURIComponent(sel.value)}/summary`, body);
            clearErrors(sForm);
            notify("Підсумки збережено.");
        }
        catch (e) {
            showErrors(sForm, e);
        }
    });
    const out = h("div", {});
    const rForm = h("form", { novalidate: true, "aria-label": "Звіт за період" }, h("div", { class: "row" }, field("from", "Від", input("date", { required: true })), field("to", "До", input("date", { required: true }))), formError(), h("button", { class: "btn", type: "submit" }, "Сформувати звіт"));
    rForm.addEventListener("submit", async (ev) => {
        ev.preventDefault();
        const from = val(rForm, "from"), to = val(rForm, "to");
        if (!from || !to) {
            showErrors(rForm, new ApiError(400, "", "Вкажіть обидві дати.", { from: from ? "" : "Вкажіть дату", to: to ? "" : "Вкажіть дату" }));
            return;
        }
        try {
            const r = await api("GET", `/api/coordinator/reports?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
            clearErrors(rForm);
            const n = (x) => (typeof x === "number" ? x.toLocaleString("uk-UA", { maximumFractionDigits: 2 }) : "0");
            const cells = (c) => [n(c.eventsCount), n(c.totalParticipants), n(c.totalWasteKg), n(c.totalTreesPlanted), n(c.totalHoursSpent)];
            const heads = ["Населений пункт", "Подій", "Учасників", "Відходи, кг", "Дерева", "Години"];
            const cities = r.cities || [];
            out.replaceChildren(...(r.syntheticData ? [h("p", { class: "help" }, "⚠ Синтетичні навчальні дані")] : []), cities.length ? h("div", { class: "table-wrap" }, h("table", { class: "stack" }, h("caption", { class: "muted" }, `Звіт за період ${fmtDate(r.from + "T12:00:00Z")} — ${fmtDate(r.to + "T12:00:00Z")}`), h("thead", {}, h("tr", {}, ...heads.map(t => h("th", { scope: "col" }, t)))), h("tbody", {}, ...cities.map(c => h("tr", {}, h("td", { "data-label": heads[0] }, c.city), ...cells(c).map((v, k) => h("td", { "data-label": heads[k + 1] }, v))))), r.totals ? h("tfoot", {}, h("tr", {}, h("th", { scope: "row", "data-label": heads[0] }, "Разом"), ...cells(r.totals).map((v, k) => h("td", { "data-label": heads[k + 1] }, v)))) : null))
                : empty("За цей період даних немає."));
        }
        catch (e) {
            out.replaceChildren();
            showErrors(rForm, e);
        }
    });
    panel.replaceChildren(h("div", { class: "grid cols-2" }, h("section", { class: "panel" }, h("h2", {}, "Підсумки події"), sForm), h("section", { class: "panel" }, h("h2", {}, "Звіт за період"), rForm)), h("section", { class: "panel", "aria-label": "Результати звіту" }, out));
}
window.addEventListener("hashchange", () => { clearNotices(); route(); });
loadUser().then(route);
