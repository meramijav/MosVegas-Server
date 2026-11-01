# Архитектура Mosvegas Core

Проект разделён на сервисы. `MosvegasCore` связывает события Fabric, `Commands` маршрутизирует команды, а бизнес-правила остаются в соответствующем сервисе.

| Область | Реализация | Данные/настройки |
|---|---|---|
| Паспорт и лицензии | `IdentityService` | `PlayerData.passport`, `PlayerData.licences`, `licencePrices` |
| Банк, наличные, зарплата | `EconomyService`, `RpItems` | `bankBalance`, NBT-купюры, `salaryByFactionLevel`, `bankTerminals` |
| Фракции и предметы | `FactionService`, `ProgressService` | `faction`, `factionLevel`, `hierarchyLevel`, NBT-владелец фракции |
| Серверный уровень | `ProgressService` | `serverLevel`, `serverLevelTicks`, `serverLevelIntervalSeconds` |
| Квесты | `QuestService` | `activeQuests`, `quests[]`; пустая фракция означает начальный квест |
| Недвижимость | `PropertyService` | `properties`, `PropertyRecord.members`, части границы `areas`, палка выделения |
| Плейсхолдеры | `PlaceholderService` | независимые ключи профиля, лицензий, уровней и балансов |
| Хранилище | `DataStore`, `SqliteReader` | JSON мира (временный файл, `.bak`, атомарная замена), autosave; одноразовый импорт SQLite-базы 0.0.4 без драйвера |
| Магазины | `ShopStore`, `ShopService`, `ShopCommands` | JSON мира; шаблоны ItemStack в SNBT, банковские покупки, версия каталога |
| Зоны | `ZoneStore`, `ZoneIndex`, `ZoneService`, `ZoneCommands` | JSON мира; AABB-дерево по измерениям, флаги/приоритеты, ACL на действие |
| Защита мира | серверные mixin + события Fabric | блоки/интеракции, PvP/снаряды, взрывы, огонь, жидкости, поршни, воронки |
| Интеграции | `TaczIntegration`, `ZoneAccess`, `TransportAccess` | необязательные события TaCZ, провайдеры внешних фракций, серверный API транспорта |

## Расширение

- Новые номиналы добавляются в `cashDenominations`; купюры помечаются стабильным NBT `mosvegas_cash_value`.
- Новый тип лицензии добавляется в `IdentityService` и `licencePrices`; служебного удостоверения в ядре больше нет.
- Начальный квест имеет `faction: null`, `public` или `starter`; обычный квест использует ID фракции.
- Граница квартиры/парковки — объединение прямоугольных частей `areas`, поэтому форма может быть любой (Г, П). Старые `min`/`max` переносятся в `areas` при загрузке конфига; объекты без частей продолжают работать через `location.radius`.
- Доступ к недвижимости проверяется внутри `ZoneService.can` до правил зон (`PropertyService.denying`): действия `build`, `doors`, `containers`, `interact`, `entities`, `vehicle_place` внутри чужого объекта запрещены всем, кроме владельца, арендатора, участников и `mosvegas.property.access.bypass`. Поэтому новая защитная точка, вызывающая `ZoneService`, автоматически учитывает недвижимость.
- Банковские терминалы задаются в `bankTerminals` или командами `/mosvegas bankterminal add|remove`; разрушение блока также удаляет терминал.
- Внешние моды могут получать плейсхолдер через `PlaceholderService.resolve` и распознавать RP-предметы через публичные ключи `RpItems`.
- Новые постоянные сущности следует добавлять полем `DataStore.State`: они сохраняются тем же атомарным файлом.

## Зоны и магазины (0.0.3)

Определения зон/магазинов относятся к конкретному миру и хранятся отдельно от данных игроков в двух JSON-файлах. `JsonFile` сначала пишет временный файл, сохраняет `.bak` и заменяет основной файл; новое состояние публикуется после успешного сохранения/валидации. Ошибочный reload сохраняет предыдущий снимок. Ошибка начальной загрузки останавливает запуск, а не отключает защиту молча.

