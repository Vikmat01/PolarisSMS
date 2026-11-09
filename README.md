<p align="center">
  <img src="docs/banner.png" alt="Polaris SMS" width="100%">
</p>

<p align="center">
  <a href="https://github.com/Vikmat01/PolarisSMS/releases/latest"><img src="https://img.shields.io/github/v/release/Vikmat01/PolarisSMS?style=for-the-badge&color=2fd06b&label=версия" alt="Версия"></a>
  <a href="https://github.com/Vikmat01/PolarisSMS/releases"><img src="https://img.shields.io/github/downloads/Vikmat01/PolarisSMS/total?style=for-the-badge&color=2ef2b0&label=загрузки" alt="Загрузки"></a>
  <img src="https://img.shields.io/badge/Android-13%2B-3fc8ff?style=for-the-badge&logo=android&logoColor=white" alt="Android 13+">
  <img src="https://img.shields.io/badge/Kotlin-100%25-9b6bff?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
  <a href="LICENSE"><img src="https://img.shields.io/badge/лицензия-GPL--3.0-f5b400?style=for-the-badge" alt="GPL-3.0"></a>
</p>

<p align="center">
  <a href="https://github.com/Vikmat01/PolarisSMS/releases/latest"><b>⬇ Скачать последнюю версию</b></a>
</p>

---

SMS-приложение для Android в стиле жидкого стекла. Пара к звонилке [Polaris Phone](https://github.com/Vikmat01/PolarisPhone): общий вид, общий чёрный список.

## ✨ Возможности

| | |
|---|---|
| 🔮 **Жидкое стекло** | Шапка, кнопки, поле ввода и меню преломляют переписку под собой. Кнопки упруго «набухают» при нажатии |
| 🌗 **Три темы** | Чёрная, светлая и «Авто»: светлая днём, чёрная после заката. Восход и закат считаются по вашему месту |
| 🎨 **Цвет пузырей** | Свой цвет для каждой темы, выбирается на цветовом круге |
| 🚫 **Чёрный список** | Системный, общий со звонилкой: заблокированный номер не может ни писать, ни звонить |
| 👤 **Фото контактов** | В списке переписок, поиске и чёрном списке |
| 📳 **Отклик** | Мягкая вибрация на кнопках, переключателях, цветовом круге и новых сообщениях |
| ⚡ **Плавность** | Анимации переходов и сообщений, приложение просит у экрана максимальную частоту (90/120 Гц) |
| 🔄 **Автообновления** | Приложение само находит новый релиз здесь и предлагает установить |

## 📲 Установка

1. Скачайте `PolarisSMS-x.y.apk` из [последнего релиза](https://github.com/Vikmat01/PolarisSMS/releases/latest).
2. Разрешите установку из браузера или файлового менеджера, если Android спросит.
3. При первом запуске нажмите «Сделать основным». Если окно выбора не появилось (Honor, Xiaomi): Настройки → Приложения → Приложения по умолчанию → SMS → Polaris SMS.
4. Дальше обновления приходят сами (Настройки → «О приложении»).

Нужен Android 13 или новее. MMS (картинки) пока не поддерживаются.

## 🛠 Сборка

```bash
./gradlew assembleRelease
```

Без `keystore.properties` релиз подписывается отладочным ключом. Для своей подписи положите в корень проекта `keystore.properties`:

```properties
storeFile=polaris-apps.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## 🚀 Выпуск новой версии

1. Поднять `versionCode` и `versionName` в `app/build.gradle.kts`.
2. Описать изменения в `release-notes/vX.Y.md` и закоммитить в `main`.
3. GitHub Actions соберёт подписанный APK и опубликует релиз, приложения сами предложат обновиться.

Секреты репозитория для подписи: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## 📄 Лицензия

Polaris SMS распространяется по лицензии [GNU GPL v3](LICENSE). Код можно изучать, менять и распространять, но изменённые версии тоже должны оставаться открытыми под GPL-3.0.

Жидкое стекло — библиотека [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) от Kyant0.
