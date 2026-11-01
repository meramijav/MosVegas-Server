# Свет и следы, версия 0.1.1

При изучении поведения использованы публичные исходники Immersive Vehicles:

- https://github.com/DonBruce64/MinecraftTransportSimulator/blob/master/mccore/src/main/java/minecrafttransportsimulator/rendering/RenderableModelObject.java
- https://github.com/DonBruce64/MinecraftTransportSimulator/blob/master/mccore/src/main/java/minecrafttransportsimulator/rendering/RenderableVertices.java
- https://github.com/DonBruce64/MinecraftTransportSimulator/blob/master/mccore/src/main/java/minecrafttransportsimulator/entities/instances/EntityParticle.java
- https://github.com/DonBruce64/MinecraftTransportSimulator/blob/master/mccore/src/main/java/minecrafttransportsimulator/entities/instances/PartGroundDevice.java
- Лицензия: https://github.com/DonBruce64/MinecraftTransportSimulator/blob/master/LICENCE.md

Код и текстуры оттуда в мод не включены. В этом проекте используется собственная реализация: фары освещают поверхности через глубину кадра с отдельной картой препятствий мира; следы строятся непрерывными сегментами между контактами шин. Текстуры протектора и тени генерирует `tools/refine_vehicle_assets.py`.

Ограничения: карта фар дискретная (21×13 направлений на фару), поэтому очень мелкие препятствия могут давать неточную границу тени; несколько пар фар обновляются по очереди. Свет не меняет игровые уровни освещения блоков. Следы разрываются на высоких ступенях и не рисуются в воздухе. Совместимость с внешними шейдерпаками требует отдельной проверки.
