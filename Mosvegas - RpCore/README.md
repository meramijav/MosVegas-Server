# Mosvegas Core

Модульное RP-ядро для Fabric 1.20.4: документы, физические наличные, банк, автоматическая зарплата, фракции, квесты, недвижимость, магазины и серверная защита зон.

## Сборка

Требуется Java 17.

```powershell
.\gradlew.bat build
```

Готовый универсальный JAR: `build/libs/mosvegas-core-0.0.4.jar`.

На этом компьютере `./dev.ps1 -Mode build -Offline` использует Java 17 из Minecraft-лаунчера. `-Mode test` запускает регрессионные проверки и отдельный тестовый сервер, `-Mode client` — клиент в `run-zone-tests`.

Обновление с 0.0.4 и старше без потери данных: [DATA_MIGRATION_GUIDE.md](DATA_MIGRATION_GUIDE.md). Магазины, команды зон и пошаговые проверки: [SHOPS_ZONES_GUIDE.md](SHOPS_ZONES_GUIDE.md). API будущего транспорта: [transtortintegration.md](transtortintegration.md). Результаты проверки: [TEST_REPORT.md](TEST_REPORT.md).

## Основные команды

- `/mfc` — паспорт (имя, фамилия, возраст и пол).
- `/driverlicence` и `/weaponlicence` — отдельные меню лицензий.
- `/bankcard` — получение/перевыпуск банковской карты.
- `/salary` — статус следующего автоматического начисления.
- `/faction`, `/quests`, `/property` — основные RP-системы.
- `/quests start <id>` — принять конкретный квест по его ID; полная настройка описана в `QUESTS_CONFIG_GUIDE.md`.
- `/faction level set|give|take <игрок> <число>` — уровень фракции.
- `/faction hierarchy set|give|take <игрок> <число>` — уровень иерархии.
- `/serverlevel set|give|take <игрок> <число>` — серверный уровень.
- `/property wand` — получить палку выделения зон (ЛКМ/ПКМ).
- `/property` — только свои объекты; купить или арендовать — ПКМ по двери объекта.
- `/property admin` — список квартир/парковок по страницам, создание, изменение названия/ID/цен, части границы, удаление, владельцы и участники.
- `/mosvegas bankterminal add|remove` — создать или удалить терминал на блоке под прицелом.
- `/mosvegas money give|take|set <игрок> <сумма>` — изменить банковский счёт игрока (`mosvegas.admin.money`).
- `/rpplaceholder <id> [игрок]` — проверить значение плейсхолдера.
- `/mosvegas reload|save` — перечитать конфиг или немедленно сохранить данные.
- `/mosvegas shop <id>` — открыть магазин; без ID — список магазинов.
- `/mosvegas shopadmin create|edit|add|price|remove|enabled|rename|delete|list` — управление товарами и ценами.
- `/mosvegas zone pos1|pos2|create|bounds|flag|grant|revoke|priority|info|list|here|world|delete` — серверные приваты.

## Плейсхолдеры

`%mosvegas_first_name%`, `%mosvegas_last_name%`, `%mosvegas_age%`, `%mosvegas_gender%`, `%mosvegas_licences%`, `%mosvegas_driver_licence%`, `%mosvegas_weapon_licence%`, `%mosvegas_cash%`, `%mosvegas_bank%`, `%mosvegas_faction%`, `%mosvegas_faction_level%`, `%mosvegas_hierarchy_level%`, `%mosvegas_server_level%`.

Для интеграций доступен стабильный Java-метод `PlaceholderService.resolve(player, placeholder)`.

## Файлы

- `config/mosvegas-core.json` — расширяемые определения фракций, квестов, объектов, границ, банковских блоков и экономики.
- `<папка мира>/mosvegas-core-data.json` — игроки, лицензии, активные квесты, недвижимость и права; предыдущая версия — `.bak`.
- `<папка мира>/mosvegas-zones.json`, `mosvegas-shops.json` — зоны/ACL и каталоги товаров с NBT; изменения команд сохраняются сразу, предыдущая версия — `.bak`.
- SQLite-база `<папка мира>/mosvegas-core.db` версий 0.0.4 и старше переносится в JSON при первом запуске и переименовывается в `.migrated`.

Номиналы физических денег: 5 000, 2 000, 1 000, 500, 200, 100, 50, 10, 5, 2 и 1 ₽. Произвольная сумма автоматически раскладывается от крупнейшего номинала к меньшему.

Граница объекта — список прямоугольных частей `areas` (Г-, П-образные квартиры); палка выделения показывает новую зону жёлтым клиентским контуром, сохранённый объект — голубым. Дверь занятого чужого объекта получает красную кастомную рамку, доступная — зелёную. Ванильная рамка блока и частицы для этих подсветок не используются. Активный квест показывает в action bar вращающуюся стрелку, а цель — клиентские контурные кольца и маяк.