Для зон индекс строится только при изменении/перезагрузке. Балансированное дерево ограничивающих объёмов не разворачивает городской регион в миллионы чанков. Проверяются только кандидаты в измерении и точке. Правило выбирается отдельно для каждого действия: максимальный явный приоритет, при равенстве все ограничения должны разрешить действие. Членство игрока берётся из текущих серверных данных. Глобальный overworld запрещает `BUILD` и `TERRAIN`; перемещение не имеет ограничивающего флага.

Шаблоны товаров декодируются при загрузке, для покупок выдаются копии. Изменение каталога увеличивает ревизию: устаревшие подтверждения отклоняются. Вместимость основного инвентаря проверяется до банковского списания; все действия происходят в одном серверном потоке. Шаблон не получает описание цены из GUI. Каталог имеет неограниченный запас. Покупка и autosave данных мира не являются общей транзакцией с Minecraft playerdata: аварийное выключение между сохранениями, как и у прежней экономики, не обеспечивает атомарного сохранения денег и вещей.

TaCZ подключается через `GunShootEvent`, `GunFireEvent` и `EntityHurtByGunEvent.Pre` без compile/runtime-зависимости. Рефлексия разрешает методы один раз при загрузке; регистрация не прошла при установленном несовместимом TaCZ — запуск прерывается с явной ошибкой. Его `ProjectileExplosion` наследует обработку блоков ванильного `Explosion.affectWorld`, которую фильтрует ядро. Для произвольных модов, обходящих стандартные события, требуется адаптер по публичному API.

`policyTest` проверяет правила и сравнивает индекс с линейным поиском. `src/smoke` — отдельный тестовый мод, запускающий настоящую Fabric-среду; он не попадает в релизный JAR. Результат серверных тестов проверяется Gradle, ошибки дают неуспешную задачу.

## Основные permission-ноды

`mosvegas.command.mfc`, `mosvegas.command.driverlicence`, `mosvegas.command.weaponlicence`, `mosvegas.command.bankcard`, `mosvegas.bank.withdraw`, `mosvegas.bank.deposit`, `mosvegas.bank.transfer`, `mosvegas.command.salary`, `mosvegas.command.faction`, `mosvegas.command.quests`, `mosvegas.quests.accept`, `mosvegas.quests.cancel`, `mosvegas.command.property`, `mosvegas.property.buy`, `mosvegas.property.rent`, `mosvegas.property.build.bypass`, `mosvegas.property.access.bypass`, `mosvegas.admin.levels`, `mosvegas.admin.factions`, `mosvegas.admin.quests`, `mosvegas.admin.properties`, `mosvegas.admin`.

Обычные действия по умолчанию доступны всем; административные используют fallback-уровень оператора 3. Поддерживаются точные ноды, `*` и wildcard вида `mosvegas.bank.*`.

Точечные административные ноды: `mosvegas.admin.config`, `mosvegas.admin.permissions`, `mosvegas.admin.bankterminal`, `mosvegas.admin.money`, `mosvegas.admin.serverlevel`, `mosvegas.admin.placeholders`, `mosvegas.admin.factions.manage`, `mosvegas.admin.factions.level`, `mosvegas.admin.factions.hierarchy`, `mosvegas.admin.quests.manage`, `mosvegas.admin.properties.view`, `mosvegas.admin.properties.wand`, `mosvegas.admin.properties.create`, `mosvegas.admin.properties.edit`, `mosvegas.admin.properties.delete`, `mosvegas.admin.properties.owner`, `mosvegas.admin.properties.members`, `mosvegas.admin.properties.bounds`.

Каждое действие доступно OP уровня 3 или игроку с соответствующей нодой. Общие старые ноды `mosvegas.admin`, `mosvegas.admin.properties`, `mosvegas.admin.factions` и `mosvegas.admin.quests` сохранены как совместимые полные доступы.
