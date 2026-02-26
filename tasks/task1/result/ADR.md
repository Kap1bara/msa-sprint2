### <a name="_b7urdng99y53"></a>**Название задачи:**  Hotelio: текущий срез и переход на микросервисную архитектуру с поэтапным выделением (Strangler Fig)

### <a name="_hjk0fkfyohdk"></a>**Автор:** Мурин А.А.
### <a name="_uanumrh8zrui"></a>**Дата:**
 

---

## Контекст

Сервис Hotelio реализован как монолитное Spring Boot приложение, содержащее все бизнес-модули:

- Booking
- User
- Hotel
- PromoCode
- Review

Все модули:
- работают в одном процессе,
- используют общую базу данных PostgreSQL,
- тесно связаны друг с другом через прямые вызовы сервисов.

### Основные проблемы

1. **Сложность сопровождения**
   - Изменения в Booking требуют понимания User, Hotel, Promo, Review.
   - Высокая связность сервисов.

2. **Низкая масштабируемость**
   - Невозможно масштабировать бронирование независимо.
   - Общая БД создаёт единый радиус отказа.

3. **Ограниченная гибкость разработки**
   - Один артефакт → сложный CI/CD.
   - Параллельная работа команд затруднена.

4. **Риски внедрения изменений**
   - Любая новая фича затрагивает весь монолит.
   - Высокий риск регрессии.

---

### <a name="_3bfxc9a45514"></a>**Функциональные требования**

| № | Акторы | Use Case | Описание |
|:-:|--------|----------|----------|
| 1 | Пользователь | Создать бронирование | Проверка пользователя → проверка отеля → проверка доверия → проверка промокода → расчёт цены → сохранение |
| 2 | Пользователь | Просмотреть бронирования | Получение списка бронирований по userId |
| 3 | Пользователь | Получить данные отеля | Информация об отеле |
| 4 | Пользователь | Проверить статус пользователя | Active / VIP / Blacklisted |
| 5 | Пользователь | Проверить промокод | Валидность и применимость |
| 6 | Пользователь | Получить отзывы | Отзывы и trusted-флаг |

---

### <a name="_u8xz25hbrgql"></a>**Нефункциональные требования**

1. Пошаговая миграция без остановки системы.
2. Сохранение REST-контракта `/api/bookings` на первом этапе.
3. Возможность масштабирования бронирования независимо.
4. Наблюдаемость (метрики, корреляция запросов).
5. Возможность быстрого отката.
6. Подготовка к независимому CI/CD.
7. Эволюция к database-per-service.
8. Поддержка будущего API Gateway / BFF.

---

### <a name="_qmphm5d6rvi3"></a>**Решение**

### Стратегия

Выбран подход **Strangler Fig**:

- Монолит остаётся REST-фасадом. В последствии контроллеры могут стать BFF или API Gateway
- Модуль Booking выносится в отдельный сервис.
- Делегирование происходит через gRPC-прокси, уже предусмотренный в проекте (`BOOKING_SERVICE_EXTERNAL_HOST`). (подсказка в task2)

Это позволяет:
- минимально изменить код монолита,
- быстро откатиться при проблемах,
- начать масштабирование самого нагруженного модуля.

---

# C4 Диаграммы

---

## C1 — System Context (Текущее состояние)

```plantuml
@startuml
!includeurl https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Context.puml

Person(user, "User", "Interacts with frontend")

System(monolith, "Hotelio Monolith", "Spring Boot", "REST API containing booking, user, hotel, promo and review logic")
SystemDb(db, "PostgreSQL", "Shared database")

Rel(user, monolith, "Uses", "HTTP/JSON")
Rel(monolith, db, "Reads/Writes")

@enduml
```
## C2 — Вынос Booking

```plantuml
@startuml First Step

!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml


Person(user, "User", "Uses frontend")

System_Boundary(hotelio, "Hotelio") {
   Container(monolith, "Hotelio Monolith (reduced)", "Spring Boot", "Still exposes REST API; delegates booking to booking-service")

   Container(bookingSvc, "Booking Service", "Java + gRPC", "Handles booking creation and listing")

ContainerDb(db, "PostgreSQL", "Shared DB (temporary)")
}

Rel(user, monolith, "Uses REST", "HTTP/JSON")
Rel(monolith, bookingSvc, "Delegates booking operations", "gRPC")
Rel(monolith, db, "Reads/Writes non-booking data")
Rel(bookingSvc, db, "Reads/Writes bookings")

@enduml
```

```plantuml
@startuml
!includeurl https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Component.puml


Container_Boundary(booking, "Booking Service") {

  Component(api, "Booking gRPC API", "gRPC endpoint", "CreateBooking / ListBookings")

  Component(appService, "Booking Application Service", "Java Service", "Orchestrates validation, computes price, persists booking")

  Component(repo, "BookingRepository", "JPA Repository", "CRUD for bookings")

  Component(userClient, "User Client", "HTTP/gRPC client", "Reads user status (temporary dependency)")

  Component(hotelClient, "Hotel Client", "HTTP/gRPC client", "Reads hotel status (temporary dependency)")

  Component(promoClient, "Promo Client", "HTTP/gRPC client", "Validates promo (temporary dependency)")

  Component(reviewClient, "Review Client", "HTTP/gRPC client", "Checks trusted hotel (temporary dependency)")

  ComponentDb(db, "Booking Data Store", "PostgreSQL", "Bookings (shared initially; later separate DB)")

  Rel(api, appService, "Invokes")
  Rel(appService, repo, "Uses")
  Rel(repo, db, "Reads/Writes")

  Rel(appService, userClient, "Validates user")
  Rel(appService, hotelClient, "Validates hotel")
  Rel(appService, promoClient, "Validates promo")
  Rel(appService, reviewClient, "Checks trust")
}

@enduml
```

В этом сценарии унаследованная система относительно проста и понятна, а ещё у неё чёткие и последовательные модели данных и протоколы. В таком случае добавлять ACL не выгодно. Прямая интеграция будет эффективней. 

### <a name="_bjrr7veeh80c"></a>**Альтернативы**

1. Начать с Promo/Review

- Меньше риска.

- Не решает проблему масштабирования бронирования.

2. Big Bang миграция

- Быстрое достижение целевого состояния.

- Очень высокий риск для небольшой команды. Antipattern

3. Сначала внедрить Gateway/BFF

- Улучшает API.

- Не снижает связность внутри домена,  а значит  мало поможет с масштабированием

### Недостатки, ограничения, риски

- Shared DB на старте сохраняет связность.

- Усложняется диагностика без трассировки.

- Риск расхождения контрактов.

- Требуется дисциплина версионирования API.

### План миграции (Strangler Fig)

## Шаг 0 — Подготовка

Зафиксировать контракт /api/bookings.

Добавить наблюдаемость.

Убрать ddl-auto: create, перейти на миграции.

## Шаг 1 — Вынос Booking

Создать booking-service.

Реализовать gRPC API.

Проверить стабильность.

## Шаг 2 — Снижение связности

Постепенно выделять зависимости (User, Hotel, Promo, Review).

В дальнейшем перейти к database-per-service.
