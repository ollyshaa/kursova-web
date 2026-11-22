-- Демонстраційні дані (ідемпотентні: ON CONFLICT DO NOTHING).
--
-- ТЕСТОВІ АКАУНТИ (пароль для всіх: Password123)
--   Координатор 1: coordinator1@ecoportal.test / Password123
--   Координатор 2: coordinator2@ecoportal.test / Password123
--   Учасник 1:     participant1@ecoportal.test / Password123
--   Учасник 2:     participant2@ecoportal.test / Password123

INSERT INTO users (id, full_name, email, password_hash, role) VALUES
 ('00000000-0000-4000-8000-000000000001', 'Олена Коваленко', 'coordinator1@ecoportal.test',
  '$2b$10$VUAInKefuFJckhkCUt7F6eyMwuIjVBjA0HsbKBEHaP11cR6FXkzgq', 'COORDINATOR'),
 ('00000000-0000-4000-8000-000000000002', 'Андрій Шевченко', 'coordinator2@ecoportal.test',
  '$2b$10$29yP1/NTUHFfngyOPmFOKuKDsEySoEyvYd9mG36tsqu/ZV3DX56iC', 'COORDINATOR'),
 ('00000000-0000-4000-8000-000000000003', 'Марія Бондаренко', 'participant1@ecoportal.test',
  '$2b$10$UtBd0BCptz/60VWFChUDBuigJdWF4OvAl0c2O.UCaaqErB1dKm3ky', 'PARTICIPANT'),
 ('00000000-0000-4000-8000-000000000004', 'Ігор Мельник', 'participant2@ecoportal.test',
  '$2b$10$gWop55mdEjtgs7BtTzLXj.fhNRtvtQ1mLwehaSYeziidDrPEFLegC', 'PARTICIPANT')
ON CONFLICT DO NOTHING;

INSERT INTO initiatives (id, coordinator_id, title, description, category, status) VALUES
 ('10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001',
  'Чисте узбережжя Одеси',
  'Регулярні толоки з прибирання пляжів і набережної Одеси, а також просвітницькі лекції про збереження Чорного моря.',
  'Очищення водойм', 'PUBLISHED'),
 ('10000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000002',
  'Зелений Київ',
  'Висадка дерев і кущів у парках та скверах Києва, догляд за молодими насадженнями разом із мешканцями міста.',
  'Озеленення', 'PUBLISHED')
ON CONFLICT DO NOTHING;

INSERT INTO events (id, initiative_id, title, description, city, address_text, latitude, longitude,
                    starts_at, ends_at, registration_closes_at, participants_limit, status) VALUES
 ('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001',
  'Прибирання пляжу Ланжерон',
  'Спільне прибирання пляжу від пластику та побутового сміття. Рукавички та мішки надаємо, візьміть із собою зручне взуття та воду.',
  'Одеса', 'пляж Ланжерон, Французький бульвар', 46.458600, 30.755300,
  date_trunc('day', now()) + interval '7 days 10 hours',
  date_trunc('day', now()) + interval '7 days 14 hours',
  date_trunc('day', now()) + interval '6 days 18 hours', 30, 'PUBLISHED'),
 ('20000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001',
  'Еко-лекція «Пластик у Чорному морі»',
  'Відкрита лекція екологів про вплив пластикового забруднення на морську екосистему та прості кроки, які може зробити кожен.',
  'Одеса', 'вул. Дерибасівська, 10', 46.485200, 30.741300,
  date_trunc('day', now()) + interval '14 days 18 hours',
  date_trunc('day', now()) + interval '14 days 20 hours',
  date_trunc('day', now()) + interval '13 days 12 hours', 50, 'PUBLISHED'),
 ('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000002',
  'Висадка дерев у парку Наталка',
  'Висаджуємо молоді дерева та кущі в парку. Інструменти й саджанці забезпечує організатор, потрібен одяг, який не шкода забруднити.',
  'Київ', 'Оболонський проспект, парк Наталка', 50.517800, 30.498000,
  date_trunc('day', now()) + interval '10 days 11 hours',
  date_trunc('day', now()) + interval '10 days 15 hours',
  date_trunc('day', now()) + interval '9 days 18 hours', 40, 'PUBLISHED'),
 ('20000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000002',
  'Еко-ярмарок на Подолі',
  'Чернетка: ярмарок місцевих екопродуктів, обміну речами та майстер-класів із сортування відходів.',
  'Київ', 'Контрактова площа', 50.465500, 30.516700,
  date_trunc('day', now()) + interval '30 days 12 hours',
  date_trunc('day', now()) + interval '30 days 18 hours',
  date_trunc('day', now()) + interval '28 days 18 hours', 100, 'DRAFT')
ON CONFLICT DO NOTHING;

INSERT INTO applications (id, event_id, user_id, comment, status, decided_by, decided_at) VALUES
 ('30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
  '00000000-0000-4000-8000-000000000003', 'Прийду з другом, маємо власні рукавички.', 'PENDING', NULL, NULL),
 ('30000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001',
  '00000000-0000-4000-8000-000000000004', 'Хочу долучитися до прибирання.', 'APPROVED',
  '00000000-0000-4000-8000-000000000001', now()),
 ('30000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000002',
  '00000000-0000-4000-8000-000000000003', 'Цікавить тема мікропластику.', 'REJECTED',
  '00000000-0000-4000-8000-000000000001', now()),
 ('30000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000003',
  '00000000-0000-4000-8000-000000000004', 'Маю досвід садівництва.', 'APPROVED',
  '00000000-0000-4000-8000-000000000002', now()),
 ('30000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000003',
  '00000000-0000-4000-8000-000000000003', 'На жаль, плани змінилися.', 'CANCELLED', NULL, NULL)
ON CONFLICT DO NOTHING;
